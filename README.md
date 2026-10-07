# Pride Overseer

A real-time-strategy view for Minecraft 1.12.2. You rise above the world, select villagers, golems, pets and NPCs from mods, and give them orders like units in an RTS. You can also run whole villages from above.

> **Status: beta (0.1.0).**

## Features

### The overseer view
- Press **O** to leave your body and float above the world. Press it again to return. Your body stays where it was.
- Camera controls: pan (including edge-pan), rotate, zoom (6–220 blocks), smooth camera, follow-selected and keep-height.
- HUD: a top bar, a village list on the left, a unit card on the right and a command bar with formations at the bottom.
- Also shown: a minimap (size, zoom and rotation can be set), a message log, floating names, health bars, selection rings, order lines and off-screen arrows.
- **Box select**, double-click to select every unit of the same type, Shift to queue orders and **control groups 1–9**. Press `?` to list every key.
- Unit kinds, each with its own icon and ring colour: villager, guard, golem, pet, mount, soldier, worker, NPC and animal.

### Orders
Move (right-click the ground), Hold, Follow me, Guard, Patrol, Roam, Go home, Come here, Rally (to the nearest Rally Flag), Free, Glow and Rename.

- Fighters on guard attack enemies.
- Followers teleport to you when they fall far behind (configurable).
- Formations: box, line, column, circle, wedge and loose.
- Orders are **server-authoritative**. The client only sends requests, and the server checks range, ownership and limits before it carries them out.

### Villages from above
- Vanilla villages and groups of mod NPCs (MCA families, Millénaire, ToroQuest, Ancient Warfare camps, Custom NPCs towns) are detected and given stable names. You can rename them.
- A **village card** shows people by job, children, houses, golems, your reputation and mood, reputation and danger bars.
- Whole-village commands:
  - **Gather**: everyone goes to the square.
  - **Curfew**: everyone goes indoors.
  - **Alarm**: villagers hide and golems guard the square.
  - **Festival**: fireworks, music and hearts.
  - **Normal life**: all orders are cleared.
- A **news feed** reports births, deaths and monsters at the gates.
- **Remote trading**: you can trade with villagers from above. The trade window stays open while you're in the overseer view.
- A villager's card lists what it sells. In creative mode you can also heal, teleport and make units glow.

### Items and blocks (Blockbench models)
| Item | Recipe | Use |
|---|---|---|
| **Command Baton** | diamond / gold ingot / stick (vertical) | Right-click to enter the view. Sneak + right-click a creature to make it follow you (do it again to let it go). |
| **War Table** | banner–map–banner, 3 planks, 2 fences | Right-click to oversee from the table |
| **Rally Flag** ×2 | gold nugget + wool / iron ingot + wool / 3 cobblestone | Place it anywhere. *Rally* sends selected units to the nearest flag. |

## Commands
- `/oversee` (client) opens or closes the view and also works in key macros:
  - `/oversee select all|none|<kind>`
  - `/oversee order <move|hold|follow|guard|patrol|wander|home|come> [x y z]`
  - `/oversee act <stop|glow|rally|heal|tpme>`
  - `/oversee group <1-9>`
- `/overseer list` shows how many nearby units are carrying out orders.
- `/overseer clearall` sends every nearby unit back to normal life.

## Configuration

**Server rules**: `config/prideoverseer-server.cfg`

| Option | Default | Description |
|---|---|---|
| `survival` | `true` | Allow the view outside creative mode |
| `opOnly` | `false` | Only operators may use it |
| `range` | `256` | How far from your body you can see and command (blocks) |
| `maxUnits` | `64` | Most units in one order |
| `hostiles` | `false` | Allow commanding hostile mobs |
| `othersPets` | `false` | Allow commanding other players' pets |
| `remoteTalk` | `true` | Trade and talk with NPCs far from your body |
| `creativeTools` | `true` | Heal / teleport / glow units in creative mode |
| `followTeleport` | `true` | Followers teleport to you when far behind |
| `syncTicks` | `10` | Ticks between unit position updates |

**Client options**: `config/prideoverseer-client.json`, edited in the in-game settings screen. It covers camera speeds and limits, FOV, edge-pan, inversion, labels, rings, health bars, which unit kinds are shown, which panels are visible, minimap size/zoom, panel opacity, formation, guard/roam radius, accent colour and whether your own body is shown.

## Compatibility

Works with any walking creature: vanilla villagers, iron golems and tamed pets. NPCs from **Ancient Warfare 2**, **Custom NPCs**, **MCA**, **Millénaire** and **ToroQuest** are recognised by mod ID. None of them are required, and there are no hard dependencies.

## Requirements

- Minecraft **1.12.2**
- **Forge** 14.23.5.2860+ or **Cleanroom**
- Install it on **both client and server**.

## Building from source

```sh
./gradlew build
```

The jar is written to `build/libs/`. `tools/gen_assets.py` regenerates the item/block textures and models. The `.bbmodel` sources are in `blockbench/`.

## License

[MIT](LICENSE.txt). All code and assets are original to this project.

## Credits

Made with [Claude Code](https://claude.com/claude-code) and [Blockbench](https://www.blockbench.net).

- Inspired by RTS games and by the command baton from **Ancient Warfare 2**. No code from it is used.


## Compile-only jars

The build compiles against these jars in `libs/` (other authors' mods / APIs). They are not included in this repo — get them from their official pages and drop them in `libs/` before building:

- `mixinbooter-api.jar`
- `sponge-mixin.jar`
