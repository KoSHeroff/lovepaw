# LovePaw

![Build](https://github.com/KoSHeroff/lovepaw/actions/workflows/build.yml/badge.svg)

Cosmetic pets that walk beside you in Minecraft. Pets are **data, not entities**: they are
loaded from resource packs, simulated and drawn on the client, so the mod works on any
server — including a vanilla one, where only you see your pet.

Install it on the server too and everyone sees everyone's pets.

- **Fabric and NeoForge**, Minecraft 1.21.1 (more versions are one line of build config away)
- Pets are made in **Blockbench** and shipped as plain JSON — no code, no compiling
- No hard dependencies: the Bedrock model, animation and Molang support is built in

Russian version of this file: [README.ru.md](README.ru.md).
Making a pet: [docs/pet-format.md](docs/pet-format.md).
What changed when: [CHANGELOG.md](CHANGELOG.md).

## How it works

A pet is not a Minecraft entity. Nothing is registered, nothing is spawned, nothing is
saved in the world.

| | Mod on client only | Mod on client and server |
|---|---|---|
| You see your own pet | yes | yes |
| Others see your pet | no | yes |
| Server load | none | one tiny packet per pet change |

The server never sends positions. It only relays *who owns which pet*; every client runs
the same follow behaviour locally. That is what keeps it cheap, and what lets the client
half work on a server that has never heard of the mod.

Running it locally does not mean running it differently: a pet's decisions come from dice
seeded by its owner and the world clock, so every client watching it sees the same pet
doing the same thing. Every pet your client knows about is simulated whether or not you are looking at it — only
drawing is skipped — so a pair of them can come past you already in the middle of a game.

If someone wears a pet you do not have installed, nothing is drawn for them — this is the
point where a future pet catalogue would offer to download it.

## Using it

- **G** opens the pet picker (rebindable in Controls; vanilla already uses P for social
  interactions, so the default stays out of its way)
- Pick a pet, or "No pet" to put it away
- The picker also toggles whether you see other players' pets

A pet behaves like an animal already in the game. One that walks behaves like a tamed cat
or wolf; one that flies behaves like an allay carrying something you handed it. A pack says
which of the two its pet is, and nothing else about how it moves — those numbers are the
game's, not a list of sliders to guess at.

So: it lives in a patch of ground rather than on a leash. While you stay near where you
settled it ignores you and gets on with its own life — strolling to spots it picks itself,
going over to look at things it notices (a bed, a sign, a painting, somebody else's
chicken), playing chase with another player's pet that wanders past, sitting down, looking
around. Walk far enough away and it moves house: it works out where you are heading and
runs to a spot *there*, arriving alongside you rather than trailing behind. Get properly
far ahead and it appears beside you, the way a cat does.

The **Settings** button in the picker sets how big your own pet is, and that is all there
is on it. The world keeps running behind the screen, so you watch the change take effect.

It lives in `config/lovepaw-client.json` alongside your pet choice:

```json
{
  "selected_pet": "lovepaw:cat",
  "show_other_players_pets": true,
  "download_player_pets": true,
  "overrides": {
    "scale": 1.0
  }
}
```

An empty `overrides` leaves the pet the size its author drew it. It applies to your own pet
only — other players' pets stay as their authors made them.

Server settings live in `config/lovepaw-server.json`:

```json
{
  "share_with_others": true,
  "allowed_pets": [],
  "denied_pets": []
}
```

`share_with_others: false` keeps pets private to their owner. An empty `allowed_pets`
means everything that is not in `denied_pets` is allowed.

## Adding a pet

Drop a folder into `lovepaw/pets/` in your game directory — the mod makes it on first run:

```
.minecraft/lovepaw/pets/<pet_name>/
    pet.json
    model.geo.json
    texture.png
    animations.animation.json
```

Reload resources (F3 + T) and it is in the picker as `local:<pet_name>`. No pack to build,
nothing to enable: export from Blockbench into the folder and press the key.

The same folder shipped inside a resource pack, under
`assets/<your_namespace>/lovepaw/pets/<pet_name>/`, is how you hand a pet to somebody else
— then its id is `your_namespace:pet_name`. The full format reference, including how to
export from Blockbench, is in [docs/pet-format.md](docs/pet-format.md).

## Building

```bash
./gradlew build                      # every target
./gradlew :1.21.1-fabric:build       # one target
./gradlew :1.21.1-fabric:runClient   # dev client
./gradlew :1.21.1-fabric:test        # parser tests
```

Jars land in `versions/<target>/build/libs/`.

Targets are declared in `settings.gradle` and their dependency versions in
`versions/<target>/gradle.properties`. Adding Minecraft 1.21.11, say, means one line in
each. [Stonecutter](https://stonecutter.kikugie.dev/) keeps one shared source tree across
all of them; both loaders build against Mojang mappings, so shared code needs no
per-loader name swaps.

Source layout:

```
src/main/java       shared code (model, animation, behaviour, rendering, protocol)
src/fabric/java     Fabric entry points
src/neoforge/java   NeoForge entry points
src/main/resources  mod metadata, language files, the built-in pets
src/test/java       parser and coordinate-conversion tests
```

## Add-on API

Not published yet — deliberately. The JSON format is the supported way to add pets today,
and it is the only one that can ever be shipped over a network. The internals are built
around a single registry entry point so a Java API for add-on mods can be opened later
without rewriting anything, but until it is published there is no stable API to code
against.

## Status

Working: loading pets from packs and from the player's own folder, Bedrock models and animations with Molang, the follow
behaviour with wandering, curiosity about what is nearby, sitting, collision, step-up,
hopping over what it cannot step onto, routing round what it cannot hop over, and swimming, the picker, client-side rendering with shadows, and server relay on
both loaders.

Not there yet: a pet catalogue with downloads, sounds, particle and sound keyframe
effects, per-face UV rotation, and the published Java API.

A dev client boots clean with the built-in pets loaded and the server handshake working,
and parsers, coordinate conversion and behaviour are covered by unit tests. What no test
can check is how the pet looks on screen, so expect to shake out visual details by
playing.

## Licence

MIT — see [LICENSE](LICENSE). Written by KoSHer.
