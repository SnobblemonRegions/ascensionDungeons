package com.ascensiondungeons.mc;

import com.ascensiondungeons.gen.type.FireModules;
import com.ascensiondungeons.gen.type.TypePlan;
import com.ascensiondungeons.gen.landmark.Voxels;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
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
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/** Server-only vanilla chunk data. No client registry, renderer or custom packet is required. */
public final class TypeDungeonChunkGenerator extends ChunkGenerator {
    public static final MapCodec<TypeDungeonChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.CODEC.fieldOf("biome").forGetter(g -> g.biome),
            Codec.LONG.fieldOf("seed").forGetter(g -> g.plan.seed())
    ).apply(i, TypeDungeonChunkGenerator::new));
    private static final Map<String, BlockState> STATES = new ConcurrentHashMap<>();
    private final Holder<Biome> biome;
    private final TypePlan plan;
    public TypeDungeonChunkGenerator(Holder<Biome> biome, long seed) {
        super(new FixedBiomeSource(biome)); this.biome = biome; plan = TypePlan.fire(seed); FireModules.preload();
    }
    public TypePlan plan() { return plan; }
    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }
    @Override public int getGenDepth() { return 384; }
    @Override public int getMinY() { return -64; }
    @Override public int getSeaLevel() { return TypePlan.FLOOR; }
    @Override public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structures, RandomState random, long seed) {
        return ChunkGeneratorStructureState.createForFlat(random, seed, biomeSource, java.util.stream.Stream.empty());
    }
    @Override public void applyCarvers(WorldGenRegion region,long seed,RandomState random,BiomeManager biomes,StructureManager structures,ChunkAccess chunk,GenerationStep.Carving step) {}
    @Override public void buildSurface(WorldGenRegion region,StructureManager structures,RandomState random,ChunkAccess chunk) {}
    @Override public void spawnOriginalMobs(WorldGenRegion region) {}
    // Required common ChunkGenerator API; this contains no client references or rendering code.
    @Override public void addDebugScreenInfo(java.util.List<String> info,RandomState random,BlockPos pos) {}

    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender,RandomState random,StructureManager structures,ChunkAccess chunk) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        for(int x=x0;x<x0+16;x++) for(int z=z0;z<z0+16;z++) for(int y=chunk.getMinBuildHeight();y<TypePlan.HEIGHT;y++) {
            BlockState s = block(x,y,z);
            if (!s.isAir()) chunk.setBlockState(pos.set(x,y,z),s,false);
        }
        Heightmap.primeHeightmaps(chunk,java.util.EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG,Heightmap.Types.OCEAN_FLOOR_WG));
        return CompletableFuture.completedFuture(chunk);
    }
    @Override public int getBaseHeight(int x,int z,Heightmap.Types type,LevelHeightAccessor level,RandomState random) { return TypePlan.HEIGHT; }
    @Override public NoiseColumn getBaseColumn(int x,int z,LevelHeightAccessor level,RandomState random) {
        BlockState[] out = new BlockState[TypePlan.HEIGHT-level.getMinBuildHeight()];
        for(int i=0;i<out.length;i++) out[i]=block(x,i+level.getMinBuildHeight(),z);
        return new NoiseColumn(level.getMinBuildHeight(),out);
    }
    private BlockState block(int x,int y,int z) {
        if(y<0) return Blocks.DEEPSLATE.defaultBlockState();
        if(y>=TypePlan.HEIGHT) return Blocks.AIR.defaultBlockState();
        String raw=raw(x,y,z);
        if(raw.startsWith("minecraft:chest"))return Blocks.AIR.defaultBlockState();
        BlockState result=state(raw);
        TypePlan.Room room=plan.at(x,z);
        if(room!=null)result=result.rotate(switch(room.rotation()) {
            case 1 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
            case 2 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_180;
            case 3 -> net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
            default -> net.minecraft.world.level.block.Rotation.NONE;
        });
        return result;
    }
    /** Immutable template lookup and bounded module overlay, never world mutation or global scans. */
    private String raw(int x,int y,int z) {
        if(Math.floorDiv(x,64)==5 && Math.floorDiv(z,64)==0) return FireModules.bossArena().get(Math.floorMod(x,64),y,Math.floorMod(z,64));
        TypePlan.Room room=plan.at(x,z);
        if(room==null) return FireModules.STONE;
        int lx=Math.floorMod(x,64), lz=Math.floorMod(z,64), tx=lx,tz=lz;
        for(int r=0;r<room.rotation();r++) { int old=tx; tx=tz; tz=63-old; }
        String original=FireModules.room(room.shell(),room.variant()).get(tx,y,tz);
        if(y>4 && y<=12 && (lx<5 || lx>58 || lz<5 || lz>58)) {
            boolean aperture = (lx>=28&&lx<=36&&lz<5&&(room.doors()&TypePlan.N)!=0)
                    || (lx>=28&&lx<=36&&lz>58&&(room.doors()&TypePlan.S)!=0)
                    || (lz>=28&&lz<=36&&lx<5&&(room.doors()&TypePlan.W)!=0)
                    || (lz>=28&&lz<=36&&lx>58&&(room.doors()&TypePlan.E)!=0);
            return aperture ? FireModules.AIR : FireModules.BRICK;
        }
        // A single pre-cleared corner socket: rotate it together with its module and markers.
        if(tx>=9&&tx<=25&&tz>=9&&tz<=25&&y>=5&&y<24) {
            String overlay=FireModules.feature(room.landmark()).get(tx-9,y-5,tz-9);
            if(overlay!=null) original=overlay;
        }
        return original==null?FireModules.AIR:original;
    }
    private static BlockState state(String name) {
        return STATES.computeIfAbsent(name,n -> {
            try { return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(),n,false).blockState(); }
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new IllegalStateException("Invalid type module block "+n,e); }
        });
    }
    @Override public void applyBiomeDecoration(WorldGenLevel level,ChunkAccess chunk,StructureManager structures) {
        int x0=chunk.getPos().getMinBlockX(),z0=chunk.getPos().getMinBlockZ();
        // Only discovery modules carry chest markers; five possible markers, never a room-volume scan.
        for(TypePlan.Room room:plan.rooms()) if(room.landmark()>=10) {
            int x=17,z=room.landmark()==11?15:room.landmark()==14?18:17;
            for(int r=0;r<room.rotation();r++) { int old=x; x=63-z; z=old; }
            x+=room.cx()*64; z+=room.cz()*64;
            if(x<x0||x>=x0+16||z<z0||z>=z0+16) continue;
            BlockPos pos=new BlockPos(x,6,z);
            var chest=BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("lootr","lootr_chest")).orElse(Blocks.CHEST);
            level.setBlock(pos,chest.defaultBlockState(),2);
            RandomizableContainer.setBlockEntityLootTable(level,level.getRandom(),pos,ResourceKey.create(Registries.LOOT_TABLE,
                    ResourceLocation.fromNamespaceAndPath("ascensiondungeons","chests/ashen_caldera")));
        }
    }
}
