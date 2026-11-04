# Boss voice assets — 16 October 2026

Prepared for Claude's later integration. **Assets only: no Scene/HUD hooks or installation.**
There are nine bosses, five events, and two WAV variants per event: 90 clips.
`victory` means the boss won / player lost; `defeat` means the boss lost / player won.
`death` is a temporary commander removal reaction, so it does not assume the duel ended.

Files use `<boss id>/<event>_<1-based variant>.wav`, 24 kHz mono PCM16.
`lines.json` contains English subtitles indexed in that same order. The three fully
nonverbal creatures have empty lists for every event. **An empty subtitle list does
not mean no audio:** use the two variants recorded in `manifest.json` for them.

## Original writing and voice direction

All 60 spoken lines are newly written for this mod. None is copied from commercial
game dialogue. These are original interpretations of MTG characters, using synthetic
identities; they do not reproduce or aim to imitate any particular voice actor.
No film/game/actor audio or sound-effect recordings were downloaded or used.

| Boss | Synthetic Piper reference | Performance and finish |
|---|---|---|
| Nahiri | en_US-kristin-medium | Direct, steely, impatient; blade and stone imagery; almost dry finish |
| Niv-Mizzet | en_US-norman-medium | Measured intellectual superiority; low, resonant dragon register, pitch/speed 0.76 |
| Sorin | en_US-john-medium | Old, restrained aristocratic menace; quiet contempt, pitch/speed 0.90 |
| Teferi | en_US-joe-medium | Patient confidence and wry humor, natural register |
| Lathliss | en_GB-cori-high | Imperious brood queen; low, resonant female dragon, pitch/speed 0.79 |
| Chandra | en_US-amy-medium | Energetic, impulsive, cheeky; bright delivery and little reverberation |
| Ghalta | None — original DSP | Large nonverbal dinosaur throat/chest roar; restrained grit and short open-air reflection |
| Sliver Queen | None — original DSP | Asynchronous chitin clicks and layered breath/formant noise; no words |
| Emrakul | None — original DSP | Inharmonic bass, moving breath and granular spectral tears; no words |

Character direction follows Wizards' profiles of
[Nahiri](https://magic.wizards.com/en/story/nahiri-planeswalker),
[Sorin](https://magic.wizards.com/en/story/sorin-markov-planeswalker),
[Teferi](https://magic.wizards.com/en/story/teferi-planeswalker), and
[Chandra](https://magic.wizards.com/en/story/chandra-nalaar-planeswalker).
Niv-Mizzet's intellect/fire combination follows Wizards' discussion of
[Niv-Mizzet, Parun](https://magic.wizards.com/en/news/making-magic/even-more-stories-city-2018-10-08).
Lathliss's dragon-brood direction interprets her title and Dragon creation/boosting
card abilities in the [Foundations release notes](https://media.wizards.com/2024/downloads/FDN_Release_Notes_dPeeQG7XyE/EN_FDN%20Release%20Notes%2008282024.pdf).
This is a creative interpretation, not a claim of an established canonical voice.

Ghalta's huge primal dinosaur direction follows the
[Rivals of Ixalan design notes](https://magic.wizards.com/en/news/making-magic/rivals-no-other-part-2-2018-01-15).
The swarm direction interprets Wizards' description of the
[Sliver hive-mind feel](https://magic.wizards.com/en/news/making-magic/twenty-things-were-going-kill-magic-2013-08-05).
Emrakul's incomprehensible presence follows
[Wizards' Eldrazi discussion](https://magic.wizards.com/en/news/making-magic/odds-ends-innistrad-crimson-vow-part-1-2021-12-06).
These three sound designs are intentionally wordless, with no subtitle placeholders.

## Synthesis and provenance

Speech uses the existing local Chatterbox 0.1.6 English model, Torch 2.7.1+cu128,
and the RTX 5060 Laptop GPU, one generation job at a time. Neutral 8–12 second
Piper-generated prose supplies each identity. References and checkpoints remain
in the private production directory, outside this delivery.

The isolated production snapshot is
`_mods/voices/boss-voices-20261016/renderer.py`, derived from the established
`make_voices_chatterbox.py`. Only definitions/output paths and boss casting/settings
were changed. The shared GPU lock, 60% allocator budget, deterministic bounded
retries, CPU Whisper small.en checks, and default Perth watermark remain enabled.
Finishing trims silence, filters rumble, lifts presence gently, compresses, applies
restrained echo and the configured pitch/speed shift, then targets -18 LUFS with
a -1 dBFS ceiling. Watermark detection after finishing was not tested.

[Chatterbox code](https://github.com/resemble-ai/chatterbox) is MIT licensed by
Resemble AI. Reference-source terms remain separate from that code license.
Local Piper cards `models/en_US_{kristin,norman,john}_medium.txt` and
`models/en_GB_cori_high.txt` identify public-domain LibriVox datasets prepared by
[Bryce Beattie](https://brycebeattie.com/files/tts/).
Joe's local card identifies the [OHF-Voice dataset](https://github.com/OHF-Voice/voice-datasets), CC0.
Amy's [model card](https://huggingface.co/rhasspy/piper-voices/raw/main/en/en_US/amy/medium/MODEL_CARD)
refers its dataset terms to [MycroftAI Mimic3 voices](https://github.com/MycroftAI/mimic3-voices)
(repository license CC-BY-SA-4.0); it is not labelled public domain here.
No model weights or references are redistributed in this asset folder.

All 30 nonverbal effects are original deterministic, sample-free DSP in
`_mods/voices/boss-voices-20261016/sound_design.py`; Ghalta also uses the existing
original `roar_synth.py` engine. The new DSP effects are dedicated under CC0-1.0.
Their target is -21 LUFS with a -1.5 dBFS ceiling; peak headroom takes precedence
over exact loudness. No TTS approximations of animal or alien speech are used.

## Verification and reproduction

Production definitions, seeds, renderer fingerprints, transcripts, per-file metrics,
and audit logs are retained under `_mods/voices/boss-voices-20261016/`.
`manifest.json` lists the delivered files, SHA-256 hashes, event mapping, variant
counts, durations and loudness. Speech is checked by CPU ASR before processing and
again on final PCM with the deliberate pitch/speed change reversed for recognition.
All delivered files are checked for exact format, finite/nonempty data, clipping,
silence, duration, identity coverage and duplicate hashes. This is automated quality
control, not a claim of human listening to every clip.

Run commands from `D:\forge\_mods\voices` with `.venv-chatterbox\Scripts\python.exe`:

1. `-B boss-voices-20261016/prepare.py` — prepare isolated text/config/snapshot.
2. `-B boss-voices-20261016/sound_design.py` — generate 30 nonverbal effects on CPU.
3. `-B boss-voices-20261016/renderer.py --report-name boss-render.json` — render 60 speech clips.
4. `-B boss-voices-20261016/validate_deliver.py --publish` — audit and copy assets only.

No player `voices/lines.json`, Scene, HUD, opponent deck or reapply/JAR file is edited
by this production workflow. Existing publication holds remain in effect.
