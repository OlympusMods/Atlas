package com.github.olympus.atlas.client;

import com.github.olympus.atlas.core.map.ChunkData;
import com.github.olympus.atlas.core.map.MapStore;
import com.github.olympus.atlas.core.map.MapWorld;
import com.github.olympus.atlas.core.map.Region;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class World {
    public static final int PLAYER = 0;
    public static final int HOSTILE = 1;
    public static final int PASSIVE = 2;
    public static final int ITEM = 3;
    public static final int OTHER = 4;

    private static final int MAX_RUN = 24;
    private static final int MAX_GAP = 64;
    private static final int BODY_DEPTH = 4;

    private static final int COLORED = 1 << 16;
    private static final int WATER = 1 << 17;
    private static final int SOLID = 1 << 18;
    private static final int FLAT = 1 << 19;
    private static final int THIN = 1 << 20;
    private static final ConcurrentHashMap<BlockState, Integer> STATES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Biome, Integer> BIOMES = new ConcurrentHashMap<>();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    public record Blip(Entity entity, int kind) {
    }

    private World() {
    }

    public static String keyName(ResourceKey<?> key) {
        String text = key.toString();
        int split = text.lastIndexOf(" / ");
        return split < 0 ? text : text.substring(split + 3, text.length() - 1);
    }

    public static String session(Minecraft minecraft) {
        if (minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null) {
            Path root = minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            return "sp_" + MapWorld.clean(root.getFileName().toString());
        }
        ServerData server = minecraft.getCurrentServer();
        if (server != null && server.ip != null) {
            return "mp_" + MapWorld.clean(server.ip);
        }
        return "mp_unknown";
    }

    public static boolean remote(Minecraft minecraft) {
        return !minecraft.hasSingleplayerServer();
    }

    public static void blocks(MapStore.Shared shared) {
        for (Block block : BuiltInRegistries.BLOCK) {
            info(block.defaultBlockState(), shared);
        }
    }

    public static List<String> blockNames() {
        List<String> names = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            names.add(BuiltInRegistries.BLOCK.getKey(block).toString());
        }
        return names;
    }

    public static void biomes(ClientLevel level, MapStore.Shared shared) {
        Heights.biomes(level, (name, biome) -> {
            int id = shared.biomes.id(name);
            shared.biomeColors.define(id, biome.getGrassColor(0.0, 0.0), biome.getFoliageColor(),
                    biome.getWaterColor());
            BIOMES.put(biome, id);
        });
    }

    public static void forget() {
        BIOMES.clear();
    }

    private static int info(BlockState state, MapStore.Shared shared) {
        Integer known = STATES.get(state);
        if (known != null) {
            return known;
        }
        Block block = state.getBlock();
        int id = shared.blocks.id(BuiltInRegistries.BLOCK.getKey(block).toString());
        int flags = 0;
        int color = 0;
        boolean water = false;
        try {
            MapColor map = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (map != MapColor.NONE) {
                flags |= COLORED;
                color = map.col;
            }
            water = block == Blocks.WATER || (map == MapColor.WATER && !state.getFluidState().isEmpty());
            if (map == MapColor.PLANT && !state.canOcclude()
                    && state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
                flags |= THIN;
            }
        } catch (RuntimeException exception) {
            if (!state.isAir()) {
                flags |= COLORED;
                color = 0x8C8C8C;
            }
        }
        if (water) {
            flags |= WATER | COLORED;
        }
        if (state.canOcclude()) {
            flags |= SOLID;
        }
        try {
            if (!water && state.getFluidState().isEmpty()
                    && state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
                flags |= FLAT;
            }
        } catch (RuntimeException exception) {
            flags &= ~FLAT;
        }
        if (state == block.defaultBlockState() && (flags & COLORED) != 0) {
            shared.palette.define(id, color, water);
        }
        int packed = id | flags;
        STATES.put(state, packed);
        return packed;
    }

    public static LevelChunk chunk(ClientLevel level, int cx, int cz) {
        return level.getChunkSource().getChunk(cx, cz, false);
    }

    public static ChunkData scan(LevelChunk chunk, int cx, int cz, int minY, int maxY, int slice,
            MapStore.Shared shared) {
        ChunkData data = new ChunkData(cx, cz);
        LevelChunkSection[] sections = chunk.getSections();
        boolean surface = slice == MapWorld.SURFACE;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int i = (z << 4) | x;
                int y = Math.min(chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), maxY - 1);
                boolean cut = false;
                if (!surface && y > slice) {
                    y = slice;
                    cut = (info(state(sections, x, y, z, minY), shared) & SOLID) != 0;
                }
                int packed = 0;
                while (y >= minY) {
                    int index = (y - minY) >> 4;
                    if (index >= sections.length) {
                        y = minY + (sections.length << 4) - 1;
                        continue;
                    }
                    LevelChunkSection section = sections[index];
                    if (section == null || section.hasOnlyAir()) {
                        y = minY + (index << 4) - 1;
                        continue;
                    }
                    packed = info(section.getBlockState(x, y & 15, z), shared);
                    if ((packed & COLORED) != 0) {
                        break;
                    }
                    y--;
                }
                if (y < minY || (packed & COLORED) == 0) {
                    continue;
                }
                if ((packed & THIN) != 0) {
                    int floor = y - 1;
                    while (floor >= minY && floor > y - 4) {
                        int under = info(state(sections, x, floor, z, minY), shared);
                        if ((under & COLORED) != 0 && (under & THIN) == 0) {
                            y = floor;
                            break;
                        }
                        floor--;
                    }
                } else if ((packed & FLAT) != 0 && y > minY && !cut
                        && (info(state(sections, x, y - 1, z, minY), shared) & (COLORED | FLAT)) == COLORED) {
                    y--;
                }
                data.height[i] = (short) y;
                data.block[i] = (short) packed;
                data.light[i] = (byte) (cut ? Region.CUT : 0);
                if ((packed & WATER) != 0) {
                    int floor = y - 1;
                    int under = 0;
                    while (floor >= minY) {
                        under = info(state(sections, x, floor, z, minY), shared);
                        if ((under & WATER) == 0 && (under & COLORED) != 0) {
                            break;
                        }
                        floor--;
                    }
                    data.depth[i] = (byte) Math.min(255, y - floor);
                    data.under[i] = floor >= minY ? (short) under : 0;
                } else if (surface) {
                    overhang(data, i, sections, x, y, z, minY, shared);
                }
                data.biome[i] = (short) biome(chunk, (cx << 4) + x, y, (cz << 4) + z, shared);
            }
        }
        return data;
    }

    private static void overhang(ChunkData data, int i, LevelChunkSection[] sections, int x, int top, int z,
            int minY, MapStore.Shared shared) {
        int run = 1;
        int y = top - 1;
        while (run < MAX_RUN && y >= minY) {
            BlockState state = state(sections, x, y, z, minY);
            if (state.isAir()) {
                break;
            }
            int inner = info(state, shared);
            if ((inner & FLAT) != 0) {
                break;
            }
            if (run <= BODY_DEPTH && (inner & (COLORED | WATER)) == COLORED) {
                data.body[i] = (short) inner;
            }
            run++;
            y--;
        }
        if (run >= MAX_RUN || y < minY) {
            return;
        }
        int open = 0;
        int packed = 0;
        while (open < MAX_GAP && y >= minY) {
            BlockState state = state(sections, x, y, z, minY);
            if (!state.isAir()) {
                packed = info(state, shared);
                if ((packed & (COLORED | FLAT)) == COLORED) {
                    break;
                }
            }
            open++;
            y--;
        }
        if (open == 0 || open >= MAX_GAP || y < minY) {
            return;
        }
        data.thick[i] = (byte) run;
        data.gap[i] = (byte) open;
        data.ground[i] = (short) packed;
    }

    private static BlockState state(LevelChunkSection[] sections, int x, int y, int z, int minY) {
        int index = (y - minY) >> 4;
        if (index < 0 || index >= sections.length) {
            return AIR;
        }
        LevelChunkSection section = sections[index];
        return section == null || section.hasOnlyAir() ? AIR : section.getBlockState(x, y & 15, z);
    }

    private static int biome(LevelChunk chunk, int x, int y, int z, MapStore.Shared shared) {
        Holder<Biome> holder = chunk.getNoiseBiome(x >> 2, y >> 2, z >> 2);
        Biome biome = holder.value();
        Integer known = BIOMES.get(biome);
        if (known != null) {
            return known;
        }
        int id = holder.unwrapKey().map(key -> shared.biomes.id(keyName(key))).orElse(0);
        if (id != 0 && !shared.biomeColors.known(id)) {
            shared.biomeColors.define(id, biome.getGrassColor(0.0, 0.0), biome.getFoliageColor(),
                    biome.getWaterColor());
        }
        BIOMES.put(biome, id);
        return id;
    }

    public static String biomeAt(ClientLevel level, BlockPos pos) {
        return level.getBiome(pos).unwrapKey().map(World::keyName).orElse("");
    }

    public static int blockLight(ClientLevel level, BlockPos pos) {
        return level.getLightEngine().getLayerListener(LightLayer.BLOCK).getLightValue(pos);
    }

    public static int skyLight(ClientLevel level, BlockPos pos) {
        return level.getLightEngine().getLayerListener(LightLayer.SKY).getLightValue(pos);
    }

    public static boolean ceiling(ClientLevel level) {
        return level.dimensionType().hasCeiling();
    }

    public static void blips(Minecraft minecraft, boolean players, boolean hostile, boolean passive,
            boolean hideSneaking, boolean hideInvisible, boolean everything, double range, List<Blip> out) {
        out.clear();
        LocalPlayer self = minecraft.player;
        if (self == null || minecraft.level == null || !(players || hostile || passive || everything)) {
            return;
        }
        double limit = range * range;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity == self || !entity.isAlive()) {
                continue;
            }
            boolean living = entity instanceof LivingEntity && !(entity instanceof ArmorStand);
            if (!living && !everything) {
                continue;
            }
            double dx = entity.getX() - self.getX();
            double dz = entity.getZ() - self.getZ();
            if (dx * dx + dz * dz > limit) {
                continue;
            }
            if (hideInvisible && entity.isInvisible()) {
                continue;
            }
            if (!living) {
                out.add(new Blip(entity, entity instanceof ItemEntity ? ITEM : OTHER));
            } else if (entity instanceof Player) {
                if ((players || everything) && !(hideSneaking && entity.isShiftKeyDown())) {
                    out.add(new Blip(entity, PLAYER));
                }
            } else if (entity instanceof Enemy) {
                if (hostile || everything) {
                    out.add(new Blip(entity, HOSTILE));
                }
            } else if (passive || everything) {
                out.add(new Blip(entity, PASSIVE));
            }
        }
    }

    public static double x(Entity entity, float partial) {
        return entity.xo + (entity.getX() - entity.xo) * partial;
    }

    public static double y(Entity entity, float partial) {
        return entity.yo + (entity.getY() - entity.yo) * partial;
    }

    public static double z(Entity entity, float partial) {
        return entity.zo + (entity.getZ() - entity.zo) * partial;
    }

    public static float yaw(Entity entity, float partial) {
        return entity.getViewYRot(partial);
    }

    public static String name(Entity entity) {
        return entity.getName().getString();
    }

    public static int renderDistance(Minecraft minecraft) {
        return minecraft.options.getEffectiveRenderDistance();
    }

    public static void command(LocalPlayer player, String command) {
        player.connection.sendCommand(command);
    }

    public static boolean blocked(ClientLevel level, LocalPlayer player, double ax, double ay, double az, double bx,
            double by, double bz) {
        return level.clip(new ClipContext(new Vec3(ax, ay, az), new Vec3(bx, by, bz), ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS;
    }

    public static boolean identifying(Minecraft minecraft) {
        return minecraft.options.keyPlayerList.isDown();
    }

    public static boolean dead(LocalPlayer player) {
        return player.isDeadOrDying();
    }
}
