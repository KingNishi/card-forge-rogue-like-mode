# Credits

## Forge — original game

**Card-Forge and all Forge contributors:** https://github.com/Card-Forge/forge

Forge supplies the engine, Magic rules implementation, AI foundation, card database, Adventure mode and original interface/assets. This community package builds on that work; it does not claim authorship of Forge. The unmodified GPLv3 text is included. Code modifications follow Forge's GPL-3.0-or-later source notices, and existing contributor lists and component licences are preserved. See `NOTICE.md`.

## Runtime

**Eclipse Temurin / Adoptium:** https://adoptium.net/temurin/  
Windows x64 OpenJDK 17 JRE. The exact download URL, version and published SHA-256 are recorded in `installer/temurin.json`. The runtime's `legal` directory and notices are retained. OpenJDK source project: https://github.com/adoptium/jdk17u

## Public HUD fonts

- **Cinzel**, Natanael Gama and contributors: https://github.com/google/fonts/tree/main/ofl/cinzel
- **Atkinson Hyperlegible**, Braille Institute and contributors: https://github.com/google/fonts/tree/main/ofl/atkinsonhyperlegible

Both use the SIL Open Font License 1.1; their exact licence texts ship under `_mods/modern-hud/licenses`.

## Icons, terrain and sprites

- Zone icons: lorc, faithtoken, badges and delapouite, https://game-icons.net/, CC BY 3.0. Individual icon credits are retained in `_mods/zone-icons/CREDITS.txt`. PNGs were rendered/recoloured for this HUD.
- Terrain materials: rubberduck, GGBotNet and Poly Haven. CC0 source URLs and checksums are preserved in `_mods/calm-terrain/external/sources.json` and `extra-sources.json`.
- Additional enemy sprite inputs: Ækashics / Librarium, https://www.akashics.moe/. Original asset rights remain with their creators.
- Other original game illustrations, card art and character designs retain their original credits and rights. Battlefield redesigns and custom presentation include AI-assisted work; no claim is made to the underlying card characters or brands.

## Synthetic commander voices

Spoken character audio was rendered with Chatterbox TTS, https://github.com/resemble-ai/chatterbox (MIT-licensed software), using synthetic Piper references. These are **not recordings of the commercial characters' voice actors**. Pantlaza's nonverbal creature sounds use the separate sources below.

Reference sources include Piper/OHF Voice, https://github.com/OHF-Voice/piper1-gpl, LibriVox, https://librivox.org/, LibriTTS-R/OpenSLR, https://www.openslr.org/, Bryce Beattie's recordings, the OHF Voice datasets, https://github.com/OHF-Voice/voice-datasets, and LJ Speech, https://keithito.com/LJ-Speech-Dataset/. Reference dataset/model rights are distinct from Chatterbox's software licence. No model weights, neural-network environments or copied actor recordings are included.

The public voice set keeps the selected Amy-derived Azula casting, John-derived Ureni processing, and the other existing commander casts. Original lines were written for this mod.

## Pantlaza — nonverbal creature sounds

- **CC0 Deep Monster Roar**, trazzz123: https://opengameart.org/content/cc0-deep-monster-roar
- **Monster snarls / snarl attack**, Darsycho: https://opengameart.org/content/monster-snarls
- CC0 1.0: https://creativecommons.org/publicdomain/zero/1.0/

Edited for the mod by downmixing, filtering, moderate pitch variation, quiet layering and level reduction with short fades. No film audio, spoken TTS, hard clipping or added large-hall reverb is used for Pantlaza. Per-source hashes and processing metadata are retained in `voices/pantlaza-sun-favored/sources.json`; `CREDITS.txt` accompanies the clips.
