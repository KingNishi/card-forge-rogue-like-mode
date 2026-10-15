# SPDX-License-Identifier: GPL-3.0-or-later
"""CPU-only nonverbal creature sound design; no speech synthesis or saturation."""
from pathlib import Path
from fractions import Fraction
import json,hashlib,sys
import numpy as np
import soundfile as sf
from scipy import signal
HERE=Path(__file__).resolve().parent;ROOT=Path(sys.argv[1]).resolve();SR=24000
source=HERE/'roar-sources';out=HERE/'roars';out.mkdir(exist_ok=True);(HERE/'data').mkdir(exist_ok=True)
sources=json.loads((source/'sources.json').read_text())
def load(p):
 x,sr=sf.read(p,dtype='float64',always_2d=True);x=x.mean(axis=1)
 x=signal.resample_poly(x,SR,sr)
 sos=signal.butter(3,[48,4800],btype='bandpass',fs=SR,output='sos')
 return signal.sosfiltfilt(sos,x)
raw=[load(source/x['file']) for x in sources]
def regions(x):
 block=480;y=np.pad(x,(0,(-len(x))%block));energy=np.sqrt(np.mean(y.reshape(-1,block)**2,axis=1))
 active=energy>max(.005,np.max(energy)*.075)
 # Fill gaps of at most 160 ms so a growl is kept intact.
 for i in range(1,len(active)-1):
  if active[i-1] and not active[i]:
   following=np.flatnonzero(active[i:i+9])
   if len(following):active[i:i+int(following[0])]=True
 changes=np.diff(np.r_[False,active,False].astype(int));starts=np.flatnonzero(changes==1);ends=np.flatnonzero(changes==-1)
 return [x[max(0,(a-2)*block):min(len(x),(b+2)*block)] for a,b in zip(starts,ends) if (b-a)*block/SR>.45]
snarls=regions(raw[1])+regions(raw[2]);assert len(snarls)>=4
def vary(x,rate):
 ratio=Fraction(1/rate).limit_denominator(200)
 return signal.resample_poly(x,ratio.numerator,ratio.denominator)
def finish(x):
 # Natural dynamic range; never hard-clip, overdrive, or add a large hall reverb.
 target=.105;rms=np.sqrt(np.mean(x*x));x=x*min(4,target/max(1e-9,rms))
 x=x*min(1,.50/max(1e-9,np.max(np.abs(x))))
 fade=min(int(.04*SR),len(x)//3);x[:fade]*=np.linspace(0,1,fade);x[-fade:]*=np.linspace(1,0,fade)
 return np.r_[np.zeros(int(.04*SR)),x,np.zeros(int(.08*SR))]
defs=json.loads((ROOT/'_mods/rogue-commanders/voices/lines.json').read_text(encoding='utf8'))
pant=defs['pantlaza-sun-favored'];pant['voice']['nonverbal']=True;pant['voice']['source']='CC0 creature sounds; see sources.json'
report=[];basefolder=ROOT/'_mods/rogue-commanders/voices/pantlaza-sun-favored'
for i,p in enumerate(sorted(basefolder.glob('*.wav'))):
 event=p.stem.rsplit('_',1)[0]
 desired=1.5 if event=='package' else 2.2 if event=='defeat' else 3.3 if event in ('boss','record','commander') else 2.8
 clip=vary(snarls[i%len(snarls)],.83+(i%5)*.045)
 # Blend a quiet, clean low monster layer under the snarl to give Pantlaza a consistent body.
 maxlen=int(desired*SR);clip=clip[:maxlen];n=len(clip)
 bass=vary(raw[0],.91+(i%3)*.03);offset=min(int(.35*SR+(i%4)*.12*SR),max(0,len(bass)-n));bass=bass[offset:offset+n]
 if len(bass)<n:bass=np.pad(bass,(0,n-len(bass)))
 bass=bass*(np.sqrt(np.mean(clip*clip))*.22/max(1e-9,np.sqrt(np.mean(bass*bass))))
 x=finish(clip+bass);dest=out/p.name;sf.write(dest,x,SR,subtype='PCM_16')
 actual,sr=sf.read(dest);peak=float(np.max(np.abs(actual)));truepeak=float(np.max(np.abs(signal.resample_poly(actual,4,1))))
 rms=float(np.sqrt(np.mean(actual*actual)));assert sr==SR and .25<len(actual)/sr<4.5 and .02<rms<.2 and peak<=.501 and truepeak<.64
 report.append(dict(file=p.name,duration=round(len(actual)/sr,3),peak_dbfs=round(20*np.log10(peak),2),true_peak_dbfs=round(20*np.log10(truepeak),2),rms_dbfs=round(20*np.log10(rms),2),sha256=hashlib.sha256(dest.read_bytes()).hexdigest()))
for event,texts in pant.items():
 if isinstance(texts,list):pant[event]=['[pained dinosaur growl]' if event=='defeat' else '[low dinosaur rumble]' if event=='package' else '[dinosaur roar]']*len(texts)
(HERE/'data/development-voice-lines.json').write_text(json.dumps(defs,ensure_ascii=False,indent=2),encoding='utf8')
runtime=json.loads((ROOT/'_mods/rogue-commanders/voices/lines.json').read_text(encoding='utf8'))
runtime['pantlaza-sun-favored']=pant
(HERE/'data/voice-lines.json').write_text(json.dumps(runtime,ensure_ascii=False,indent=2),encoding='utf8')
(out/'sources.json').write_text(json.dumps(dict(sources=sources,processing='CPU downmix/resample, clean bandpass, moderate pitch variation and quiet bass layer, 40ms fades, peak <= -6dBFS. No TTS, spoken lines, saturation or large reverb.',license='CC0-1.0'),indent=2),encoding='utf8')
(out/'CREDITS.txt').write_text('Pantlaza nonverbal creature sounds\nCC0 1.0: https://creativecommons.org/publicdomain/zero/1.0/\nDeep Monster Roar — trazzz123 — https://opengameart.org/content/cc0-deep-monster-roar\nMonster snarls / snarl attack — Darsycho — https://opengameart.org/content/monster-snarls\nEdited for this mod: filtered, pitched, layered and reduced in level; no spoken TTS.\n',encoding='utf8')
(HERE/'roar-validation.json').write_text(json.dumps(dict(clips=report,source_segments=len(snarls),speech_synthesis=False,gpu_used=False),indent=2),encoding='utf8')
assert len(report)==19
print('Rendered',len(report),'nonverbal clips under',out,'; no game files installed.')
