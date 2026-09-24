# Making a pet

A pet is a folder of files in a resource pack. No code, no compiling, no registration.

```
assets/<namespace>/lovepaw/pets/<pet_name>/
    pet.json                  the description below
    cat.geo.json              Blockbench "Bedrock Model" export
    cat.png                   the texture
    cat.animation.json        Blockbench animation export (optional)
```

The pet's id is `<namespace>:<pet_name>`, taken from the path. Two packs can each ship a
`cat` as long as their namespaces differ. A pack that uses an id another pack already used
replaces it, which is how you re-skin a pet you did not make.

After adding the folder, press **F3 + T** in game to reload resources; the pet appears in
the picker (**G**).

## Without a resource pack

While you are making a pet, building a pack around it every time is a waste. Drop the
folder straight into the game directory instead:

```
.minecraft/lovepaw/pets/<pet_name>/
    pet.json
    cat.geo.json
    cat.png
    cat.animation.json
```

The mod creates `lovepaw/pets/` on first run, so it is already there waiting. Export from
Blockbench into the folder, press **F3 + T**, and the pet is in the picker — no
`pack.mcmeta`, no namespace, nothing to enable.

The id of a pet loaded this way is `local:<pet_name>`, so it can never collide with a pet
from a pack, and the folder name has to work as one: lowercase letters, digits, `_`, `-`
and `.`. Files are named plainly, sitting next to `pet.json`; the `namespace:path/file`
form a pack may use does not work here, because these files are not in a pack.

Other players will not see a pet you loaded this way — they do not have the files. That is
the same as with a resource pack only you have installed.

## In Blockbench

1. **New Model → Bedrock Model.** Do not use "Modded Entity" or "Java Block/Item".
2. Build the model. Keep the feet at `y = 0`; the pet is placed on the ground by that point.
   The model faces the same way vanilla mobs do.
3. Name bones deliberately — animations refer to them by name.
4. **File → Export → Export Bedrock Geometry** for the `.geo.json`.
5. For animations use the Animate tab, then **File → Export → Export Animations** for the
   `.animation.json`.
6. Texture sizes are up to you; the geometry file records them.

Anything Blockbench writes for a Bedrock model is understood: bone hierarchies, per-cube
rotation and pivot, inflate, mirror, box UVs and per-face UVs. Not yet supported:
per-face UV *rotation*, polymesh (non-cube) geometry, and texture meshes.

## pet.json

```json
{
  "format_version": 1,
  "name": "Cat",
  "author": "Your name",
  "version": "1.0.0",

  "model": "cat.geo.json",
  "texture": "cat.png",
  "animation_file": "cat.animation.json",

  "animations": {
    "idle": "animation.cat.idle",
    "walk": "animation.cat.walk",
    "run": "animation.cat.run",
    "sit": "animation.cat.sit",
    "fall": "animation.cat.fall",
    "swim": "animation.cat.swim",
    "jump": "animation.cat.jump"
  },

  "render": {
    "scale": 1.0,
    "y_offset": 0.0,
    "shadow_radius": 0.3,
    "glow": false
  },

  "behaviour": {
    "type": "follow",
    "anchor_radius": 8.0,
    "prediction_seconds": 1.2,
    "stop_distance": 1.4,
    "run_distance": 5.0,
    "teleport_distance": 20.0,
    "walk_speed": 0.16,
    "run_speed": 0.3,
    "gravity": 0.08,
    "step_height": 1.0,
    "jump_power": 0.42,
    "width": 0.5,
    "height": 0.7,
    "can_swim": true,
    "hover": false,
    "hover_height": 0.0,
    "wander": true,
    "wander_radius": 4.5,
    "wander_speed": 0.1,
    "sit_chance": 0.35
  }
}
```

### Top level

| Field | Meaning |
|---|---|
| `format_version` | Always `1` today. A pet asking for a newer version is skipped with a message rather than half-loaded. |
| `name` | Shown in the picker. |
| `author`, `version` | Credit and your own versioning; shown in the picker's tooltip. |
| `model` | The `.geo.json`. A bare file name is resolved next to `pet.json`; `namespace:path/to/file.geo.json` also works. |
| `texture` | The `.png`, same resolution rules. |
| `animation_file` | The `.animation.json`. Omit it for a pet that never animates. |
| `animations` | Which animation plays in which state, by the name inside the animation file. |

### States

`idle`, `walk`, `run`, `jump`, `fall`, `swim`, `sit`.

Only `idle` is really needed. A state with no animation falls back: `run` falls back to
`walk`, `swim` falls back to `walk`, `jump` falls back to `fall`, and everything else falls
back to `idle`. `sit` is only ever used if you provide it — a pet without a sit animation
wanders instead of sitting.

`jump` plays while the pet is rising, `fall` once it is dropping. A `jump` animation with
`"loop": "hold_on_last_frame"` holds its last frame for as long as the pet is in the air
rather than starting over.

### render

| Field | Default | Meaning |
|---|---|---|
| `scale` | `1.0` | Size multiplier, capped at 8. |
| `y_offset` | `0.0` | Vertical nudge in blocks, if your model does not sit on zero. |
| `shadow_radius` | `0.3` | Drop shadow radius in blocks; `0` turns it off. |
| `glow` | `false` | Draw at full brightness, ignoring world light. |

### behaviour

Distances are blocks, speeds are blocks per tick (multiply by 20 for blocks per second).

| Field | Default | Meaning |
|---|---|---|
| `type` | `follow` | Only `follow` exists so far. The field is read now so packs stay valid when more arrive. |
| `anchor_radius` | `8.0` | How far you can stray from the pet's patch of ground before it moves the patch. Clamped to 1–48. |
| `prediction_seconds` | `1.2` | How far ahead of you it aims when you are on the move. `0` aims straight at you. |
| `stop_distance` | `1.4` | How close to a target counts as arrived. |
| `run_distance` | `5.0` | Beyond this to its target it uses `run_speed`. |
| `teleport_distance` | `20.0` | Beyond this *from you* it gives up and appears next to you. Keep it comfortably above `anchor_radius` plus `wander_radius`, or a wandering pet will teleport for no reason. Clamped to 4–64. |
| `walk_speed` | `0.16` | Roughly a walking player is `0.21`. |
| `run_speed` | `0.30` | A sprinting player is roughly `0.28`. |
| `gravity` | `0.08` | Fall acceleration per tick. |
| `step_height` | `1.0` | How high a ledge it walks up instead of into. |
| `jump_power` | `0.42` | Upward speed of a hop, used when it walks into something taller than `step_height`. Vanilla's `0.42` clears one block; `0` for a pet that never jumps. |
| `width`, `height` | `0.5`, `0.7` | Collision box. Keep it close to the model or it will snag on doorways. |
| `can_swim` | `true` | Float in water instead of sinking. |
| `hover` | `false` | Ignore the ground entirely and fly — for bees, ghosts, orbs, fairies. |
| `hover_height` | `0.0` | How high above **the ground under it** a flying pet likes to be. Clamped to 0–8. |
| `hover_drift` | `1.0` | How far above and below that it wanders of its own accord. `0` holds one height exactly. Clamped to 0–4. |
| `wander` | `true` | Potter about on its own once it has caught up. |
| `wander_radius` | `4.5` | How far from the owner it will wander. Clamped to 1.5–16. |
| `wander_speed` | `0.10` | Speed while wandering; slower than `walk_speed` reads as ambling. |
| `sit_chance` | `0.35` | Odds of sitting down instead of wandering, once it has nothing to do. |
| `curiosity` | `0.4` | Odds of going to look at something nearby instead of wandering to a spot of its own. `0` for a pet that never investigates anything. |
| `interest_radius` | `10.0` | How far around its patch it notices things worth a look, other pets included. Clamped to 0–24. |
| `playfulness` | `0.5` | Odds of starting a game of chase with another player's pet that comes near. `0` for a pet that keeps itself to itself. |

### What the pet does with all this

A pet lives in a patch of ground rather than on a leash. The patch is anchored wherever
you last settled, and `wander_radius` is its size.

- **while you stay within `anchor_radius` of that anchor** the pet ignores you and gets on
  with its own life: **resting** (standing, glancing your way now and then and off
  elsewhere the rest of the time — it holds each look for a few seconds rather than
  tracking you), **wandering** to a spot in the patch it picked itself, **going to look at
  something** it noticed, or sitting down for a while with odds of `sit_chance`
- **when you leave that radius** it moves house instead of giving chase: it works out
  where you are heading from how you are moving, anchors the patch `prediction_seconds`
  ahead of you, and runs to a spot in it — so it arrives alongside you rather than
  trailing behind. While you keep moving the prediction keeps updating; the moment you
  stop, the anchor settles where you stopped and the pet goes back to its own business
- **walking into something** taller than `step_height` — a full block, a fence post — it
  hops over it, if its `jump_power` is not zero
- **meeting something it cannot get over** — a wall, the corner of a house — it works out a
  route round, block by block, and follows that until the place it was heading for moves.
  It only bothers once walking straight has plainly failed, so the search costs nothing in
  the open. A flying pet climbs over it instead

A flying pet picks its own height rather than hanging at a fixed distance from you: it
holds `hover_height` above whatever happens to be under it, drifts up and down within
`hover_drift` of that on its own, rises when it runs into something, and ducks under a low
ceiling. It only pays attention to where you are vertically when you get well above it —
climb a tower and it will come up after you rather than wait by the ground.
- **beyond `teleport_distance`**, or if it gets properly wedged on geometry, it gives up
  and appears next to you. The same rescue covers a pet that ends up inside a block, from
  a teleport into a tight spot or from a block placed on top of it

When another player's pet comes within `interest_radius`, the two of them may start a game
of chase: one runs, the other goes after it, and they swap over every few seconds until the
game runs out. Neither pet tells the other anything — both work out from the pair of owners
and the world clock whether there is a game on and who is chasing, so both players watch
the same chase. The shyer pet sets the odds, so `playfulness: 0` keeps a pet out of games
altogether rather than leaving it to be chased. A pet being chased stays inside its own
patch: it runs round the far side of it rather than back into whoever is after it.

Things worth a look are the ones a player would notice: beds, signs, banners, paintings and
item frames, candles and campfires, jukeboxes and note blocks, chests and barrels, anvils,
bookshelves, amethyst, decorated pots — and anything alive that is not you. Stone and dirt
are everywhere and say nothing, so they are not on the list. The pet ambles over, stands in
front of the thing for a few seconds — sometimes sitting down to look at it properly — and
then gets on with something else. It remembers the last handful of things it has studied,
so it does not shuttle between the same two all afternoon.

Set `curiosity: 0` for a pet that ignores the world, `wander: false` for a pet that should
stand still in its patch, and
`prediction_seconds: 0` for one that heads straight at where you are rather than where
you are going. A small `anchor_radius` (say 3) gives the glued-to-your-heels feel.

Two things are worth knowing about how this is run. Your own pet is always simulated — it
is either beside you or on its way there — while somebody else's pet thinks only while it
is on your screen: off screen its behaviour, collision and route finding are all skipped,
and it carries on from where it stood when it comes back into view. And every decision is
taken on a shared clock with dice seeded from the pet's owner and the world time, so two
players watching the same pet watch it do the same thing — one of them does not see it
sitting on a chest while the other sees it sniffing a flower.

Note that a player can override `anchor_radius`, `prediction_seconds`, `wander_radius`,
`sit_chance`, `wander` and `scale` for their own pet from the settings screen. Your values
are the default and stay in force until they change something, but do not rely on them
being exact.

## Animations

Standard Blockbench Bedrock animations. Times are seconds, rotations degrees, positions
model units (16 per block), and everything means what it means in Blockbench.

```json
{
  "format_version": "1.8.0",
  "animations": {
    "animation.cat.walk": {
      "loop": true,
      "animation_length": 0.8,
      "bones": {
        "leg_front_left": {
          "rotation": {
            "0.0": [32, 0, 0],
            "0.4": [-32, 0, 0],
            "0.8": [32, 0, 0]
          }
        }
      }
    }
  }
}
```

Supported: `loop` (`true`, `false`, `"hold_on_last_frame"`), `animation_length`, the
`rotation` / `position` / `scale` channels, constant values as well as keyframe maps,
`lerp_mode` of `linear` / `catmullrom` / `step`, and `pre` / `post` pairs for a hard cut.

Not supported yet: `sound_effects`, `particle_effects` and `timeline` entries. They are
ignored rather than rejected, so an animation using them still plays.

### Molang

A keyframe value can be an expression instead of a number:

```json
"tail": {
  "rotation": ["0", "math.sin(query.anim_time * 90) * 14", "0"]
}
```

Available: arithmetic, comparisons, `&&`, `||`, `!`, the ternary `? :`, variables
(`variable.x` / `v.x`, assignable with `v.x = ...;`), and the `math.*` functions —
`abs acos asin atan atan2 ceil clamp cos exp floor hermite_blend lerp lerprotate ln max
min mod pow random random_integer round sin sqrt trunc`, plus `math.pi`. Trigonometry is
in degrees, as Molang specifies.

Queries: `query.anim_time` (seconds into the current animation), `query.life_time`
(seconds since the pet appeared), `query.ground_speed` (blocks per second),
`query.is_on_ground` (0 or 1).

Any query or function that does not exist evaluates to `0`, so an animation written for
full Bedrock Molang still plays instead of failing.

## Limits

A model may have at most 256 bones, 512 cubes per bone and 2048 cubes in total. These
exist because pets are content from strangers: a broken or hostile file should fail with a
message, not hang the client.

## When it does not show up

Everything is logged with the file path. Check `logs/latest.log` for `LovePaw`.

| Symptom | Usual cause |
|---|---|
| Pet missing from the picker | `pet.json` failed to parse, or the folder is not under `lovepaw/pets/`. The log names the file and the reason. |
| A pet in `lovepaw/pets/` is missing | The folder name is not a valid id (uppercase or spaces), one of its files is not there, or `pet.json` names a file as `namespace:path`. The log says which. |
| Purple and black model | The `texture` path does not point at your png. |
| Model inside out or mirrored | Exported as something other than "Bedrock Model". |
| Nothing animates | The names in `animations` do not match the names inside the animation file. |
| Others cannot see it | The server does not have the mod, has `share_with_others: false`, or they do not have your pack. |
