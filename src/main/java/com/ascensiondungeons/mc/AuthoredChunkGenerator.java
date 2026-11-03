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
    }

    public AuthoredLayout layout() { return layout; }

    public BlockPos toWorld(int[] c) { return new BlockPos(ox + c[0], oy + c[1], oz + c[2]); }
    public int[] toSchematic(BlockPos p) { return new int[] {p.getX() - ox, p.getY() - oy, p.getZ() - oz}; }
    public BlockPos worldEntry() { return toWorld(layout.data().entry); }
    public double centreX() { return ox + layout.width() / 2.0; }
    public double centreZ() { return oz + layout.length() / 2.0; }
    public double borderSize() { return Math.max(layout.width(), layout.length()) + 48; }

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
            if (sx < 0 || sz < 0 || sx >= layout.width() || sz >= layout.length()) continue;
            for (int sy = 0; sy < layout.height(); sy++) {
                BlockState s = block(sx, sy, sz);
                if (!s.isAir()) chunk.setBlockState(pos.set(x, sy + oy, z), s, false);
            }
        }
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
            out[i] = sx >= 0 && sz >= 0 && sx < layout.width() && sz < layout.length() && sy >= 0 && sy < layout.height()
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
    }

    public static BlockState state(String name) {
        return STATES.computeIfAbsent(name, n -> {
            try { return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), n, false).blockState(); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new IllegalStateException("Invalid authored block " + n, e); }
        });
    }
}
