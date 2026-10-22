(() => {
    'use strict';
    if (window !== window.top || location.origin !== 'https://player.bilibili.com') return;
    const bridge = window.CompanionAudio;
    const post = bridge.postMessage.bind(bridge), encode = JSON.stringify.bind(JSON);
    let video = null, context = null, node = null, source = null;
    let epoch = 0, ready = false, playing = false, closed = false;
    function send(value) { post(encode(value)); }
    function stop() {
        if (closed) return;
        closed = true; observer.disconnect(); clearInterval(watch);
        if (video) video.pause();
        if (node) { node.port.onmessage = null; node.disconnect(); node.port.close(); }
        if (source) source.disconnect();
        if (context) context.close().catch(() => {});
    }
    function fail() {
        if (closed) return;
        // A fixed invalid protocol kind stops the native task; no webpage error text is exported.
        try { send({kind: 'error', epoch: Math.max(1, epoch)}); } finally { stop(); }
    }
    function state(force = false) {
        if (closed || !ready) return;
        const next = !video.paused && !video.ended && !video.seeking && video.readyState >= 3;
        if (!force && playing === next) return;
        playing = next;
        if (++epoch > 1000000000) return fail();
        send({kind: 'state', epoch, playing, rate: context.sampleRate});
        node.port.postMessage({kind: 'state', epoch, playing});
    }
    bridge.onmessage = ({data}) => {
        if (closed || !node) return;
        try {
            const ack = JSON.parse(data);
            if (Object.keys(ack).sort().join(',') !== 'epoch,sequence' ||
                !Number.isInteger(ack.epoch) || !Number.isInteger(ack.sequence)) return fail();
            if (ack.epoch === epoch) node.port.postMessage({kind: 'ack', epoch, sequence: ack.sequence});
        } catch (_) { fail(); }
    };
    async function attach(found) {
        video = found;
        try {
            context = new AudioContext({latencyHint: 'interactive'});
            for (const event of ['play', 'playing', 'pause', 'ended', 'waiting', 'seeking', 'seeked', 'emptied', 'ratechange']) {
                video.addEventListener(event, () => {
                    if (closed) return;
                    // Resume inside the play event, before any await, retaining the user's gesture.
                    if (!video.paused && context.state === 'suspended') context.resume().catch(fail);
                    state(event === 'seeking' || event === 'ratechange' || event === 'emptied');
                });
            }
            const url = URL.createObjectURL(new Blob([/*WORKLET_SOURCE*/], {type: 'text/javascript'}));
            try { await context.audioWorklet.addModule(url); } finally { URL.revokeObjectURL(url); }
            if (closed) return;
            node = new AudioWorkletNode(context, 'companion-pcm', {
                numberOfInputs: 1, numberOfOutputs: 1, outputChannelCount: [2],
                channelCount: 2, channelCountMode: 'explicit', channelInterpretation: 'speakers'
            });
            node.onprocessorerror = fail;
            node.port.onmessage = ({data}) => {
                if (closed) return;
                if (data.kind === 'error') return fail();
                const bytes = new Uint8Array(data.buffer);
                try {
                    if (data.epoch !== epoch || !playing) return;
                    let binary = '';
                    for (const byte of bytes) binary += String.fromCharCode(byte);
                    send({kind: 'pcm', epoch, sequence: data.sequence, frames: 1024, pcm: btoa(binary)});
                } catch (_) { fail(); } finally { bytes.fill(0); }
            };
            source = context.createMediaElementSource(video);
            // MediaElementAudioSource preserves the element's volume/muted/playbackRate semantics.
            source.connect(node); node.connect(context.destination);
            ready = true; state(true);
            if (!video.paused) await context.resume();
        } catch (_) { fail(); }
    }
    function scan() {
        if (closed) return;
        const list = document.querySelectorAll('video');
        if (list.length > 1 || (video && (list.length !== 1 || list[0] !== video))) return fail();
        if (!video && list.length === 1) void attach(list[0]);
    }
    const observer = new MutationObserver(scan);
    observer.observe(document, {childList: true, subtree: true});
    const watch = setInterval(() => {
        if (closed) return;
        scan();
        if (ready) {
            if (context.state === 'closed') return fail();
            state();
        }
    }, 500);
    window.addEventListener('pagehide', stop, {once: true});
    scan();
})();
