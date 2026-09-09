/* One continuous output clock, independent of Vue rendering and network chunks. */
class RoadPcmPlayer extends AudioWorkletProcessor {
  constructor() {
    super(); this.queue=[]; this.offset=0; this.queued=0; this.started=false;
    this.ended=false; this.paused=false; this.frames=0; this.starved=0; this.consumed=0;
    this.port.onmessage=({data})=>{
      if(data.type==='chunk') { this.queue.push(data.samples); this.queued+=data.samples.length; }
      if(data.type==='end') this.ended=true;
      if(data.type==='pause') this.paused=true;
      if(data.type==='resume') this.paused=false;
    };
  }
  process(_inputs, outputs) {
    const out=outputs[0][0]; if(!out) return true;
    if(this.paused) return true;
    if(!this.started && (this.queued>=sampleRate*.7 || this.ended)) {
      this.started=true; this.port.postMessage({type:'playing'});
    }
    if(!this.started) return true;
    let square=0;
    for(let i=0;i<out.length;i++) {
      const chunk=this.queue[0];
      if(chunk) {
        const value=chunk[this.offset++];out[i]=value;square+=value*value;this.queued--;this.consumed++;
        if(this.offset===chunk.length){this.queue.shift();this.offset=0;}
      } else if(!this.ended) this.starved++;
    }
    this.frames+=out.length;
    if(this.frames>=sampleRate/30) {
      this.port.postMessage({type:'level',level:Math.min(1,Math.sqrt(square/out.length)*5),starvedMs:this.starved/sampleRate*1000});
      this.port.postMessage({type:'consumed',samples:this.consumed});this.consumed=0;
      this.frames=0;
    }
    if(this.ended && !this.queued) {this.port.postMessage({type:'ended',starvedMs:this.starved/sampleRate*1000});return false;}
    return true;
  }
}
registerProcessor('road-pcm-player',RoadPcmPlayer);
