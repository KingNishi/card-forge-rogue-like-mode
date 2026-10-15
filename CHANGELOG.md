# 0.1.0 — initial public package

- Pinned Forge 2.0.16-SNAPSHOT-10.09 and bundled verified Temurin 17 Windows x64 runtime.
- Added clean installer, path relocation, file manifest, checksums and source/asset attribution.
- Included curated Rogue Commander rewards. Battlefield animations are kept for the bundled default board; the MTG Arena-derived battle boards are not distributed.
- Added permission-aware top-library card slot next to the commander.
- Added pre-attack removal timing for valuable attack engines.
- Guaranteed an extra Mind's Eye for the level-10 boss.
- Included the revised Ureni voice and selected Azula voice.
- Excluded personal profiles, development caches, Arena client extraction trees and the Witcher soundtrack; replaced Windows system fonts with OFL alternatives.

Validation: Java 17 compilation; seven native CardView visibility/top-change cases; native attack-trigger/suppression fixture; exact boon-selection method over 1,000 seeds; bytecode verification and patch idempotence; unrelated JAR entries preserved. Previous battlefield GL checks passed for all twelve boards. A full human gameplay test of these last three changes is still recommended.

- Raised signature unlock pools from eight to sixteen for every commander, preserving the original cards.
- Improved Karolina/Jace starting mana bases with four/three triomes, flexible lands, balanced basics and a protected Chromatic Lantern.
- Ysh duel upgrades: Syphon Mind → Painful Truths; Cleansing Nova → Toxic Deluge; Decanter of Endless Water → Fellwar Stone; White Auracite → Get Lost; Ophidian Eye → Undermine. Keep these upgrades through starter randomization.
- Added ten free alternative packages and ten permanent achievement/point package unlocks, plus pre-run selection with original minimum counts. Existing runs keep their original package set; existing unlock pacing remains intact.
- Added an in-run shop with stable saved stocks of six cards per category and a separate temporary gold balance. Purchases are run-only and singleton-safe; a full deck sends purchases to the bench.
- Validation: Java 17 compilation, native class verification, migration/selection/currency/stock/purchase/fallback checks for all ten commanders and 150 round stocks. Full human gameplay and visual checks remain pending while the original game is running.

- Revised gold based on human playtest: permanent bank, no starting gold, no normal-win gold; bosses5/10/15 award6/10/15 gold with saved duplicate-credit protection. Legacy temporary free gold is not converted; earned bosses in an active older run are credited once.
- Every shop land now costs6gold. Reroll the complete18-card stock for2gold; rolls and debits persist through reloads. Card ownership remains run-only.
- Replaced the broad shop with a hand-selected elite shortlist. Removed Chaos Warp, Infernal Grasp and other weaker fillers. Context gates exclude unsupported Mox Opal, unsuitable green hatebears, irrelevant Urborg/Yavimaya and Cavern for Jace. Existing normal reward packages remain unchanged.
- Rebuilt package selection as filled checkbox tiles with strong selected surfaces/borders and explicit SELECTED/NOT SELECTED labels. Original minimum5/Azula6 remains, with6/7 free available choices and a visible additional locked package on the first screen.
- Replaced all19 Pantlaza speech clips with nonverbal CC0 creature sound design, measured true peaks below-5.8dBFS, corrected captions and guarded both local speech renderers against overwriting nonverbal creatures. Preserved all other characters' runtime/development metadata separately, including Ureni's accepted captions.
- Audited the ten commanders and actual packages for archetype coverage; artifact engines and dedicated Aristocrats are the clearest roster gaps. No extra commander added without a design decision.
- Validation: Java17 build/native class verification, all10commanders and150shop stocks, saved boss ledger, cross-run bank, six-gold lands, paid reroll persistence, visible extra package choices, native WAV format/level checks. New human gameplay/audio/visual test remains pending.

- Added Teysa Karlov, Breya, Etherium Shaper and Eriette of the Charmed Apple as achievement-unlocked commanders; gated existing Shang-Chi behind boss 1. Dedicated achievements use the exact human-selected objectives with cross-run cumulative counters. Native sacrifice/cast events and actual post-replacement life loss determine progress; combat, copied spells, AI casts, poison, life payments and mana burn are excluded where applicable.
- Added 477 reviewed new card entries, seven hand-picked themes per new commander, sixteen signatures each, four-colour Breya fixing, locked roster previews and a paged achievement list. Original Forge reachable duel/card trophies reuse native evaluators and thresholds with independent Rogue persistence; Arena/Quest/event requirements are excluded.
- Validation: 1,974 bank/shop/selection checks across 13 commanders and 195 stocks; native event, save migration, dedicated unlock and lethal-cause checks; Java17 class verification. Human gameplay/visual retest remains pending. Publication remains on hold.

- Every reward package grew by 10-15 hand-picked cards (1,483 additions over 102 pools), checked for colour identity, no Game Changers, no starter/signature duplicates and no digital-only or multiplayer-only cards.
- New run rule: choose 2 packages before a run; 3 more of your unlocked packages join at random. Every reward offers one of your 2 picks.
- 1v1 card ratings: cards built for 4-player Commander (lands that need two or more opponents, goad/voting, symmetric group hug, per-opponent scaling) are rated lower; Rhystic Study and Mystic Remora moderately.
- New commander Karolina Dean replaces Terra; reviewed theme packages for every commander; stronger opponents after the first boss; readable long prompts; enemy intro text fits its panel.