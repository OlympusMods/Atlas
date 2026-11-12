# Changelog

## 1.0.0

- Minimap on the HUD: round or square, rotating or north up, six zoom levels, any corner, size, opacity, coordinates, biome, light level and compass letters
- Radar with separate switches for players, hostile mobs and passive mobs, and names for every entity and dropped item while the player list key is held
- Fullscreen map with drag and scroll, dimension switching, a cave layer slider, a chunk grid and a searchable waypoint list
- The 2D fullscreen map is kept in textures on the graphics card and drawn at the full resolution of the display
- 3D terrain view of the explored map with tree canopies and overhangs, orbit, pan and zoom, one key to switch between 2D and 3D, and an optional tilted 3D minimap
- Map colors from averaged block textures or vanilla map colors, with height shading, water depth and biome tint
- Explored regions saved per world and per server in a compact format, scanned, drawn and saved on background threads with a memory limit
- Waypoints with name, color, icon and group, shown in the world as an icon that stays on its block, with a beacon beam and a name and distance when you look at it
- Death waypoints with a limit and expiry, temporary waypoints, and a key to add a waypoint where you stand
- Waypoint import and export as JSON, and import from Xaero's Minimap, JourneyMap and VoxelMap
- Optional click to teleport for players who are allowed to run the command
- Worlds behind one server address, like a lobby and a survival world on a proxy, each have their own map and waypoints. They are told apart by the seed hash the server sends, where you left and arrive, and the biomes and terrain around you, and can be picked by hand in the settings
- Follows the Xaero's Minimap and VoxelMap server codes that turn off cave mode, the radar or the minimap
- Settings in a card of the Proteus menu and in config/atlas.json
