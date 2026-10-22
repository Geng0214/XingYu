// Only PCM crosses the native boundary. Browser output stays zero even before tab-level muting.
class CompanionPcm extends AudioWorkletProcessor {
    constructor() {
        super();
        this.epoch = 0; this.playing = false; this.sequence = 0; this.acked = 0;
        this.offset = 0; this.pcm = new Uint8Array(4096); this.failed = false;
        this.port.onmessage = ({data}) => {
            if (this.failed) return;
            if (data.kind === 'state' && Number.isInteger(data.epoch) && data.epoch > this.epoch) {
                this.pcm.fill(0); this.offset = 0; this.sequence = 0; this.acked = 0;
                this.epoch = data.epoch; this.playing = data.playing;
            } else if (data.kind === 'ack' && data.epoch === this.epoch) {
                if (data.sequence !== this.acked + 1 || data.sequence > this.sequence) return this.fail();
                this.acked = data.sequence;
            }
        };
    }
    fail() {
        this.failed = true; this.playing = false; this.pcm.fill(0); this.offset = 0;
        this.port.postMessage({kind: 'error'});
    }
    process(inputs, outputs) {
        for (const output of outputs) for (const channel of output) channel.fill(0);
        if (this.failed) return false;
        if (!this.playing || inputs[0].length === 0) return true;
        // AudioWorkletNode explicitly downmixes the media source to stereo.
        const left = inputs[0][0], right = inputs[0][1] || left;
        for (let frame = 0; frame < left.length; frame++) {
            for (let channel = 0; channel < 2; channel++) {
                const value = channel === 0 ? left[frame] : right[frame];
                if (!Number.isFinite(value)) { this.fail(); return false; }
                const sample = Math.round(Math.max(-1, Math.min(1, value)) * (value < 0 ? 32768 : 32767));
                this.pcm[this.offset++] = sample & 255; this.pcm[this.offset++] = (sample >> 8) & 255;
            }
            if (this.offset === 4096) {
                if (this.sequence - this.acked >= 8) { this.fail(); return false; }
                const buffer = this.pcm.buffer;
                this.port.postMessage({kind: 'pcm', epoch: this.epoch, sequence: ++this.sequence, buffer}, [buffer]);
                this.pcm = new Uint8Array(4096); this.offset = 0;
            }
        }
        return true;
    }
}
registerProcessor('companion-pcm', CompanionPcm);
