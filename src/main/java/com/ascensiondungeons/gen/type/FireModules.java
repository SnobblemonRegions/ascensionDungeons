package com.ascensiondungeons.gen.type;

import com.ascensiondungeons.gen.landmark.Prims;
import com.ascensiondungeons.gen.landmark.Voxels;
import java.util.ArrayList;
import java.util.List;

/** Fire pilot: five shells, three route arrangements, fifteen independently placed landmark blockouts. */
public final class FireModules {
    public static final String AIR = "minecraft:air", STONE = "minecraft:netherrack", BRICK = "minecraft:red_nether_bricks",
            BASALT = "minecraft:basalt[axis=y]", MAGMA = "minecraft:magma_block", OBSIDIAN = "minecraft:obsidian",
            LAVA = "minecraft:lava[level=0]", BLACKSTONE = "minecraft:blackstone", QUARTZ = "minecraft:nether_quartz_ore",
            LIGHT = "minecraft:light[level=13]", GLOW = "minecraft:shroomlight", GOLD = "minecraft:gold_block",
            CHEST = "minecraft:chest[facing=south,type=single,waterlogged=false]";
    private static final Voxels[][] SHELLS = new Voxels[5][3];
    private static final List<Voxels> LANDMARKS = new ArrayList<>();
    private static Voxels arena;
    private FireModules() {}

    public static synchronized void preload() {
        if (arena != null) return;
        for (int shell = 0; shell < 5; shell++) for (int variant = 0; variant < 3; variant++) SHELLS[shell][variant] = shell(shell, variant);
        for (int i = 0; i < 15; i++) LANDMARKS.add(landmark(i));
        arena = arena();
    }
    public static Voxels room(int shell, int variant) { preload(); return SHELLS[shell][variant]; }
    public static Voxels feature(int id) { preload(); return LANDMARKS.get(id); }
    public static Voxels bossArena() { preload(); return arena; }

    private static Voxels shell(int kind, int variant) { return VolcanicCaverns.room(kind,variant,false); }

    private static Voxels landmark(int id) {
        Voxels v = new Voxels(17, 19, 17);
        Prims.disc(v,8,8,6,0,BRICK);
        switch (id) {
            case 0 -> { // Cinder crown
                Prims.tube(v,8,8,4,5,1,5,OBSIDIAN);
                for (int i=0;i<8;i++) { int x=8+(int)Math.round(5*Math.cos(i*Math.PI/4)), z=8+(int)Math.round(5*Math.sin(i*Math.PI/4)); Prims.column(v,x,6,9+(i%2)*2,z,BASALT); v.set(x,10+(i%2)*2,z,GLOW); }
            }
            case 1 -> { Prims.tube(v,8,8,2,4,1,15,BASALT); Prims.tube(v,8,8,4,5,12,13,BRICK); }
            case 2 -> { Prims.tube(v,8,8,4,6,1,7,OBSIDIAN); Prims.box(v,7,4,1,10,8,5,AIR); Prims.disc(v,8,8,3,1,MAGMA); }
            case 3 -> { Prims.box(v,3,1,7,5,11,9,OBSIDIAN); Prims.box(v,11,1,7,13,11,9,OBSIDIAN); Prims.box(v,3,11,7,13,13,9,OBSIDIAN); Prims.box(v,6,11,7,10,11,9,GOLD); }
            case 4 -> { Prims.box(v,7,1,7,9,11,9,BASALT); Prims.box(v,3,7,7,12,8,9,BASALT); Prims.box(v,9,10,5,14,11,7,OBSIDIAN); v.set(3,9,8,GLOW); v.set(13,12,6,GLOW); }
            case 5 -> { Prims.box(v,3,1,3,13,6,13,BRICK); Prims.box(v,5,2,2,11,4,4,AIR); Prims.box(v,6,2,4,10,3,6,GLOW); Prims.tube(v,8,8,2,3,7,13,BASALT); }
            case 6 -> { Prims.box(v,3,1,5,13,3,11,BASALT); Prims.box(v,5,4,6,11,4,10,"minecraft:copper_block"); Prims.column(v,8,5,8,8,"minecraft:copper_block"); }
            case 7 -> { Prims.disc(v,8,8,5,1,OBSIDIAN); Prims.tube(v,8,8,3,4,2,4,BRICK); Prims.disc(v,8,8,2,2,GLOW); }
            case 8 -> { Prims.box(v,5,1,5,11,2,11,BASALT); Prims.box(v,7,3,7,9,5,9,"minecraft:iron_block"); Prims.box(v,4,6,6,12,7,10,"minecraft:iron_block"); }
            case 9 -> { Prims.ring(v,8,8,5,6,1,GOLD); Prims.ring(v,8,8,3,4,1,BASALT); for (int x:new int[]{3,13}) { Prims.column(v,x,1,5,8,OBSIDIAN); v.set(x,6,8,GLOW); } }
            case 10 -> { Prims.box(v,3,1,3,13,7,13,BRICK); Prims.box(v,5,1,4,11,5,11,AIR); Prims.box(v,6,1,3,10,4,3,AIR); v.set(8,1,8,CHEST); }
            case 11 -> { Prims.box(v,3,1,10,13,8,12,BRICK); for(int x=4;x<=12;x+=2) Prims.column(v,x,1,8,9,"minecraft:bookshelf"); v.set(8,1,6,CHEST); }
            case 12 -> { Prims.tube(v,8,8,4,6,1,6,OBSIDIAN); Prims.box(v,5,2,1,11,6,5,AIR); for(int x:new int[]{5,11}) Prims.column(v,x,3,8,8,GLOW); v.set(8,1,8,CHEST); }
            case 13 -> { Prims.box(v,4,1,6,12,5,12,BRICK); Prims.box(v,6,1,6,10,4,10,AIR); v.set(8,1,8,CHEST); Prims.box(v,6,6,7,10,6,11,"minecraft:iron_block"); }
            case 14 -> { Prims.box(v,2,1,3,14,8,5,OBSIDIAN); Prims.box(v,5,1,3,11,5,5,AIR); Prims.box(v,2,1,11,14,8,13,BRICK); v.set(8,1,9,CHEST); }
            default -> throw new IllegalArgumentException("landmark " + id);
        }
        // Warm edges and magma cores read as volcanic ruins throughout the landmark library.
        for(int i=2;i<=14;i+=4) { v.set(i,0,2,MAGMA); v.set(i,0,14,MAGMA); }
        if(id<10) {
            for(int x=7;x<=9;x++)for(int z=7;z<=9;z++)if(v.get(x,1,z)==null) v.set(x,1,z,GLOW);
        }
        return v;
    }

    private static Voxels arena() { return VolcanicCaverns.room(3,0,true); }
}
