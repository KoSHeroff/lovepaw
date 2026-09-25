# Changelog

Notable changes, newest first. Versions are `<mod>+mc<minecraft>`; the Minecraft
half is not part of the mod's own numbering.

## 0.6.0 — 2026-09-25

### Added

- **Chase is a game for however many pets are there.** It used to be strictly a pair: two
  pets agreed between themselves and nobody else could join. Now three come out two against
  one, the sides turn over a few seconds later and it is one against two, with the one
  going for whichever is closer and changing its mind as they move. A pet with two after it
  runs the way that puts distance between it and both at once instead of away from one and
  into the other. Nothing is arranged: each pet works out its own side from its owner and
  the world clock, and works out everybody else's the same way, so every client sees the
  same game without a packet passing between them.

### Changed

- **A pet now behaves like an animal the game already has.** One that walks behaves like a
  tamed cat or wolf: it minds its own business, follows when you get ahead of it, hops what
  it cannot step over, and appears beside you past twelve blocks — which is exactly where
  vanilla gives up on following and teleports. One that flies behaves like an allay
  carrying something you handed it: a looser distance, four blocks counted as close enough,
  its own height held over whatever is underneath.
- **A pack says which of the two its pet is, and nothing else about how it moves.** The
  page of numbers a `pet.json` used to carry — how far it wandered, how fast it walked, how
  far ahead of you it aimed, how often it sat down — is gone. Every one of them asked a
  pack author to invent an animal from scratch, and nobody can guess good values for that;
  the pets that came out of it moved wrongly in ways nobody could name. `"type": "ground"`
  or `"type": "flying"` is the whole of the behaviour block now.
- Pets written before this keep working untouched. `"type": "follow"` still means a walking
  pet and `"hover": true` still means a flying one; any other numbers left in the block are
  ignored.
- A pet's collision box comes from how big it is drawn, so a pet at `scale: 2.0` takes up
  twice the room instead of walking through the doorways it plainly does not fit through.
- **The settings screen is one slider: size.** The others set how far your pet wandered,
  how far ahead of you it aimed and how often it sat — the same invented numbers, asked of
  the player instead. Old values sitting in `config/lovepaw-client.json` are ignored.

## 0.5.0 — 2026-09-24

### Added

- **A pet you do not have arrives by itself.** Anyone can make a pet and put it in their
  own folder, and until now everybody else had to install the same files by hand or see
  nothing. A client that meets a pet it does not have now asks the server for it, the
  server asks whoever is wearing it, and the files come across. Nothing is fetched in
  advance and nothing is fetched twice: a pet moves the first time somebody actually needs
  it, is kept on the server for whoever asks next, and is kept on disk here so meeting it
  again costs nothing at all.
- No resource reload happens for any of it. A downloaded pet is handed straight to the
  texture manager, so nobody is thrown out of what they were doing to load somebody's cat,
  and a later reload does not take it away again.
- Downloaded pets stay out of the picker. They belong to whoever made them and are only
  ever drawn on that player.
- `share_player_pets` in the server config turns the whole thing off for a server that
  would rather not carry content it cannot look at; `download_player_pets` in the client
  config turns it off for a player who would rather not receive any.

### Changed

- **A pet is now identified by its files, not by its name.** Two players can each have a
  `local:cat` of their own making, and the server only ever said the name — so one of them
  was quietly shown the other's cat, with nothing to suggest anything was wrong. Every pet
  now carries a hash of the files it is made of, and a pet whose files do not match is not
  drawn as one we have.
- Reading a pet goes through one door whatever it came from, so a pet that arrives from
  another player is read exactly as strictly as one the player installed themselves. A
  file left lying in a pet's folder is neither read nor part of the pet.
- The network protocol changed with it. A client and a server on different versions of
  LovePaw now leave each other alone instead of misreading each other, which means other
  players' pets stay hidden until both are updated.

## 0.4.1 — 2026-09-24

### Changed

- Every pet a client knows about is simulated again, on screen or not. Skipping the ones
  nobody was looking at saved a few microseconds each and cost the thing those pets are for:
  a game of chase only existed while somebody watched it, so two pets could never come
  tearing past already in the middle of one. Drawing is still skipped for what cannot be
  seen, which is where the cost actually was — twenty-five microseconds a pet against six.

## 0.4.0 — 2026-09-24

### Added

- **Pets play with each other.** When another player's pet comes within
  `interest_radius`, the two of them may start a game of chase: one runs, the other goes
  after it, and they swap over every few seconds until the game runs out. Neither pet tells
  the other anything — whether there is a game on, who chases and how long it lasts all come
  out of dice seeded from the pair of owners and the stretch of world time the game belongs
  to, so both players watch the same chase. New `playfulness` in `pet.json`; the shyer of
  two pets sets the odds, so `playfulness: 0` keeps a pet out of games rather than leaving
  it to be chased.

## 0.3.0 — 2026-09-24

### Changed

- Pets further than 64 blocks, or outside the camera's view, are no longer drawn. Their
  animation clock keeps running, so one that walks back into view is where it should be.
- Bodies and shadows are drawn in separate passes rather than alternating per pet: they use
  different render types, and swapping between them ended a batch every time. Measured with
  a crowd of stand-in pets: a thousand went from 25 ms a frame to 16, and five thousand with
  your back to them from 125 ms to 12.
- Somebody else's pet is only simulated while it is on your screen — no behaviour, no
  collision, no route finding otherwise. Your own pet is always simulated, since it is
  either beside you or on its way there.
- A pet's decisions now come from dice seeded by its owner and the world clock, taken on a
  shared grid of ticks. Two players watching the same pet see it do the same thing, rather
  than one watching it sit on a chest while the other watches it sniff a flower.

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
