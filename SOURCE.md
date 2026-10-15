# Source and rebuild information

**Original Forge project:** https://github.com/Card-Forge/forge  
**Included engine:** 2.0.16-SNAPSHOT-10.09, build `2026-10-09 18:26:32`.

The game's `LICENSE.txt`, contributor list, changelog and resource licences are retained. Forge's source headers specify GPL version 3 or any later version; this repository's code modifications use the same GPL-3.0-or-later terms. `NOTICE.md` identifies the modified version and its changes. Donations are optional and do not limit access to code or licence rights.

`source/mods/` contains the Java helpers and bytecode patchers used for the modifications. `source/reapply_mods.py` records the local patch ordering. Some optional art-generation and private soundtrack steps in that development script are not part of the public package: do not run it as an installer.

For editing and rebuilding Java helpers, install a JDK 17+ and Python 3.11+, then run:

```powershell
python tools/build_mod.py --base-jar "D:\Games\CardForge-Rogue\forge-gui-mobile-dev-2.0.16-SNAPSHOT-jar-with-dependencies.jar" --jdk "C:\path\to\jdk-17" --out "build\forge-modded.jar"
```

The base is the pinned JAR from this release, which already contains native UI hooks; the tool compiles the editable helpers, applies the removal-timing patch and native life-loss observer idempotently, and produces a separate output JAR. It refuses to overwrite its input. General native-engine changes should be made against the Forge upstream source, not an arbitrary newer binary snapshot.

`installer/Relocate.java` relocates installation paths in class constant pools. `installer/Install.ps1` performs the clean installation. `assets/` contains the selected mod runtime inputs, including original source/credit manifests where available. `installer/temurin.json` identifies the runtime download and checksum; OpenJDK source is maintained at https://github.com/adoptium/jdk17u.

`source/audio/prepare_pantlaza_roars.py` and its `roar-sources/` inputs reproduce the nonverbal Pantlaza clips on the CPU using Python, NumPy, SciPy and soundfile. Run the script with the path to an installed game as its argument. It writes generated clips and metadata beside the script; it does not install them into a running game. Input sounds retain CC0-1.0; source URLs and hashes accompany them. Pantlaza's metadata marks it as nonverbal so the local speech-rendering pipeline will not replace its roars with TTS.


**Corresponding upstream source:** the bundled engine is the Forge snapshot built 2026-10-09 18:26 CEST from the Card-Forge master branch; the closest upstream commit is https://github.com/Card-Forge/forge/commit/c26a2e938f74e327b80f6ba5f5f1d631179f971c . All modifications are applied by the patchers in source/mods.
