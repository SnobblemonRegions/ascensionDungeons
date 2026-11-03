package com.ascensiondungeons.gen.type;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;

class TypePlanTest {
    @Test void seededPlansKeepDoorsConnectedAndLandmarksUnique() {
        for(long seed=0;seed<200;seed++) {
            var plan=TypePlan.fire(seed);
            assertEquals(plan,TypePlan.fire(seed));
            assertEquals(5,plan.rooms().stream().map(TypePlan.Room::shell).distinct().count());
            assertEquals(12,plan.rooms().stream().map(TypePlan.Room::landmark).distinct().count());
            var seen=new HashSet<Integer>();var queue=new ArrayDeque<Integer>();queue.add(0);
            while(!queue.isEmpty()) {
                int i=queue.remove();if(!seen.add(i))continue;
                var room=plan.rooms().get(i);
                for(int[] step:new int[][]{{TypePlan.N,TypePlan.S,-4},{TypePlan.E,TypePlan.W,1},{TypePlan.S,TypePlan.N,4},{TypePlan.W,TypePlan.E,-1}}) {
                    if((room.doors()&step[0])==0)continue;
                    int j=i+step[2];assertTrue(j>=0&&j<12);assertNotEquals(0,plan.rooms().get(j).doors()&step[1]);queue.add(j);
                    assertNotEquals(room.shell(),plan.rooms().get(j).shell(),"connected neighbors should use different shells");
                }
            }
            assertEquals(12,seen.size());
            assertNull(plan.at(plan.boss()[0],plan.boss()[2]),"boss is isolated from exploration");
        }
    }
    @Test void catalogueHasAllTypesAndDoesNotAdvertiseDraftsAsPlayable() {
        assertEquals(18,TypeCatalogue.all().size());
        assertEquals(90,TypeCatalogue.all().stream().mapToInt(t->t.rooms().size()).sum());
        assertEquals(270,TypeCatalogue.all().stream().mapToInt(t->t.landmarks().size()).sum());
        assertEquals(1,TypeCatalogue.all().stream().filter(TypeCatalogue.Theme::playable).count());
        assertTrue(TypeCatalogue.get("fire").playable());
        assertFalse(TypeCatalogue.get("water").playable());
    }
    @Test void everyRoomVariantConnectsDoorsSpawnAndLandmarkWithoutLava() {
        for(int kind=0;kind<5;kind++)for(int variant=0;variant<3;variant++) {
            var v=FireModules.room(kind,variant);var seen=new HashSet<Integer>();var q=new ArrayDeque<Integer>();q.add(32+32*64);
            while(!q.isEmpty()) {
                int key=q.remove(),x=key%64,z=key/64;if(!seen.add(key))continue;
                for(int[] d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
                    int nx=x+d[0],nz=z+d[1];if(nx<0||nx>=64||nz<0||nz>=64)continue;
                    boolean clear=!FireModules.AIR.equals(v.get(nx,4,nz))&&!FireModules.LAVA.equals(v.get(nx,4,nz))&&!FireModules.MAGMA.equals(v.get(nx,4,nz));
                    for(int y=5;y<=7;y++)clear&=FireModules.AIR.equals(v.get(nx,y,nz));
                    if(clear&&!seen.contains(nx+nz*64))q.add(nx+nz*64);
                }
            }
            for(int[] target:new int[][]{{32,0},{63,32},{32,63},{0,32},{17,26}})assertTrue(seen.contains(target[0]+target[1]*64),"Missing walkable target for shell "+kind+", variant "+variant);
            assertNotEquals(FireModules.AIR,v.get(32,47,32));
            int exposed=0;
            for(int x=0;x<64;x++)for(int z=0;z<64;z++)if(FireModules.LAVA.equals(v.get(x,3,z))) {
                assertNotEquals(FireModules.AIR,v.get(x,2,z));
                for(int[] d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}})assertNotEquals(FireModules.AIR,v.get(x+d[0],3,z+d[1]));
                if(FireModules.AIR.equals(v.get(x,4,z))&&FireModules.AIR.equals(v.get(x,5,z)))exposed++;
            }
            assertTrue(exposed>120,"Room requires a visible lake: "+kind+"/"+variant+" count="+exposed);
        }
        var arena=FireModules.bossArena();
        assertEquals(FireModules.OBSIDIAN,arena.get(32,4,32));
        for(int y=5;y<13;y++)assertEquals(FireModules.AIR,arena.get(32,y,48));
    }

    @Test void landmarksCannotBlockTheRequiredRoutesAfterComposition() {
        for(int kind=0;kind<5;kind++)for(int variant=0;variant<3;variant++)for(int id=0;id<15;id++) {
            var room=FireModules.room(kind,variant);var feature=FireModules.feature(id);
            var seen=new HashSet<Integer>();var queue=new ArrayDeque<Integer>();queue.add(32+32*64);
            while(!queue.isEmpty()) {
                int key=queue.remove(),x=key%64,z=key/64;if(!seen.add(key))continue;
                for(int[] d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
                    int nx=x+d[0],nz=z+d[1];if(nx<0||nx>=64||nz<0||nz>=64)continue;
                    String floor=room.get(nx,4,nz);boolean clear=!FireModules.AIR.equals(floor)&&!FireModules.LAVA.equals(floor)&&!FireModules.MAGMA.equals(floor);
                    for(int y=5;y<=7;y++) {
                        String block=room.get(nx,y,nz);
                        if(nx>=9&&nx<=25&&nz>=9&&nz<=25) {String overlay=feature.get(nx-9,y-5,nz-9);if(overlay!=null)block=overlay;}
                        clear&=FireModules.AIR.equals(block);
                    }
                    if(clear&&!seen.contains(nx+nz*64))queue.add(nx+nz*64);
                }
            }
            for(int[] target:new int[][]{{32,0},{63,32},{32,63},{0,32},{17,26}})assertTrue(seen.contains(target[0]+target[1]*64),"Landmark "+id+" blocks room "+kind+"/"+variant);
        }
    }
}
