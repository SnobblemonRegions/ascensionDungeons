package com.ascensiondungeons.mc;

import com.ascensiondungeons.gen.authored.AuthoredLayout;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/**
 * Renders one authored dungeon (data/ascensiondungeons/dungeons/&lt;id&gt;) into a run level. Server-only vanilla chunk data:
 * outside the schematic the level is void and the dungeon is sealed, so nobody sees that.
 */
public final class AuthoredChunkGenerator extends ChunkGenerator {
    public static final MapCodec<AuthoredChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.CODEC.fieldOf("biome").forGetter(g -> g.biome),
            Codec.STRING.fieldOf("dungeon").forGetter(g -> g.layout.id()),
            Codec.LONG.fieldOf("seed").forGetter(g -> g.seed)
    ).apply(i, AuthoredChunkGenerator::new));

    private static final Map<String, BlockState> STATES = new ConcurrentHashMap<>();
    private final Holder<Biome> biome;
    private final long seed;
    private final AuthoredLayout layout;
    private final BlockState[] palette;
    private final int ox, oy, oz;
    /** This run's procedural Depths (null when the dungeon has none), where its corner is in the world, and its blocks by role. */
    private final com.ascensiondungeons.gen.depths.Depths depths;
    /** How the Depths join the dungeon: where the maze sits, and the tunnel and stairway blocks. */
    private final com.ascensiondungeons.gen.depths.DepthsLink link;
    private final BlockState[] depthBlocks;
    /** The tunnel and stairway blocks, by chunk, as {world x, world y, world z, role}. */
    private final java.util.Map<Long, java.util.List<int[]>> linkBlocks = new java.util.HashMap<>();
    private final int minX, minZ, maxX, maxZ;

    public AuthoredChunkGenerator(Holder<Biome> biome, String dungeon, long seed) {
        super(new FixedBiomeSource(biome));
        this.biome = biome;
        this.seed = seed;
        this.layout = AuthoredLayout.get(dungeon);
        this.ox = layout.data().origin[0];
        this.oy = layout.data().origin[1];
        this.oz = layout.data().origin[2];
        String[] raw = layout.palette();
        palette = new BlockState[raw.length];
        for (int i = 0; i < raw.length; i++) {
            BlockState s = state(raw[i]);
            // Chests are placed in decoration (they need a block entity, Lootr and the key).
            palette[i] = s.is(Blocks.CHEST) ? Blocks.AIR.defaultBlockState() : s;
        }
        // Without Lootr there is no Outbreak dive, so no Depths either (see OutbreakDive.lootrInstalled).
        this.depths = OutbreakDive.lootrInstalled() ? layout.depths(seed) : null;
        this.link = depths == null ? null : layout.link();
        int x1 = 0, z1 = 0, x2 = layout.width() - 1, z2 = layout.length() - 1;
        if (link != null) {
            int[] reach = link.reach();
            x1 = Math.min(x1, reach[0]); z1 = Math.min(z1, reach[1]); x2 = Math.max(x2, reach[2]); z2 = Math.max(z2, reach[3]);
            for (var e : link.overrides().entrySet()) {
                int[] c = com.ascensiondungeons.gen.depths.DepthsLink.unkey(e.getKey());
                int wx = ox + c[0], wz = oz + c[2];
                linkBlocks.computeIfAbsent(ChunkPos.asLong(wx >> 4, wz >> 4), k -> new java.util.ArrayList<>()).add(new int[] {wx, oy + c[1], wz, e.getValue()});
            }
        }
        this.minX = ox + x1; this.minZ = oz + z1; this.maxX = ox + x2; this.maxZ = oz + z2;
        this.depthBlocks = new BlockState[7];
        if (depths != null) {
            String[] p = layout.depthsPalette();
            depthBlocks[com.ascensiondungeons.gen.depths.Depths.WALL] = state(p[0]);
            depthBlocks[com.ascensiondungeons.gen.depths.Depths.FLOOR] = state(p[1]);
            depthBlocks[com.ascensiondungeons.gen.depths.Depths.ACCENT] = state(p[2]);
            depthBlocks[com.ascensiondungeons.gen.depths.Depths.PILLAR] = state(p[3]);
            depthBlocks[com.ascensiondungeons.gen.depths.Depths.AIR] = Blocks.AIR.defaultBlockState();
            depthBlocks[com.ascensiondungeons.gen.depths.Depths.LIGHT] = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 13);
            // The stairway's half steps: the first of the dungeon's slabs that this game has; with none, the cell stays open.
            BlockState step = Blocks.AIR.defaultBlockState();
            for (String name : layout.depthsSteps()) {
                ResourceLocation id = ResourceLocation.tryParse(name);
                if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) continue;
                net.minecraft.world.level.block.Block block = BuiltInRegistries.BLOCK.get(id);
                if (block instanceof net.minecraft.world.level.block.SlabBlock) { step = block.defaultBlockState(); break; }
            }
            depthBlocks[com.ascensiondungeons.gen.depths.DepthsLink.STEP] = step;
        }
    }

    /** The block of the Depths at a world position, or null outside them. */
    private BlockState depthsBlock(int x, int y, int z) {
        if (depths == null) return null;
        int[] l = link.toLocal(x - ox, y - oy, z - oz);
        if (l[0] < 0 || l[2] < 0 || l[1] < 0 || l[0] >= depths.sizeX() || l[2] >= depths.sizeZ() || l[1] >= com.ascensiondungeons.gen.depths.Depths.HEIGHT) return null;
        return depthBlocks[depths.role(l[0], l[1], l[2])];
    }

    /** The tunnel or stairway block at a world position, or null where the link changes nothing. */
    private BlockState linkBlock(int x, int y, int z) {
        if (link == null) return null;
        Byte role = link.override(x - ox, y - oy, z - oz);
        return role == null ? null : depthBlocks[role];
    }

    /** The lowest and highest world y the Depths occupy. */
    private int depthsMinY() { return oy + link.toSchem(0, 0, 0)[1]; }

    public AuthoredLayout layout() { return layout; }

    public BlockPos toWorld(int[] c) { return new BlockPos(ox + c[0], oy + c[1], oz + c[2]); }
    public int[] toSchematic(BlockPos p) { return new int[] {p.getX() - ox, p.getY() - oy, p.getZ() - oz}; }
    public BlockPos worldEntry() { return toWorld(layout.data().entry); }
    /** The level's border is centred on, and sized to hold, the authored dungeon and the Depths together. */
    public double centreX() { return (minX + maxX + 1) / 2.0; }
    public double centreZ() { return (minZ + maxZ + 1) / 2.0; }
    public double borderSize() { return Math.max(maxX - minX + 1, maxZ - minZ + 1) + 48; }

    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }
    @Override public int getGenDepth() { return 384; }
    @Override public int getMinY() { return -64; }
    @Override public int getSeaLevel() { return oy; }
    @Override public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structures, RandomState random, long seed) {
        return ChunkGeneratorStructureState.createForFlat(random, seed, biomeSource, java.util.stream.Stream.empty());
    }
    @Override public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes, StructureManager structures,
                                        ChunkAccess chunk, GenerationStep.Carving step) {}
    @Override public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {}
    @Override public void spawnOriginalMobs(WorldGenRegion region) {}
    @Override public void addDebugScreenInfo(java.util.List<String> info, RandomState random, BlockPos pos) {}

    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        for (int x = x0; x < x0 + 16; x++) for (int z = z0; z < z0 + 16; z++) {
            int sx = x - ox, sz = z - oz;
            if (sx >= 0 && sz >= 0 && sx < layout.width() && sz < layout.length()) {
                for (int sy = 0; sy < layout.height(); sy++) {
                    BlockState s = block(sx, sy, sz);
                    if (!s.isAir()) chunk.setBlockState(pos.set(x, sy + oy, z), s, false);
                }
            } else if (depths != null) {
                int y0 = depthsMinY();
                for (int ly = 0; ly < com.ascensiondungeons.gen.depths.Depths.HEIGHT; ly++) {
                    BlockState s = depthsBlock(x, y0 + ly, z);
                    if (s != null && !s.isAir()) chunk.setBlockState(pos.set(x, y0 + ly, z), s, false);
                }
            }
        }
        // The tunnel out of the entrance room and the stairway down go in last: they cut through the dungeon's own wall.
        java.util.List<int[]> cut = linkBlocks.get(chunk.getPos().toLong());
        if (cut != null) for (int[] b : cut) chunk.setBlockState(pos.set(b[0], b[1], b[2]), depthBlocks[b[3]], false);
        Heightmap.primeHeightmaps(chunk, java.util.EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    /** The block at a schematic cell, including the invisible lights and any seal that stands until a mechanic opens it. */
    public BlockState block(int sx, int sy, int sz) {
        String seal = layout.sealAt(sx, sy, sz);
        if (seal != null) return state(seal);
        BlockState s = palette[layout.paletteIndex(sx, sy, sz)];
        int level = layout.data().lightLevel;
        if (level > 0 && layout.isLight(sx, sy, sz)) {
            if (s.isAir()) return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level);
            if (s.is(Blocks.WATER)) return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level).setValue(LightBlock.WATERLOGGED, true);
        }
        return s;
    }

    @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return oy + layout.height();
    }
    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int min = level.getMinBuildHeight(), top = oy + layout.height();
        BlockState[] out = new BlockState[Math.max(0, top - min)];
        int sx = x - ox, sz = z - oz;
        for (int i = 0; i < out.length; i++) {
            int sy = i + min - oy;
            BlockState deep = linkBlock(x, i + min, z);
            if (deep == null) deep = depthsBlock(x, i + min, z);
            out[i] = deep != null ? deep : sx >= 0 && sz >= 0 && sx < layout.width() && sz < layout.length() && sy >= 0 && sy < layout.height()
                    ? block(sx, sy, sz) : Blocks.AIR.defaultBlockState();
        }
        return new NoiseColumn(min, out);
    }

    @Override public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        var lootr = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("lootr", "lootr_chest"));
        for (AuthoredLayout.Chest c : layout.data().chests) {
            BlockPos pos = toWorld(c.pos);
            if (pos.getX() < x0 || pos.getX() >= x0 + 16 || pos.getZ() < z0 || pos.getZ() >= z0 + 16) continue;
            BlockState authored = state(c.state);
            if ("key".equals(c.kind)) {
                // A plain chest holding exactly the key; Lootr would hand every player a copy.
                level.setBlock(pos, authored, 2);
                if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest)
                    chest.setItem(13, AuthoredItems.key(c.key, layout.data().keys.has(c.key) ? layout.data().keys.getAsJsonObject(c.key) : null));
                continue;
            }
            BlockState placed = lootr.map(b -> b.defaultBlockState()).orElse(Blocks.CHEST.defaultBlockState());
            if (placed.hasProperty(ChestBlock.FACING) && authored.hasProperty(ChestBlock.FACING))
                placed = placed.setValue(ChestBlock.FACING, authored.getValue(ChestBlock.FACING));
            level.setBlock(pos, placed, 2);
            String table = c.table != null ? c.table : layout.data().lootTable;
            RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), pos, ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(table)));
        }
        // Outbreak features for this run's seed: the blocks go in here, the dive runs them (OutbreakDive).
        for (var f : OutbreakDive.lootrInstalled() ? layout.features(seed) : java.util.List.<com.ascensiondungeons.outbreak.FeaturePlanner.Placement>of()) {
            BlockPos pos = toWorld(f.cell());
            if (pos.getX() < x0 || pos.getX() >= x0 + 16 || pos.getZ() < z0 || pos.getZ() >= z0 + 16) continue;
            switch (f.kind()) {
                case PORTAL -> level.setBlock(pos.below(), state("minecraft:lodestone"), 2);
                case DESCENT, RETURN -> level.setBlock(pos.below(), state("minecraft:chiseled_deepslate"), 2);
                case ALTAR -> level.setBlock(pos, state("minecraft:crying_obsidian"), 2);
                // A nest's heart: a block no tool breaks, on a ring of the dungeon's accent so it reads as a place.
                case NEST -> level.setBlock(pos, state("minecraft:reinforced_deepslate"), 2);
                default -> {
                    // The floor under a chest shows its rarity even without a resource pack.
                    String floor = switch (f.kind()) {
                        case STRONGBOX -> "minecraft:netherite_block";
                        case TIMER_CHEST -> "minecraft:gold_block";
                        case NEST_CHEST -> "minecraft:copper_block";
                        case HOARD -> "minecraft:netherite_block";
                        default -> f.rarity().floorBlock();
                    };
                    level.setBlock(pos.below(), state(floor), 2);
                    level.setBlock(pos, lootr.map(b -> b.defaultBlockState()).orElse(Blocks.CHEST.defaultBlockState()), 2);
                    RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), pos,
                            ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(f.rarity().lootTable())));
                }
            }
        }
    }

    public static BlockState state(String name) {
        return STATES.computeIfAbsent(name, n -> {
            try { return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), n, false).blockState(); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new IllegalStateException("Invalid authored block " + n, e); }
        });
    }
}
