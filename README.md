# Atlas

A client-side minimap, world map and waypoint mod for Minecraft, with a 2D and a 3D view of what you have explored.

Press **M** for the map, **B** to add a waypoint where you stand, **V** to switch between 2D and 3D. The map is built only from chunks your client has loaded. Nothing is sent to the server and no server mod is needed.

## What it does

- Minimap: round or square, rotating or north up, six zoom levels, any corner, size and opacity, coordinates, biome, light level, compass letters, waypoints, and a radar with separate switches for players, hostile and passive mobs. Hold the player list key (Tab) and the radar names every entity and dropped item. It can be tilted into a 3D view.
- Fullscreen map: drag to pan, scroll to zoom, dimension switch, cave layer slider, chunk grid, waypoint list with search, double click to add a waypoint, right click to edit, hide, delete or teleport. The 2D view is drawn at the full resolution of the display, whatever its size.
- 3D map: the same terrain with an orbit camera, including what is under tree canopies, roofs and other overhangs. Drag to orbit, right drag to move, scroll to zoom. Switching between 2D and 3D keeps the position and dimension.
- Cave mode: slices the map at a height. Auto turns it on underground and in dimensions with a ceiling.
- Waypoints: name, color, icon, group, dimension, per waypoint and per group visibility. In the world they are an icon fixed to their block with a beacon beam, and show their name and distance when you look at them or always. Scale, fade distance and what happens behind terrain can be set.
- Death waypoints with a limit and an expiry, temporary waypoints that go away on arrival or after a while.
- Import and export as JSON. Importers for Xaero's Minimap (`XaeroWaypoints/<world>/dim%*/*.txt`), JourneyMap (`journeymap/data/*/<world>/waypoints/*.json`) and VoxelMap (`voxelmap/*.points`). JourneyMap's newer single file NBT store is not read.
- Click to teleport is off by default, and only shown when you have permission to run the command.
- Worlds behind one server address, like a lobby and a survival world on a proxy, each get their own map and waypoints.

Every option is in the Atlas card of the [Proteus](https://github.com/OlympusMods/Proteus) menu and in `config/atlas.json`. All keys are normal keybinds under Controls, Atlas.

## Files

```
config/atlas.json                                   settings
atlas/<world>/waypoints.json                        waypoints of one world or server
atlas/<world>/map/<namespace>/<dimension>/surface   explored regions, 512 x 512 blocks per file
atlas/<world>/map/<namespace>/<dimension>/y<N>      cave layers, one folder per 8 blocks of height
atlas/<world>/worlds.json                           the worlds seen behind this server address
atlas/<world>/worlds/<N>/                           waypoints and map of the second world and up
atlas/export, atlas/import                          waypoint exports and files to import
```

`<world>` is `sp_<save folder>` or `mp_<server address>`.

## Worlds on one address

A proxy puts several worlds behind one address, and they would all draw into one map. Atlas tells them apart on its own and keeps what it knows about each in `worlds.json`. No single sign is reliable everywhere, so it weighs several each time you enter a world:

- The seed hash the server sends. It is checked against the saved map, so a network that sends the same hash for every world, or a new one on every join, is found out and its hashes are ignored from then on.
- Where you were. Atlas remembers where you last stood in every world and where you arrive when you join, and coming back to one of those places counts for that world.
- The terrain around you, compared with the maps already saved. Biomes count more than heights, because building, digging and a rebuilt lobby change the heights but not the biomes. An area with a single biome is not enough to be sure.

A world that fits none of the known ones starts a new map in `worlds/<N>`. Going through a portal stays in the same world. Until Atlas has decided, nothing is drawn into any map.

It can still be wrong, mostly on servers that hide the seed and drop you in a random unexplored place. "This world" in the Data tab of the settings steps through the known worlds or starts a new one, and Atlas remembers that answer. What only the server knows, like a world behind a proxy that looks exactly like another, it cannot find out.

The first world keeps using `atlas/<world>` itself, so maps from before this existed carry on. A map that already has two worlds drawn over each other stays that way, delete its folder to start clean. "Separate worlds on one address" in the settings turns all of this off.

## Server rules

Atlas follows the system chat codes other map mods use, so a server that already limits those is respected:

- Xaero's: `§f§a§i§r§x§a§e§r§o` turns off cave mode and the radar, `§x§a§e§r§o§w§m§n§e§t§h§e§r§i§s§f§a§i§r` allows cave mode in the Nether again, `§n§o§m§i§n§i§m§a§p` turns off the minimap, `§r§e§s§e§t§x§a§e§r§o` resets.
- VoxelMap: `§3 §6 §3 §6 §3 §6 §e` turns off the radar and `§3 §6 §3 §6 §3 §6 §d` cave mode, `§0` and `§f` at the end turn them back on.

The rules reset when you leave the server. "Follow server rules" in the settings turns this off.

## How it works

How it works: `core/.../core` is plain Java with no Minecraft in it (region format, tile baking, the 2D painter, the 3D ray caster, waypoints, importers, server rules) and the tests cover it. `client` connects it to the game. All map images are drawn into pixel buffers on worker threads and the game thread only uploads textures and draws them through the Proteus canvas, which is why the same code runs on every range and stays out of the way of Sodium and Iris.

The minimap and the 3D view are one texture, redrawn when the view changes. The 2D fullscreen map is cut into pages of 512 x 512 pixels that line up with the regions, one set per zoom level. A page is painted once, uploaded, and then only moved and scaled by the graphics card, so panning and zooming cost nothing and the map is as sharp as the display. Pages are repainted when the regions under them change. The 3D view is still ray cast on the processor, at a resolution set by "Render resolution".

Atlas needs Proteus 1.2 or newer, and Fabric API on Fabric. The build takes Proteus from Modrinth's Maven.

One source tree builds a jar for every Minecraft range and loader below. Shared code lives in `core`, and a Minecraft range only holds the files that changed from the range before it.

## Supported versions

| Minecraft | Folder | Fabric | Forge | NeoForge |
|-----------|--------|:------:|:-----:|:--------:|
| 1.20-1.20.2 | `1.20` | yes | yes | n/a |
| 1.20.3-1.20.4 | `1.20.3` | yes | yes | yes |
| 1.20.5-1.20.6 | `1.20.5` | yes | yes | yes |
| 1.21-1.21.1 | `1.21` | yes | yes | yes |
| 1.21.2-1.21.3 | `1.21.2` | yes | yes | yes |
| 1.21.4-1.21.5 | `1.21.4` | yes | yes | yes |
| 1.21.6-1.21.8 | `1.21.6` | yes | yes | yes |
| 1.21.9-1.21.10 | `1.21.9` | yes | yes | yes |
| 1.21.11 | `1.21.11` | yes | yes | yes |
| 26.1-26.1.2 | `26.1` | yes | yes | yes |
| 26.2 | `26.2` | yes | yes | yes |
| 26.3 | `26.3` | yes | yes | yes |

## Building

Each build lays out one Gradle project per range and loader under `build/targets/<range>/<loader>` and runs that project's own wrapper, so every target gets the Gradle it needs. Set `JAVA17_HOME`, `JAVA21_HOME` and `JAVA25_HOME` (or `-Pjava21Home=...`) to point at the JDKs. Fabric needs Java 25 to run Gradle for every range.

```sh
./gradlew :1.21.4:fabric:build
./gradlew :1.21.4:build
./gradlew build -PonlyVersions=1.21.4,1.21.6 -PonlyLoaders=fabric
./gradlew build
```

Jars are collected in `build/dist`. Set the version in `gradle.properties`. On Windows the layered sources are copied instead of linked, so edit the files under `core`, `loaders` and `versions`.

## Checks

```sh
python3 scripts/check.py
```

Merges the layers of every target like the build does and checks the metadata, mixin config, entry points, Java versions and changelog. It does not compile anything. The unit test in `loaders/fabric/src/test` runs with the Fabric builds.

## Layout

```
core/                       code and resources every range and loader shares
loaders/<loader>/           files every range of that loader shares
versions/<range>/common/    what changed in this range, for every loader
versions/<range>/<loader>/  what changed in this range, for one loader
versions/<range>/version.properties   loaders in the range and the JDK that runs each build
versions.txt                the ranges, oldest first
publish.gradle              Modrinth and CurseForge setup shared by every target
```

A target is the layers stacked in that order: `core`, `loaders/<loader>`, then every range from the oldest up to its own. A later file replaces an earlier one with the same path, and an empty `name.removed` file deletes `name`.

To add a Minecraft range, create `versions/<range>`, list it in `versions.txt`, add `version.properties` and only the files that differ (usually each loader's `gradle.properties` and metadata). Put code the compiler rejects in `versions/<range>/common` or the loader folder.

## Publishing

Set `modrinthToken` and `curseforgeToken` in `~/.gradle/gradle.properties` (or `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN`), set the two project ids at the top of `publish.gradle`, and add a `## <version>` entry to `CHANGELOG.md`.

```sh
./gradlew publishMods -PdryRun=true
./gradlew publishModrinth
./gradlew publishCurseforge
```

Without a token the tasks only do a dry run, and a platform whose id is still `TEMPLATE_*` is skipped. Every upload is marked client only and lists Proteus as required.

## License

[GPL-3.0](LICENSE)
