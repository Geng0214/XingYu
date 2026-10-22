// Runs the shipped scripts in a host VM. Does not emulate or claim Android audio routing.
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assets = path.resolve(__dirname, '../../app/src/main/assets');
const worklet = fs.readFileSync(path.join(assets, 'bilibili-audio-worklet.js'), 'utf8');
const page = fs.readFileSync(path.join(assets, 'bilibili-audio.js'), 'utf8').replace('/*WORKLET_SOURCE*/', JSON.stringify(worklet));
function processor() {
    let Constructor;
    const messages = [];
    vm.runInNewContext(worklet, {
        AudioWorkletProcessor: class { constructor() { this.port = {postMessage: value => messages.push(value)}; } },
        registerProcessor: (name, value) => { assert.equal(name, 'companion-pcm'); Constructor = value; }
    });
    const instance = new Constructor();
    const send = data => instance.port.onmessage({data});
    function frames(left = 0.5, right = -0.5, count = 1024, mono = false) {
        const output = [new Float32Array(count).fill(1), new Float32Array(count).fill(1)];
        const input = [new Float32Array(count).fill(left)];
        if (!mono) input.push(new Float32Array(count).fill(right));
        const alive = instance.process([input], [output]);
        assert.ok(output.every(channel => channel.every(value => value === 0)), 'browser output must remain silent');
        return alive;
    }
    return {instance, messages, send, frames};
}
test('PCM preserves stereo, little endian and clipping while browser output is zero', () => {
    const p = processor(); p.send({kind: 'state', epoch: 1, playing: true});
    p.frames(2, -2);
    const bytes = Buffer.from(p.messages[0].buffer);
    assert.equal(bytes.length, 4096); assert.equal(bytes.readInt16LE(0), 32767); assert.equal(bytes.readInt16LE(2), -32768);
    assert.equal(p.messages[0].sequence, 1);
    const mono = processor(); mono.send({kind: 'state', epoch: 1, playing: true}); mono.frames(0.5, 0, 1024, true);
    const m = Buffer.from(mono.messages[0].buffer); assert.equal(m.readInt16LE(0), 16384); assert.equal(m.readInt16LE(2), 16384);
});
test('128-frame rendering waits for a whole 1024-frame packet', () => {
    const p = processor(); p.send({kind: 'state', epoch: 1, playing: true});
    for (let i = 0; i < 7; i++) p.frames(0.5, -0.5, 128);
    assert.equal(p.messages.length, 0); p.frames(0.5, -0.5, 128); assert.equal(p.messages.length, 1);
});
test('unacknowledged audio is bounded and overflow stops instead of dropping and continuing', () => {
    const p = processor(); p.send({kind: 'state', epoch: 1, playing: true});
    for (let i = 0; i < 8; i++) assert.equal(p.frames(), true);
    assert.equal(p.frames(), false); assert.equal(p.messages.length, 9); assert.equal(p.messages[8].kind, 'error');
    p.send({kind: 'ack', epoch: 1, sequence: 1}); assert.equal(p.frames(), false);
    assert.ok(p.instance.pcm.every(value => value === 0));
});
test('ordered acknowledgements permit sustained playback and duplicate acknowledgements stop it', () => {
    const p = processor(); p.send({kind: 'state', epoch: 1, playing: true});
    for (let i = 1; i <= 100; i++) { assert.equal(p.frames(), true); p.send({kind: 'ack', epoch: 1, sequence: i}); }
    assert.equal(p.messages.length, 100); p.send({kind: 'ack', epoch: 1, sequence: 100}); assert.equal(p.frames(), false);
});
test('20 pause/seek cycles erase partial PCM and old acknowledgements never refill new credits', () => {
    const p = processor();
    for (let i = 0; i < 20; i++) {
        const epoch = i * 3 + 1;
        p.send({kind: 'state', epoch, playing: true}); p.frames(); p.frames(0.5, 0.5, 128);
        const partial = p.instance.pcm;
        p.send({kind: 'state', epoch: epoch + 1, playing: false});
        assert.ok(partial.every(value => value === 0)); const count = p.messages.length;
        p.frames(); assert.equal(p.messages.length, count);
        p.send({kind: 'state', epoch: epoch + 2, playing: true});
        p.send({kind: 'ack', epoch, sequence: 1}); assert.equal(p.instance.acked, 0);
        p.frames(); assert.equal(p.messages.at(-1).sequence, 1);
    }
});
test('invalid audio stops and wipes the partial packet', () => {
    const p = processor(); p.send({kind: 'state', epoch: 1, playing: true});
    p.frames(0.5, -0.5, 128); assert.equal(p.frames(NaN), false); assert.ok(p.instance.pcm.every(v => v === 0));
});
async function documentHarness({origin = 'https://player.bilibili.com', main = true} = {}) {
    const native = [], controls = [], events = new Map(); let scan, watch, node, context, source;
    const video = {paused: true, ended: false, seeking: false, readyState: 4,
        addEventListener: (name, callback) => events.set(name, callback), pause() { this.paused = true; events.get('pause')?.(); }};
    let videos = [video];
    const bridge = {postMessage: data => native.push(JSON.parse(data))};
    const window = {CompanionAudio: bridge, addEventListener: () => {}}; window.top = main ? window : {};
    vm.runInNewContext(page, {
        window, location: {origin}, document: {querySelectorAll: () => videos},
        MutationObserver: class { constructor(callback) { scan = callback; } observe() {} disconnect() {} },
        setInterval: callback => { watch = callback; return 1; }, clearInterval: () => {},
        Blob, URL, btoa,
        AudioContext: class {
            constructor() { context = this; this.sampleRate = 48000; this.state = 'suspended'; this.audioWorklet = {addModule: async () => {}}; }
            async resume() { this.state = 'running'; }
            async close() { this.state = 'closed'; }
            createMediaElementSource(v) { assert.equal(v, video); source = {connect() {}, disconnect() {}}; return source; }
        },
        AudioWorkletNode: class { constructor() { node = this; this.port = {postMessage: v => controls.push(v), close() {}}; } connect() {} disconnect() {} }
    });
    await new Promise(resolve => setImmediate(resolve));
    return {native, controls, video, events, bridge, context, watch,
        pcm(epoch, sequence) { const bytes = new Uint8Array(4096).fill(1); node.port.onmessage({data: {kind: 'pcm', epoch, sequence, buffer: bytes.buffer}}); return bytes; },
        replace() { videos = [{...video}]; scan(); }};
}
test('document bridge sends pause/play/seek epochs and erases transferred packets', async () => {
    const h = await documentHarness(); assert.equal(h.native[0].playing, false);
    h.video.paused = false; h.events.get('play')(); assert.equal(h.native[1].playing, true);
    const bytes = h.pcm(2, 1); assert.ok(bytes.every(value => value === 0)); assert.equal(h.native.at(-1).pcm.length, 5464);
    h.video.seeking = true; h.events.get('seeking')(); assert.equal(h.native.at(-1).playing, false);
    h.video.seeking = false; h.events.get('seeked')(); assert.equal(h.native.at(-1).epoch, 4);
    const count = h.controls.length; h.bridge.onmessage({data: '{"epoch":2,"sequence":1}'}); assert.equal(h.controls.length, count);
    const old = h.pcm(2, 2); assert.ok(old.every(value => value === 0)); assert.equal(h.native.at(-1).kind, 'state');
    h.bridge.onmessage({data: '{"epoch":4,"sequence":1}'}); assert.equal(h.controls.at(-1).kind, 'ack');
});
test('page bridge rejects another origin or subframe without starting audio', async () => {
    for (const scope of [{origin: 'https://example.com'}, {main: false}]) {
        const h = await documentHarness(scope); assert.equal(h.native.length, 0); assert.equal(h.context, undefined);
    }
});
test('video replacement and malformed acknowledgements close the context and pause media', async () => {
    for (const corrupt of [false, true]) {
        const h = await documentHarness(); h.video.paused = false; h.events.get('play')();
        if (corrupt) h.bridge.onmessage({data: '{"epoch":2,"sequence":1,"url":"https://example.com"}'});
        else h.replace();
        assert.equal(h.native.at(-1).kind, 'error'); assert.equal(h.context.state, 'closed'); assert.equal(h.video.paused, true);
    }
});
