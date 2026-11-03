package com.ascensiondungeons.mc;

import com.ascensiondungeons.gen.BiomeKind;
import com.ascensiondungeons.gen.DistrictGrid;
import com.ascensiondungeons.gen.Orientation;
import com.ascensiondungeons.gen.Side;
import com.ascensiondungeons.gen.SpongeSchematic;
import com.ascensiondungeons.gen.Template;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/**
 * Builds a run's world from district templates. The world is a square grid of 176 x 176 districts; each block is a pure
 * function of (seed, x, y, z) read from the template placed in that cell, so chunks can be generated in any order and
 * the same seed always gives the same dungeon.
 *
 * <p>The template files leave the air outside the ring wall empty. Here that air becomes rock, except for the short
 * corridors that join two districts, and any gate with no neighbour is plugged. The loot chest placeholder is replaced
 * by a Lootr chest (or a vanilla one when Lootr is absent) when the chunk is decorated.
 */
public final class DungeonChunkGenerator extends ChunkGenerator {
    public static final MapCodec<DungeonChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.CODEC.fieldOf("biome").forGetter(g -> g.biome),
            Codec.LONG.fieldOf("seed").forGetter(g -> g.grid.seed()),
            Codec.INT.fieldOf("district_radius").forGetter(g -> g.grid.radius())
    ).apply(i, DungeonChunkGenerator::new));

    private static final ResourceLocation LOOTR_CHEST = ResourceLocation.fromNamespaceAndPath("lootr", "lootr_chest");
    private static final int MIN_Y = -64;

    private final Holder<Biome> biome;
    private final DistrictGrid grid;
    private final Map<BiomeKind, Map<Orientation, BlockState[]>> palettes = new EnumMap<>(BiomeKind.class);

    public DungeonChunkGenerator(Holder<Biome> biome, long seed, int districtRadius) {
        super(new FixedBiomeSource(biome));
        this.biome = biome;
        this.grid = new DistrictGrid(seed, districtRadius, DungeonTemplates.available());
    }

    public DistrictGrid grid() { return grid; }

    /** Where the party arrives, as world {x, y, z}. */
    public int[] entry() { return grid.entry(); }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structures, RandomState random, long seed) {
        return ChunkGeneratorStructureState.createForFlat(random, seed, this.biomeSource, java.util.stream.Stream.empty());
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes,
                             StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {}

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {}

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {}

    @Override
    public int getGenDepth() { return 384; }

    @Override
    public int getSeaLevel() { return Template.FLOOR_Y; }

    @Override
    public int getMinY() { return MIN_Y; }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structures,
                                                        ChunkAccess chunk) {
        int minY = chunk.getMinBuildHeight();
        int top = topY(chunk);
        BlockState[] col = new BlockState[top - minY + 1];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = baseX + dx, z = baseZ + dz;
                column(x, z, minY, top, col);
                for (int i = 0; i < col.length; i++) {
                    if (col[i] != null) chunk.setBlockState(pos.set(x, minY + i, z), col[i], false);
                }
            }
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    /** Highest y with any generated block. Templates all share a height; above it the world is empty. */
    private int topY(LevelHeightAccessor level) {
        int h = DungeonTemplates.get(DungeonTemplates.available().get(0)).height();
        return Math.min(h - 1, level.getMaxBuildHeight() - 1);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        int minY = level.getMinBuildHeight();
        int top = topY(level);
        BlockState[] col = new BlockState[top - minY + 1];
        column(x, z, minY, top, col);
        Predicate<BlockState> blocks = type.isOpaque();
        for (int i = col.length - 1; i >= 0; i--) if (col[i] != null && blocks.test(col[i])) return minY + i + 1;
        return minY;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int minY = level.getMinBuildHeight();
        BlockState[] col = new BlockState[topY(level) - minY + 1];
        column(x, z, minY, minY + col.length - 1, col);
        for (int i = 0; i < col.length; i++) if (col[i] == null) col[i] = Blocks.AIR.defaultBlockState();
        return new NoiseColumn(minY, col);
    }

    @Override
    public void addDebugScreenInfo(List<String> text, RandomState random, BlockPos pos) {
        DistrictGrid.Cell c = grid.cellAt(pos.getX(), pos.getZ());
        text.add(c == null ? "Dungeon: outside the districts"
                : "Dungeon district " + c.cx() + "," + c.cz() + " " + c.biome().displayName()
                + " doors " + c.open() + " " + c.orientation());
    }

    // ---- terrain --------------------------------------------------------------------------------------------

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState LINING = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    private static final BlockState LANTERN = Blocks.SEA_LANTERN.defaultBlockState();

    /** Fills {@code out[y - minY]} for y = minY .. top; null means air (or "leave for decoration"). */
    private void column(int x, int z, int minY, int top, BlockState[] out) {
        DistrictGrid.Cell cell = grid.cellAt(x, z);
        if (cell == null) {
            for (int y = minY; y <= top; y++) out[y - minY] = y < 0 ? DEEPSLATE : STONE;
            return;
        }
        Template template = DungeonTemplates.get(cell.biome());
        SpongeSchematic schem = template.schematic();
        Orientation o = cell.orientation();
        int[] t = o.toTemplate(DistrictGrid.offsetInCell(x), DistrictGrid.offsetInCell(z));
        // Turning a 176-wide template about its centre pushes one boundary row to index 176; it repeats the edge row, which
        // is what keeps a gate corridor continuous across the shared boundary between two districts.
        int tx = Math.max(0, Math.min(Template.SIZE - 1, Template.CENTER + t[0]));
        int tz = Math.max(0, Math.min(Template.SIZE - 1, Template.CENTER + t[1]));
        BlockState[] palette = palette(cell.biome(), o, schem);
        for (int y = minY; y <= top; y++) {
            BlockState s;
            if (y < 0) {
                s = DEEPSLATE;
            } else {
                s = templateBlock(cell, template, palette, o, tx, y, tz);
            }
            out[y - minY] = s;
        }
    }

    private BlockState templateBlock(DistrictGrid.Cell cell, Template template, BlockState[] palette, Orientation o,
                                     int tx, int y, int tz) {
        int idx = template.schematic().at(tx, y, tz);
        if (!template.isOpen(idx)) return palette[idx];
        if (template.isExterior(tx, y, tz)) {
            Side gate = template.corridorAt(tx, y, tz);
            if (gate == null || !cell.open().contains(o.toWorld(gate))) return STONE;
            if (Template.isCorridorClear(tx, y, tz, gate)) return idx == template.lightIndex() ? palette[idx] : null;
            boolean ceiling = y == Template.CLEAR_TOP_Y + 1;
            int along = gate == Side.N || gate == Side.S ? tz : tx;
            return ceiling && along % 6 == 3 && Template.isCorridorCentre(tx, tz, gate) ? LANTERN : LINING;
        }
        Side plug = template.plugAt(tx, y, tz);
        if (plug != null && !cell.open().contains(o.toWorld(plug))) return LINING;
        return palette[idx];
    }

    // ---- palettes -------------------------------------------------------------------------------------------

    /** The template palette as block states already turned and mirrored for one orientation; chests become air. */
    private BlockState[] palette(BiomeKind biome, Orientation o, SpongeSchematic schem) {
        Map<Orientation, BlockState[]> byOrientation;
        synchronized (palettes) {
            byOrientation = palettes.computeIfAbsent(biome, b -> new HashMap<>());
        }
        synchronized (byOrientation) {
            return byOrientation.computeIfAbsent(o, k -> {
                BlockState[] out = new BlockState[schem.palette().length];
                for (int i = 0; i < out.length; i++) {
                    String name = schem.palette()[i];
                    if (name.equals("minecraft:air") || name.startsWith("minecraft:chest")) continue;
                    out[i] = transform(parse(name), k);
                }
                return out;
            });
        }
    }

    private static BlockState parse(String state) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), state, false).blockState();
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("Template block state does not parse: " + state, e);
        }
    }

    private static BlockState transform(BlockState s, Orientation o) {
        if (o.mirror()) s = s.mirror(Mirror.FRONT_BACK);
        return s.rotate(switch (o.rotation()) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        });
    }

    // ---- loot chests ----------------------------------------------------------------------------------------

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        java.util.Set<DistrictGrid.Cell> cells = new java.util.HashSet<>();
        for (int[] corner : new int[][] {{minX, minZ}, {minX + 15, minZ}, {minX, minZ + 15}, {minX + 15, minZ + 15}}) {
            DistrictGrid.Cell c = grid.cellAt(corner[0], corner[1]);
            if (c != null) cells.add(c);
        }
        for (DistrictGrid.Cell cell : cells) {
            Template template = DungeonTemplates.get(cell.biome());
            for (int[] marker : template.chestMarkers()) {
                int[] d = cell.orientation().toWorld(marker[0] - Template.CENTER, marker[2] - Template.CENTER);
                int x = cell.cx() * DistrictGrid.CELL + d[0];
                int z = cell.cz() * DistrictGrid.CELL + d[1];
                if (x < minX || x > minX + 15 || z < minZ || z > minZ + 15) continue;
                placeChest(level, new BlockPos(x, marker[1], z), cell, template, marker);
            }
        }
    }

    private void placeChest(WorldGenLevel level, BlockPos pos, DistrictGrid.Cell cell, Template template, int[] marker) {
        String original = template.schematic().palette()[template.schematic().at(marker[0], marker[1], marker[2])];
        BlockState facing = transform(parse(original), cell.orientation());
        Block block = BuiltInRegistries.BLOCK.getOptional(LOOTR_CHEST).orElse(Blocks.CHEST);
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && facing.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction f = facing.getValue(BlockStateProperties.HORIZONTAL_FACING);
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, f);
        }
        level.setBlock(pos, state, 2);
        // data/ascensiondungeons/loot_table/chests/<biome>.json; the table is resolved when a player opens the chest.
        RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), pos, ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath("ascensiondungeons", "chests/" + cell.biome().name().toLowerCase(java.util.Locale.ROOT))));
    }
}
