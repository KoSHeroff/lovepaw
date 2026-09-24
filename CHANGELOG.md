# Changelog

Notable changes, newest first. Versions are `<mod>+mc<minecraft>`; the Minecraft
half is not part of the mod's own numbering.

## 0.2.0 — 2026-09-24

### Added

- **Pets load from a folder in the game directory.** `lovepaw/pets/<name>/`, created on
  first run, read on every resource reload (F3 + T). No `pack.mcmeta`, no namespace,
  nothing to enable; the pet appears as `local:<name>`. Files are named plainly, next to
  `pet.json`.
- **Route finding.** A pet that cannot get over what is in its way works out a way round,
  block by block, using its own width and step height to decide what counts as a step. It
  only searches once walking straight has plainly failed, and keeps a route until its
  destination moves.
- **Curiosity.** Between other things, a pet goes to look at something it noticed — a bed,
  a sign, a banner, a painting or item frame, a campfire, a jukebox, a chest, an anvil,
  amethyst, or anything alive that is not its owner. It stands in front of the thing for a
  few seconds, sometimes sitting down, and remembers the last handful it has studied. New
  `curiosity` and `interest_radius` in `pet.json`; `curiosity: 0` keeps the old behaviour.
- **Flying pets choose their own height.** They hold `hover_height` above whatever is under
  them, wander up and down within the new `hover_drift`, climb over what blocks them and
  duck under low ceilings. They only follow the owner vertically once the owner is well
  above them.
- **Catbee**, a third built-in pet: a bee with a cat's face that hovers. Its wings flap
  through Molang rather than keyframes.

### Changed

- `hover_height` is measured from the ground under the pet, not from the owner's feet. A
  pet that used to hang level with its owner on a hillside now flies over the hill.

## 0.1.0 — 2026-09-24

First working version.

### Added

- Pets as data: a folder of JSON in a resource pack, loaded, simulated and drawn on the
  client, so the mod works on any server — including a vanilla one, where only its owner
  sees the pet.
- Bedrock geometry, animations and Molang parsed in the mod, with no library dependency.
- The follow behaviour: a pet lives in a patch of ground rather than on a leash, wanders,
  sits, glances about, and moves house when its owner leaves the area.
- Movement: collision, stepping up, hopping over what it cannot step onto, swimming, and a
  rescue for a pet that ends up wedged.
- The picker and a settings screen with live sliders, saved to `config/lovepaw-client.json`.
- Server relay for Fabric and NeoForge, so everyone sees everyone's pets when the server
  has the mod, and nothing is sent when it does not.
- Two pets in the jar: a cat and a catopillar.
- MIT licence, and a build on GitHub Actions for both loaders.
