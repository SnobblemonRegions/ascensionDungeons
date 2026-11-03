package com.ascensiondungeons.gen.type;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ArchSupportRegressionTest {
    @Test void routeClearanceMustNotAmputateTheNetherArchPillar() {
        var room=FireModules.room(2,0);
        assertEquals("minecraft:nether_bricks",room.get(36,9,19),"fixture contains the arch pillar above the route cut");
        assertNotEquals(FireModules.AIR,room.get(36,5,19),"pillar base must not be deleted by path clearance");
    }
    @Test void decorativeArchPostsMustMeetTheArchInsteadOfHangingAboveIt() {
        var room=FireModules.room(2,0);
        for(int x=36;x<=50;x+=2)for(int y=22;y<=24;y++) {
            if("minecraft:polished_blackstone_bricks".equals(room.get(x,y,19))) {
                assertNotEquals(FireModules.AIR,room.get(x,y-1,19),"Floating decorative post at "+x+","+y+",19");
            }
        }
    }
    @Test void balconyStaircaseMustHaveSolidSupports() {
        var room=FireModules.room(1,0);
        assertTrue(room.get(43,8,50).startsWith("minecraft:blackstone_stairs"));
        assertNotEquals(FireModules.AIR,room.get(43,7,50),"Balcony step must not float above the lake");
    }
    @Test void placedMasonryMustStayConnectedToSolidTerrainInEveryVariant() {
        for(int kind=0;kind<5;kind++)for(int variant=0;variant<3;variant++)checkSupported(FireModules.room(kind,variant),kind+"/"+variant);
        checkSupported(FireModules.bossArena(),"arena");
    }
    @Test void detoursMustRemainReachableAfterLiveConnectorWallsAreApplied() {
        for(int kind=0;kind<5;kind++)for(int variant=0;variant<3;variant++) {
            var v=FireModules.room(kind,variant);var seen=new java.util.HashSet<Integer>();var queue=new java.util.ArrayDeque<Integer>();queue.add(32+32*64);
            while(!queue.isEmpty()) {
                int key=queue.remove(),x=key%64,z=key/64;if(!seen.add(key))continue;
                for(int[] d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
                    int nx=x+d[0],nz=z+d[1];if(nx<0||nx>=64||nz<0||nz>=64)continue;
                    boolean edge=nx<5||nx>58||nz<5||nz>58;
                    boolean aperture=(nx>=28&&nx<=36&&(nz<5||nz>58))||(nz>=28&&nz<=36&&(nx<5||nx>58));
                    if(edge&&!aperture)continue;
                    boolean clear=!FireModules.AIR.equals(v.get(nx,4,nz))&&!FireModules.LAVA.equals(v.get(nx,4,nz));
                    for(int y=5;y<=7;y++)if(!edge)clear&=FireModules.AIR.equals(v.get(nx,y,nz));
                    if(clear&&!seen.contains(nx+nz*64))queue.add(nx+nz*64);
                }
            }
            for(int[] point:new int[][]{{32,0},{63,32},{32,63},{0,32}})assertTrue(seen.contains(point[0]+point[1]*64),"Live walls seal detour in "+kind+"/"+variant);
        }
    }
    private static void checkSupported(com.ascensiondungeons.gen.landmark.Voxels v,String label) {
        int volume=64*48*64;boolean[] seen=new boolean[volume];int[] queue=new int[volume];int head=0,tail=0;
        for(int z=0;z<64;z++)for(int x=0;x<64;x++) {int key=x+z*64+2*4096;seen[key]=true;queue[tail++]=key;}
        while(head<tail) {
            int key=queue[head++],x=key%64,z=(key/64)%64,y=key/4096;
            for(int[] d:new int[][]{{-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{0,0,-1},{0,0,1}}) {
                int nx=x+d[0],ny=y+d[1],nz=z+d[2];if(!v.inBounds(nx,ny,nz))continue;
                int next=nx+nz*64+ny*4096;if(seen[next])continue;
                String block=v.get(nx,ny,nz);
                if(block==null||block.equals(FireModules.AIR)||block.equals(FireModules.LAVA)||block.startsWith("minecraft:light["))continue;
                seen[next]=true;queue[tail++]=next;
            }
        }
        for(int y=0;y<48;y++)for(int z=0;z<64;z++)for(int x=0;x<64;x++) {
            String block=v.get(x,y,z);
            if(block.startsWith("minecraft:nether_brick")||block.equals("minecraft:polished_blackstone_bricks")||block.startsWith("minecraft:blackstone_stairs")) {
                assertTrue(seen[x+z*64+y*4096],"Detached masonry in "+label+" at "+x+","+y+","+z+": "+block);
            }
        }
    }
}
