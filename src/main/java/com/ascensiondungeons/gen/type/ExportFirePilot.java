package com.ascensiondungeons.gen.type;

import com.ascensiondungeons.gen.SpongeSchematic;
import com.ascensiondungeons.gen.SpongeSchematicWriter;
import com.ascensiondungeons.gen.landmark.Voxels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

/** Offline build artifact export. No server or client classes used. */
public final class ExportFirePilot {
    public static void main(String[] args) throws Exception {
        Path dir=Path.of(args.length==0?"outputs/fire-pilot":args[0]);Files.createDirectories(dir);
        var theme=TypeCatalogue.get("fire");
        for(int shell=0;shell<5;shell++)for(int variant=0;variant<3;variant++) write(dir.resolve("room_"+shell+"_variant_"+variant+".schem"),FireModules.room(shell,variant),theme.rooms().get(shell));
        for(int i=0;i<15;i++)write(dir.resolve(theme.landmarks().get(i).id()+".schem"),FireModules.feature(i),theme.landmarks().get(i).name());
        write(dir.resolve("boss_arena.schem"),FireModules.bossArena(),theme.arena());
        Files.writeString(dir.resolve("markers.json"),"""
                {"format":"fire-pilot-v1","roomSize":[64,48,64],"floorY":4,
                 "doorWidth":9,"doorAirY":[5,12],"landmarkSocket":{"origin":[9,5,9],"size":[17,19,17]},
                 "bossAnchor":[32,5,32],"bossStaging":[32,5,48],"status":"blockout"}
                """);
    }
    private static void write(Path file,Voxels voxels,String name) throws Exception {
        var palette=new LinkedHashMap<String,Integer>();palette.put("minecraft:air",0);
        short[] blocks=new short[voxels.sizeX()*voxels.sizeY()*voxels.sizeZ()];
        for(int y=0;y<voxels.sizeY();y++)for(int z=0;z<voxels.sizeZ();z++)for(int x=0;x<voxels.sizeX();x++) {
            String state=voxels.get(x,y,z);if(state==null)state="minecraft:air";
            int id=palette.computeIfAbsent(state,s->palette.size());blocks[x+z*voxels.sizeX()+y*voxels.sizeX()*voxels.sizeZ()]=(short)id;
        }
        try(var out=Files.newOutputStream(file)) { SpongeSchematicWriter.write(new SpongeSchematic(voxels.sizeX(),voxels.sizeY(),voxels.sizeZ(),palette.keySet().toArray(String[]::new),blocks),name,"Ascension type dungeon blockout",out); }
    }
}
