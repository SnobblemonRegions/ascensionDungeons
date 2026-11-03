package com.ascensiondungeons.mc;

import com.ascensiondungeons.gen.cinderforge.CinderforgeLayout;
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
 * Renders the Cinderforge Bastion into a run level. Server-only vanilla chunk data: no client registry, renderer or
 * custom packet is required. Outside the schematic the level is void; the dungeon is sealed, so nobody sees that.
 */
public final class CinderforgeChunkGenerator extends ChunkGenerator {
    public static final MapCodec<CinderforgeChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.CODEC.fieldOf("biome").forGetter(g -> g.biome),
            Codec.LONG.fieldOf("seed").forGetter(g -> g.seed)
    ).apply(i, CinderforgeChunkGenerator::new));

    /** Light level of the invisible light blocks seeded above the floors (0 disables them). */
    public static final int LIGHT_LEVEL = 10;
    public static final ResourceLocation CHEST_TABLE = ResourceLocation.fromNamespaceAndPath("ascensiondungeons", "chests/ashen_caldera");

    private static final Map<String, BlockState> STATES = new ConcurrentHashMap<>();
    private final Holder<Biome> biome;
    private final long seed;
    private final CinderforgeLayout layout = CinderforgeLayout.get();
    private final BlockState[] palette;

    public CinderforgeChunkGenerator(Holder<Biome> biome, long seed) {
        super(new FixedBiomeSource(biome));
        this.biome = biome;
        this.seed = seed;
        String[] raw = layout.palette();
        palette = new BlockState[raw.length];
        for (int i = 0; i < raw.length; i++) {
            BlockState s = state(raw[i]);
            // Chests are placed in decoration (they need a block entity, Lootr and the key).
            palette[i] = s.is(Blocks.CHEST) ? Blocks.AIR.defaultBlockState() : s;
        }
    }

    public CinderforgeLayout layout() { return layout; }

    public static BlockPos toWorld(int[] c) {
        return new BlockPos(CinderforgeLayout.ORIGIN_X + c[0], CinderforgeLayout.ORIGIN_Y + c[1], CinderforgeLayout.ORIGIN_Z + c[2]);
    }
    public static int[] toSchematic(BlockPos p) {
        return new int[] {p.getX() - CinderforgeLayout.ORIGIN_X, p.getY() - CinderforgeLayout.ORIGIN_Y, p.getZ() - CinderforgeLayout.ORIGIN_Z};
    }
    public BlockPos worldEntry() { return toWorld(layout.data().entry); }
    public double centreX() { return CinderforgeLayout.ORIGIN_X + layout.width() / 2.0; }
    public double centreZ() { return CinderforgeLayout.ORIGIN_Z + layout.length() / 2.0; }
    public double borderSize() { return Math.max(layout.width(), layout.length()) + 48; }

    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }
    @Override public int getGenDepth() { return 384; }
    @Override public int getMinY() { return -64; }
    @Override public int getSeaLevel() { return CinderforgeLayout.ORIGIN_Y; }
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
            int sx = x - CinderforgeLayout.ORIGIN_X, sz = z - CinderforgeLayout.ORIGIN_Z;
            if (sx < 0 || sz < 0 || sx >= layout.width() || sz >= layout.length()) continue;
            for (int sy = 0; sy < layout.height(); sy++) {
                BlockState s = block(sx, sy, sz);
                if (!s.isAir()) chunk.setBlockState(pos.set(x, sy + CinderforgeLayout.ORIGIN_Y, z), s, false);
            }
        }
        Heightmap.primeHeightmaps(chunk, java.util.EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    /** The block at a schematic cell, including the invisible lights and the initial Overseer gate seal. */
    public BlockState block(int sx, int sy, int sz) {
        BlockState s = palette[layout.paletteIndex(sx, sy, sz)];
        if (layout.isGate(sx, sy, sz)) return Blocks.BLACKSTONE.defaultBlockState();
        if (s.isAir() && LIGHT_LEVEL > 0 && layout.isLight(sx, sy, sz))
            return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LIGHT_LEVEL);
        return s;
    }

    @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return CinderforgeLayout.ORIGIN_Y + layout.height();
    }
    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int min = level.getMinBuildHeight(), top = CinderforgeLayout.ORIGIN_Y + layout.height();
        BlockState[] out = new BlockState[Math.max(0, top - min)];
        int sx = x - CinderforgeLayout.ORIGIN_X, sz = z - CinderforgeLayout.ORIGIN_Z;
        for (int i = 0; i < out.length; i++) {
            int sy = i + min - CinderforgeLayout.ORIGIN_Y;
            out[i] = sx >= 0 && sz >= 0 && sx < layout.width() && sz < layout.length() && sy >= 0 && sy < layout.height()
                    ? block(sx, sy, sz) : Blocks.AIR.defaultBlockState();
        }
        return new NoiseColumn(min, out);
    }

    @Override public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        var lootr = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("lootr", "lootr_chest"));
        for (CinderforgeLayout.Chest c : layout.data().chests) {
            BlockPos pos = toWorld(c.pos);
            if (pos.getX() < x0 || pos.getX() >= x0 + 16 || pos.getZ() < z0 || pos.getZ() >= z0 + 16) continue;
            BlockState authored = state(c.state);
            if (c.kind.equals("key")) {
                // The Reliquary chest is a plain chest holding exactly the key; Lootr would hand every player a copy.
                level.setBlock(pos, authored, 2);
                if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) chest.setItem(13, CinderforgeItems.forgeKey());
                continue;
            }
            BlockState placed = lootr.map(b -> b.defaultBlockState()).orElse(Blocks.CHEST.defaultBlockState());
            if (placed.hasProperty(ChestBlock.FACING) && authored.hasProperty(ChestBlock.FACING))
                placed = placed.setValue(ChestBlock.FACING, authored.getValue(ChestBlock.FACING));
            level.setBlock(pos, placed, 2);
            RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), pos, ResourceKey.create(Registries.LOOT_TABLE, CHEST_TABLE));
        }
    }

    private static BlockState state(String name) {
        return STATES.computeIfAbsent(name, n -> {
            try { return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), n, false).blockState(); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new IllegalStateException("Invalid Cinderforge block " + n, e); }
        });
    }
}
