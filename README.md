# Card Forge Rogue Like Mode

A community roguelike Commander mode and interface mod for **Forge**.

**Built on Forge by the Card-Forge team. All credit for the underlying game engine, rules implementation and original Forge project belongs to its contributors.**

**Original project and source:** https://github.com/Card-Forge/forge  
**Official Forge releases:** https://github.com/Card-Forge/forge/releases

This is an unofficial community modification, not an official Card-Forge release.


## Install on Windows x64

1. Download the Windows ZIP from this repository's **Releases** page.
2. Extract the entire ZIP. Do not run the installer from inside the ZIP viewer.
3. Double-click **Install.cmd** and choose a new installation folder.
4. Start **Rogue Commanders.cmd**, or use the desktop shortcut.

Java 17 is bundled (Eclipse Temurin); no separate Java or Python installation is needed. The installer verifies file checksums, relocates the mod to your chosen folder, and does not import or replace Forge saves. Existing Forge installations do not need to be removed. Windows x64 is the packaged platform; this is not an Android installer.

The included engine is pinned to **Forge 2.0.16-SNAPSHOT-10.09**. Do not replace it with a newer snapshot: mobile interface changes can break the HUD. Normal card pictures are downloaded by Forge as needed, so an internet connection may be needed for pictures on a fresh machine.

For an unattended installation:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\installer\Install.ps1 -Target "D:\Games\CardForge-Rogue" -NonInteractive -NoShortcuts
```

## Included changes

- Rogue Commander runs with thirteen commanders, escalating opponents, themed six-card reward packages and progression.
- Four dedicated commander achievements: Teysa Karlov after 20 sacrificed permanents; Breya after 20 artifact-creature spells cast AND defeating boss 10; Eriette after defeating a boss with lethal life drain/noncombat burn; the existing Shang-Chi after boss 1. The two counters persist across runs, including lost runs. Historical boss victories are preserved.
- Three new hand-picked archetypes: Teysa Aristocrats, Breya artifact engines (no Voltron plan), and Eriette Aura control/drain. Each has a 59-card starter, seven package choices and sixteen signatures; Breya includes four triomes and balanced fixing.
- A paged achievements screen shows commander requirements/progress and reachable original Forge trophies with their native evaluators/thresholds. Unavailable Arena/Quest/event goals and trophies for unavailable cards are excluded. Existing cards, package choices, artwork, gold and active runs survive migration.
- Sixteen signature unlocks per commander, retaining the original eight.
- Stronger four/five-colour starting mana bases with three/four triomes, balanced basics and Chromatic Lantern; five targeted Y'shtola starter upgrades for duels.
- Package selection before every run: keep at least the original five packages (six for Azula), with one free alternative per commander. Select an alternative before deselecting another package at the minimum.
- A second additional package per commander unlocks permanently for 30 progression points after achieving three wins in one run with that commander. Earn five points per victory plus one-time achievement bonuses. This supplements the existing Glory, card and artwork progression.
- A **run-only card shop** between fights: six elite artifacts, six lands and six removal/control cards, restricted to the commander's colours and relevant support. Gold is banked across runs: the bosses in rounds 5 / 10 / 15 award **6 / 10 / 15 gold**. There is no starting gold or normal-fight gold. Bought cards last only their current run; full decks receive purchases on the bench. **All lands cost 6 gold**. Reroll the entire 18-card stock for **2 gold**; paid rolls survive reopening and reloading. The high-power shop deliberately includes Mana Crypt and other cards outside normal Commander ban-list constraints for this roguelike mode.
- Package choices use filled checkboxes, a coloured selected surface, an explicit SELECTED label and a live selection count. Every commander immediately has one free choice above the original minimum (seven available for Azula's minimum of six), with further unlocks visibly indicated.
- Every one of the 2,343 original reward entries reviewed individually; the resulting 2,167 entries and commander-plan support restrictions are retained alongside the new alternative packages.
- Twelve animated battlefields with directional water, flowing waterfalls and local animal animations. Painted animals animate in place; they are not roaming agents.
- A **Top card** slot next to your commander while an effect actually permits looking at the top of your library. It supports inspection and the game's normal legal card actions.
- AI removal timing considers valuable self-attack triggers during beginning of combat, before attackers are declared. Native legal targets, costs, protection and spell timing still apply.
- The level-10 boss always begins with an extra **Mind's Eye**. Its normal one-mana draw payment still applies.
- Commander voices, including the deeper Ureni voice and the selected angry Amy casting for Azula.
- Pantlaza uses 19 nonverbal creature roars and growls with level headroom, rather than spoken TTS lines. Original sound-source credits and licence metadata are included.
- Updated battle HUD, portraits, card motion and other presentation changes.

## Public package contents

The release includes the game, selected runtime assets, installer and Temurin runtime. The repository contains modification sources, patchers, runtime asset inputs, credits and build instructions. It does **not** include the author's saves, accounts, chat histories, model downloads, development environments, private card-picture cache, Magic Arena installation or Witcher soundtrack. The public HUD uses Cinzel and Atkinson Hyperlegible under the SIL Open Font License instead of Windows system fonts.

## Credits and licence

See [CREDITS.md](CREDITS.md), [SOURCE.md](SOURCE.md), [NOTICE.md](NOTICE.md), and [LICENSE](LICENSE). Forge and the code modifications use the **GNU General Public License, version 3 or (at your option) any later version**, matching Forge's source notices. You may use, study, modify and redistribute the code under that licence. No donation is required. The software is provided without warranty; see the full licence.

Included third-party assets, libraries and the Java runtime retain their own licences and notices. Magic: The Gathering, card illustrations, character names and associated marks belong to their respective owners, including Wizards of the Coast. This project is not affiliated with or endorsed by those owners. The software licence does not grant additional rights to those illustrations, characters or marks.

## Legal

Card Forge Rogue Like Mode is unofficial Fan Content permitted under the Fan Content Policy. Not approved/endorsed by Wizards. Portions of the materials used are property of Wizards of the Coast. ©Wizards of the Coast LLC. (Fan Content Policy: https://company.wizards.com/en/legal/fancontentpolicy)

- No Magic card illustrations are distributed with this package. Like Forge itself, the game downloads card pictures and enemy artwork from Scryfall on the player's computer when they are first needed.
- No MTG Arena client files or assets derived from them are included.
- Character names from Universes Beyond and other licensed card sets (for example Marvel, Avatar: The Last Airbender, Final Fantasy) are trademarks of their respective owners and are used only to refer to the corresponding Magic cards. This project is not affiliated with or endorsed by those owners. Commander voice lines are original fan-written text spoken by synthetic voices; they are not recordings of any actor.
- Code is GPL-3.0-or-later like Forge; artwork, sounds, fonts and sprites keep the licences listed in CREDITS.md. Enemy sprites from Ækashics' Librarium are included only inside the game package under their terms of use (credit required; no redistribution as an asset pack).
- Donations are voluntary, give no features or advantages, and do not change any licence terms.