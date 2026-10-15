Downloaded terrain materials
============================
Grass: rubberduck, hp_03, 2048px.
Sand / paths: rubberduck, hp_04 cracked earth, 2048px.
Rock and earth: rubberduck handpainted textures, 1024px.
Water: GGBotNet Y2K water texture, 1024px.
Snow: Poly Haven snow_02 diffuse, 2048px.
All sources are CC0; exact source URLs, licenses and checksums are recorded in
external/sources.json and external/extra-sources.json. Original downloads are retained.

Runtime samples the real RGB surfaces and mixes them with preserved terrain
shading and alpha masks in terrain sheets. Exposure has been raised after the
first pass was too dark. Combined world chunks receive only a mild color grade:
they contain structures as well as terrain, so material replacement there is disabled.
Small terrain/chunks upload at 4x; large map atlases at 2x.
World buildings and character/card/portrait art are not converted to these surfaces.
Main town atlas replacement is limited to ground bands; its other artwork is graded.
CPU map-generation pixels and collision coordinates remain original.
Downloaded images are source materials, not a promise of 2K resolution per game tile.

Keep the full _mods directory. reapply_mods.py step 15 restores renderer changes
after a game update. The earlier generated materials.png is no longer used.
Installer backup: _mods/pending-external-terrain/before.
