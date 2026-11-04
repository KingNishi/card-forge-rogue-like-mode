# Enemy portrait sources — 2026-10-16

Original AI-generated painted game illustrations made with the built-in OpenAI Imagegen tool (`image_gen.imagegen`). Eight separate generation calls, six subjects per call. No stock image, downloaded illustration, extracted Adventure avatar, card illustration, or local GPU model was used. No reference image was supplied. Named characters are newly generated interpretations.

`generation-manifest.json` records the exact prompts, native source paths, delivery paths and SHA-256 hashes. `sheets/sheet_1.png` through `sheet_8.png` are the native 1536 × 1024 outputs. Each sheet is three columns by two rows; reading order is left to right, then top to bottom. The 48 delivered 512 × 512 portraits are lossless integer-coordinate crops, with no painting or resizing. `portraits.json` maps the exact requested names to filenames; apostrophes become hyphens, e.g. `Chandra's Pyromancer` → `chandra-s-pyromancer.png`.

`preview.png` is a labelled review contact sheet, not a game asset. Columns correspond to the eight sheets; each column reads downward through its six portraits. `validation.json` records dimensions and hashes. `prepare_assets.py` reproduces the mechanical slicing and portal layer fitting from the native source paths.

Provenance/licence note: these are AI-generated project assets without third-party source-image licences. No public-domain, exclusive-copyright, or legal-clearance status is asserted. This delivery does not publish or alter any public release.


## Act opponent extension - 2026-10-18

Four new original built-in OpenAI ImageGen calls supplied sheets 9..12, each native exact 1536 x 1024, with six 512 x 512 cells. The exact prompts and source/output SHA-256 hashes are in generation-manifest.json; jobs and the append-only reproduction script are in act-opponents-20261018/. No reference images, official illustrations, extracted assets or local GPU jobs were used for these sheets. Named bosses are original archetype interpretations with newly designed anatomy/costumes.

All 24 new portraits are lossless crops at integer cell coordinates. portraits.json now contains 72 portraits, and preview.png has twelve columns. Original 48 portrait file hashes were frozen before work and verified byte-identical afterwards. Existing portal assets were untouched. prepare_assets.py has the extended NAMES and protects existing original portraits from rewriting; the actual extension used act-opponents-20261018/prepare_new_portraits.py to avoid touching portal files. Metadata backup: act-opponents-20261018/metadata-before/.

Visual QA: sheet subject/order/style and 512px crops reviewed; raptor/dragon/imp and three demon archetypes distinct, celestial and alien silhouettes distinct, all six new bosses present, no labels/frames/grid lines. validation.json records each new crop rectangle, dimensions, hash and lossless result. Original dark-fantasy painted style retained. This delivery stays local; no public release changes.
