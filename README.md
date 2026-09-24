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
doing the same thing. Your own pet is always simulated; somebody else's is only simulated while it is on your
screen.

If someone wears a pet you do not have installed, nothing is drawn for them — this is the
point where a future pet catalogue would offer to download it.

## Using it

- **G** opens the pet picker (rebindable in Controls; vanilla already uses P for social
  interactions, so the default stays out of its way)
- Pick a pet, or "No pet" to put it away
- The picker also toggles whether you see other players' pets

A pet lives in a patch of ground rather than on a leash. While you stay near where you
settled it ignores you and gets on with its own life — wandering to spots it picks itself,
going over to look at things it notices (a bed, a sign, a painting, somebody else's
chicken), sitting down, looking around. Walk far enough away and it moves house: it works out where
you are heading and runs to a spot *there*, arriving alongside you rather than trailing
behind. How big its patch is, how far ahead it aims and how often it sits are all per-pet
settings — see [docs/pet-format.md](docs/pet-format.md).

The **Settings** button in the picker tunes your own pet with live sliders — how far you
can wander off before it moves, how far ahead of you it aims, how big its patch is, how
often it sits, its size, and whether it wanders at all. The world keeps running behind the
screen, so you watch each change take effect. "Back to pet defaults" clears the lot.

Those tweaks live in `config/lovepaw-client.json` alongside your pet choice:

```json
{
  "selected_pet": "lovepaw:cat",
  "show_other_players_pets": true,
  "overrides": {
    "anchor_radius": 5.0,
    "prediction_seconds": 1.2,
    "wander_radius": 4.5,
    "sit_chance": 0.35,
    "wander": true,
    "scale": 1.0
  }
}
```

Anything left out of `overrides` is left to the pet, which is the point: a pack author's
numbers stay in force until you actually decide otherwise. Tweaks apply to your own pet
only — other players' pets keep the settings their packs give them.

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
— then its id is `your_namespace:pet_name`. The full format reference, including every
behaviour knob and how to export from Blockbench, is in
[docs/pet-format.md](docs/pet-format.md).

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
