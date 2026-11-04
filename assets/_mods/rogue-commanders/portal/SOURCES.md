# Portal art sources — 2026-10-16

Three original layers generated separately with built-in OpenAI Imagegen (`image_gen.imagegen`) and transparent-background mode. The glow received one further Imagegen refinement using its own first generated version as the edit target. No third-party artwork, external reference image, local GPU, or procedural substitute was used. Exact prompts, the glow's edit-source prompt, and native paths are in `../enemy-portraits/generation-manifest.json` (keys `portalframe`, `portalvortex`, `portalglow`).

Delivery: `portal-frame.png`, `portal-vortex.png`, `portal-glow.png`, all 1024 × 1024 RGBA. Generated transparency is retained. Only uniform resizing, transparent canvas padding and positioning are used to fit the layers. No chroma key, alpha reconstruction, or invented replacement pixels. `preview.png` composites them over a dark background for review.

Use the same destination rectangle and the centre `(512, 512)` as origin for all three layers. Draw order: glow, vortex, frame. Keep the frame static; rotate the vortex about its centre and pulse/tint the glow. Vortex and glow use pale neutral colours for commander tinting. The frame is fit within a 920-pixel square; the vortex within a 650-pixel square, with its outer edge under the stone ring. The glow retains the full canvas.

See `../enemy-portraits/validation.json` for hashes and alpha bounds. No Java/Scene or installer changes are included. Claude owns animation and runtime integration.

Provenance/licence note: AI-generated project assets; no third-party source-image licences attached. No public-domain, exclusive-copyright, or legal-clearance status is asserted. No release or publication changes.
