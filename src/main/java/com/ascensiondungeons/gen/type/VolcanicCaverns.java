package com.ascensiondungeons.gen.type;

import com.ascensiondungeons.gen.landmark.Prims;
import com.ascensiondungeons.gen.landmark.Voxels;

/** Authored route compositions with geological banks and layered Nether architecture. */
final class VolcanicCaverns {
    private static final String ROCK="minecraft:blackstone", BASALT="minecraft:basalt[axis=y]", NETHER="minecraft:netherrack",
            BRICK="minecraft:nether_bricks", TRIM="minecraft:polished_blackstone_bricks", SLAB="minecraft:nether_brick_slab[type=bottom,waterlogged=false]",
            FENCE="minecraft:nether_brick_fence", STAIR="minecraft:blackstone_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]";
    private VolcanicCaverns() {}

    static Voxels room(int kind,int variant,boolean arena) {
        Voxels v=new Voxels(64,48,64);
        boolean[][] paths=paths(kind,variant,arena);
        for(int x=0;x<64;x++)for(int z=0;z<64;z++) {
            double noise=wave(x,z,variant+kind*7);
            double radial=Math.hypot((x-32)*1.04,z-31);
            double rim=radial-(27+noise*2.3);
            int ceiling=ceiling(kind,variant,x,z);
            boolean socket=!arena&&socket(x,z);
            int bank=bank(kind,variant,x,z);
            if(socket)bank=4;
            for(int y=0;y<48;y++) {
                boolean wall=x<2||z<2||x>61||z>61||rim>1.4*Math.sin(y*.24+noise);
                boolean solid=y<=2||wall||y>=ceiling||y<=bank;
                String s=solid?rock(x,y,z,kind):FireModules.AIR;
                if(!wall && bank<3 && y==3)s=FireModules.LAVA;
                if(!wall&&bank>=4&&y==bank)s=surface(x,z,kind);
                if(y==ceiling-1&&!wall&&x%11==6&&z%11==6)s=FireModules.LIGHT;
                v.set(x,y,z,s);
            }
        }
        // Basalt sheets run up cliffs rather than appearing as isolated repeated cone props.
        for(int x=5;x<59;x++)for(int z=5;z<59;z++) {
            if(Prims.hash(x/2,0,z/2,kind*19+variant)>.73) {
                int h=bank(kind,variant,x,z);
                if(h>8)for(int y=4;y<h;y++)v.set(x,y,z,y%7==0?ROCK:BASALT);
            }
        }
        Voxels architecture=new Voxels(64,48,64);
        boolean[][][] protectedStructure=new boolean[64][48][64];
        switch(kind) {
            case 0 -> { chimney(architecture,43+variant,21,25);fortress(architecture,52,38,17,variant); }
            case 1 -> { arcade(architecture,48,8,47,variant);gate(architecture,22,42,15,true); }
            case 2 -> { chimney(architecture,13,44,22);gate(architecture,43,19,18,false);fortress(architecture,52,43,12,variant); }
            case 3 -> { gate(architecture,39,19,23,true);fortress(architecture,13,47,19,variant); }
            case 4 -> { arcade(architecture,49,15,38,variant);chimney(architecture,44,47,24);gate(architecture,18,44,12,false); }
            default -> throw new IllegalArgumentException("room "+kind);
        }
        for(int x=0;x<64;x++)for(int y=0;y<48;y++)for(int z=0;z<64;z++) {
            String block=architecture.get(x,y,z);
            if(block!=null) {v.set(x,y,z,block);protectedStructure[x][y][z]=!FireModules.AIR.equals(block);}
        }
        ceilingTeeth(v,protectedStructure,kind,variant);
        try { routeAroundStructures(protectedStructure,paths,arena); }
        catch(IllegalStateException e) { throw new IllegalStateException("Room "+kind+"/"+variant+" arena="+arena+": "+e.getMessage(),e); }
        // Clear the actual routes after decoration; unlike the old cross they bend through each composition.
        for(int x=0;x<64;x++)for(int z=0;z<64;z++)if(paths[x][z]||(!arena&&socket(x,z))) {
            v.set(x,4,z,surface(x,z,kind));
            for(int y=5;y<=8;y++)clearTerrain(v,protectedStructure,x,y,z);
        }
        if(!arena) {
            Prims.box(v,9,5,9,25,23,25,FireModules.AIR);
            for(int x=9;x<=25;x++)for(int z=9;z<=25;z++)v.set(x,4,z,surface(x,z,kind));
            doors(v);
        } else {
            // Broad asymmetric boss shelf and dry staging approach, with cliffs and ruins framing the lake.
            for(int x=17;x<=47;x++)for(int z=19;z<=45;z++) {
                if(Math.hypot((x-32)*.85,z-32)<14) {
                    v.set(x,4,z,FireModules.OBSIDIAN);
                    for(int y=5;y<=16;y++)clearTerrain(v,protectedStructure,x,y,z);
                }
            }
            for(int x=29;x<=37;x++)for(int z=42;z<=52;z++) {
                v.set(x,4,z,TRIM);for(int y=5;y<=12;y++)clearTerrain(v,protectedStructure,x,y,z);
            }
        }
        // Chunk generation uses fully contained lava at Y=3; fill any openings below that level with rock.
        for(int x=0;x<64;x++)for(int z=0;z<64;z++) {
            v.set(x,2,z,ROCK);
            if(FireModules.AIR.equals(v.get(x,3,z)))v.set(x,3,z,ROCK);
        }
        return v;
    }

    private static void clearTerrain(Voxels v,boolean[][][] protectedStructure,int x,int y,int z) {
        // Track placement provenance, not material: terrain can legitimately use the same blocks as masonry.
        if(!protectedStructure[x][y][z])v.set(x,y,z,FireModules.AIR);
    }

    private record RouteNode(int key,int cost) {}
    private static void routeAroundStructures(boolean[][][] protectedStructure,boolean[][] paths,boolean arena) {
        boolean[][] blocked=new boolean[64][64];
        for(int x=0;x<64;x++)for(int z=0;z<64;z++) {
            boolean support=false;
            for(int y=5;y<=8;y++)support|=protectedStructure[x][y][z];
            if(support)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++) {
                int nx=x+dx,nz=z+dz;if(nx>=0&&nx<64&&nz>=0&&nz<64)blocked[nx][nz]=true;
            }
        }
        int[][] targets=arena?new int[][]{{32,48}}:new int[][]{{32,0},{63,32},{32,63},{0,32},{17,26}};
        for(int[] target:targets) {
            int[] distance=new int[4096],previous=new int[4096];java.util.Arrays.fill(distance,Integer.MAX_VALUE);java.util.Arrays.fill(previous,-1);
            int start=32+32*64,goal=target[0]+target[1]*64;
            var queue=new java.util.PriorityQueue<RouteNode>(java.util.Comparator.comparingInt(RouteNode::cost).thenComparingInt(RouteNode::key));
            distance[start]=0;queue.add(new RouteNode(start,0));
            while(!queue.isEmpty()) {
                RouteNode node=queue.remove();if(node.cost()!=distance[node.key()])continue;if(node.key()==goal)break;
                int x=node.key()%64,z=node.key()/64;
                for(int[] step:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
                    int nx=x+step[0],nz=z+step[1];if(nx<0||nx>=64||nz<0||nz>=64||blocked[nx][nz])continue;
                    int next=nx+nz*64,cost=node.cost()+(paths[nx][nz]?1:6);
                    if(cost<distance[next]) {distance[next]=cost;previous[next]=node.key();queue.add(new RouteNode(next,cost));}
                }
            }
            if(distance[goal]==Integer.MAX_VALUE)throw new IllegalStateException("Structure blocks required route to "+target[0]+","+target[1]);
            for(int key=goal;key>=0;key=previous[key]) {
                int x=key%64,z=key/64;
                for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++) {
                    int nx=x+dx,nz=z+dz;if(nx>=0&&nx<64&&nz>=0&&nz<64)paths[nx][nz]=true;
                }
            }
        }
    }

    static boolean[][] paths(int kind,int variant,boolean arena) {
        boolean[][] mask=new boolean[64][64];
        if(arena) { stroke(mask,new int[][]{{32,48},{32,32}},4);return mask; }
        int shift=variant-1;
        int[][][] lines=switch(kind) {
            case 0 -> new int[][][] {
                    {{32,0},{32,7},{30,15},{30,25},{31,34}},{{63,32},{56,32},{49,41},{40,43},{31,34}},
                    {{32,63},{32,56},{23,49},{25,41},{31,34}},{{0,32},{7,32},{15,38},{23,38},{31,34}}};
            case 1 -> new int[][][] {
                    {{32,0},{32,7},{38,14},{37,23},{28,30}},{{63,32},{56,32},{45,25},{36,24},{28,30}},
                    {{32,63},{32,56},{24,48},{23,38},{28,30}},{{0,32},{7,32},{15,30},{23,30},{28,30}}};
            case 2 -> new int[][][] {
                    {{32,0},{32,8},{40,16},{39,24},{33,34}},{{63,32},{56,32},{50,41},{41,42},{33,34}},
                    {{32,63},{32,56},{23,50},{22,42},{33,34}},{{0,32},{7,32},{15,39},{25,39},{33,34}}};
            case 3 -> new int[][][] {
                    {{32,0},{32,8},{30,16},{30,26},{34,33}},{{63,32},{55,32},{49,24},{42,25},{34,33}},
                    {{32,63},{32,55},{42,48},{42,40},{34,33}},{{0,32},{8,32},{16,41},{26,40},{34,33}}};
            case 4 -> new int[][][] {
                    {{32,0},{32,8},{39,15},{40,25},{31,34}},{{63,32},{55,32},{48,39},{39,40},{31,34}},
                    {{32,63},{32,55},{24,49},{23,42},{31,34}},{{0,32},{8,32},{15,30},{22,30},{31,34}}};
            default -> throw new IllegalArgumentException("room "+kind);
        };
        for(int[][] line:lines) {
            for(int i=2;i<line.length-1;i++){line[i][0]+=shift*2;line[i][1]-=shift;}
            stroke(mask,line,3.1);
        }
        int[] hub=lines[0][lines[0].length-1];
        stroke(mask,new int[][]{hub,{32,32}},3.5);
        stroke(mask,new int[][]{hub,{24,28},{17,26}},3.2);
        return mask;
    }
    private static void stroke(boolean[][] mask,int[][] line,double radius) {
        for(int i=1;i<line.length;i++)for(int x=0;x<64;x++)for(int z=0;z<64;z++) {
            int[] a=line[i-1],b=line[i];double dx=b[0]-a[0],dz=b[1]-a[1];
            double t=Math.clamp(((x-a[0])*dx+(z-a[1])*dz)/(dx*dx+dz*dz),0,1);
            if(Math.hypot(x-a[0]-t*dx,z-a[1]-t*dz)<=radius)mask[x][z]=true;
        }
    }
    private static boolean socket(int x,int z) {return x>=9&&x<=25&&z>=9&&z<=25;}
    private static double wave(int x,int z,int seed) {return Math.sin(x*.13+seed)+Math.cos(z*.17-seed*.4)+.5*Math.sin((x+z)*.21);}
    private static int ceiling(int kind,int variant,int x,int z) {
        return Math.clamp((int)(37+3*Math.sin(x*.14+kind)+2*Math.cos(z*.18+variant)-Math.hypot(x-32,z-32)*.09),29,43);
    }
    private static int bank(int kind,int variant,int x,int z) {
        double n=wave(x,z,kind*3+variant);
        double edge=Math.max(Math.abs(x-32),Math.abs(z-32));
        double h=switch(kind) {
            case 0 -> 1+Math.max(0,edge-18)*1.15+Math.max(0,8-Math.hypot(x-43,z-21))*1.8;
            case 1 -> x>43?9+(x-43)*.65:1+Math.max(0,19-x)*.55+Math.max(0,z-49)*.8;
            case 2 -> Math.abs(x-(33+4*Math.sin(z*.1)))<13?1:8+Math.max(0,edge-19)*.9;
            case 3 -> z>43?7+(z-43)*.65:1+Math.max(0,21-x)*.6+Math.max(0,x-48)*.9;
            case 4 -> 2+Math.max(0,22-x)*.65+Math.max(0,z-46)*.9+Math.max(0,x-48)*.7;
            default -> 2;
        };
        return Math.clamp((int)Math.floor(h+n*.8),2,23);
    }
    private static String rock(int x,int y,int z,int kind) {
        if(y<5)return ROCK;
        // Continuous deposits and warped strata avoid the old checkerboard material patches.
        double deposit=Math.sin(x*.14+kind)+Math.cos(z*.16-kind)+.4*Math.sin((x-z)*.11);
        double fault=3*Math.sin(x*.11+z*.07)+2*Math.cos(z*.14);
        if(deposit>1.25)return NETHER;
        if(Math.sin((y+fault)*.38+kind)>.62)return BASALT;
        if(deposit>1.1&&y<18)return FireModules.MAGMA;
        return ROCK;
    }
    private static String surface(int x,int z,int kind) {
        return Prims.pick(x,4,z,119+kind,ROCK,.45,"minecraft:polished_basalt[axis=y]",.32,TRIM,.13,FireModules.OBSIDIAN,.10);
    }
    private static void doors(Voxels v) {
        for(int y=5;y<=12;y++)for(int a=27;a<=36;a++)for(int b=0;b<6;b++) {
            v.set(a,y,b,FireModules.AIR);v.set(a,y,63-b,FireModules.AIR);v.set(b,y,a,FireModules.AIR);v.set(63-b,y,a,FireModules.AIR);
            v.set(a,4,b,TRIM);v.set(a,4,63-b,TRIM);v.set(b,4,a,TRIM);v.set(63-b,4,a,TRIM);
        }
    }

    private static void chimney(Voxels v,int x,int z,int h) {
        for(int y=3;y<h;y++) {
            double radius=4.8-(y-3)*.13;
            Prims.disc(v,x,z,radius,y,y%5==0?ROCK:BASALT);
            if(y>7) {v.setClipped(x+2,y,z,FireModules.MAGMA);if(y%4==0)v.setClipped(x+2,y,z,FireModules.GLOW);}
        }
        Prims.tube(v,x,z,1.5,3,h-2,h+1,FireModules.OBSIDIAN);
        Prims.disc(v,x,z,1.4,h-1,FireModules.GLOW);
        Prims.column(v,x-2,h,h+4,z,BASALT);
    }
    private static void fortress(Voxels v,int x,int z,int h,int variant) {
        Prims.box(v,x-5,3,z-5,x+5,7,z+5,TRIM);
        for(int y=8;y<=h;y++) {
            Prims.box(v,x-5,y,z-5,x+5,y,z-4,BRICK);Prims.box(v,x-5,y,z+4,x+5,y,z+5,BRICK);
            Prims.box(v,x-5,y,z-5,x-4,y,z+5,BRICK);Prims.box(v,x+4,y,z-5,x+5,y,z+5,BRICK);
            if(y%5==2)Prims.box(v,x-6,y,z-6,x+6,y,z+6,TRIM);
            if(y%5==2)Prims.box(v,x-3,y,z-3,x+3,y,z+3,FireModules.AIR);
        }
        for(int y=9;y<h-1;y++)for(int dx:new int[]{-2,2}) {
            if(y%7<4) {v.setClipped(x+dx,y,z-5,FireModules.AIR);v.setClipped(x+dx,y,z-4,FireModules.AIR);}
            if(y%7<4) {v.setClipped(x-5,y,z+dx,FireModules.AIR);v.setClipped(x-4,y,z+dx,FireModules.AIR);}
        }
        Prims.box(v,x-2,8,z-5,x+2,11,z-4,FireModules.AIR);
        for(int dx=-5;dx<=5;dx+=2)for(int dz:new int[]{-5,5})Prims.column(v,x+dx,h,h+3+(dx==3?variant:0),z+dz,BRICK);
        v.setClipped(x,12,z-4,FireModules.GLOW);
        for(int i=0;i<4;i++) {
            Prims.box(v,x-2,4+i,z-9+i,x+2,4+i,z-9+i,"minecraft:blackstone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]");
            for(int y=3;y<4+i;y++)Prims.box(v,x-2,y,z-9+i,x+2,y,z-9+i,TRIM);
            Prims.box(v,x-2,5+i,z-9+i,x+2,7+i,z-9+i,FireModules.AIR);
        }
    }
    private static void arcade(Voxels v,int x,int z0,int length,int variant) {
        // A cliff-built facade with layered ledges, vaulted openings and an accessible side stair.
        Prims.box(v,x,3,z0,x+5,22,z0+length,BRICK);
        for(int z=z0+3;z<z0+length-3;z+=9) {
            Prims.box(v,x,7,z,x+5,13,z+4,FireModules.AIR);
            Prims.box(v,x,14,z+1,x+5,15,z+3,FireModules.AIR);
            for(int y=6;y<=20;y++) {v.setClipped(x-1,y,z-1,TRIM);v.setClipped(x-1,y,z+5,TRIM);}
            v.setClipped(x-2,10,z-1,FireModules.GLOW);
            Prims.box(v,x,18,z+1,x+5,20,z+3,FireModules.AIR);
        }
        for(int y:new int[]{6,16,22})Prims.box(v,x-2,y,z0-1,x+6,y,z0+length+1,TRIM);
        Prims.box(v,x-5,11,z0-1,x-1,11,z0+length,SLAB);
        for(int z=z0;z<z0+length;z+=3)v.setClipped(x-5,12,z,FENCE);
        for(int z=z0;z<=z0+length;z+=2)Prims.column(v,x,23,25,z,BRICK);
        for(int i=0;i<8;i++) {
            for(int y=3;y<4+i;y++)Prims.box(v,x-9+i,y,z0+length-5,x-9+i,y,z0+length-2,TRIM);
            Prims.box(v,x-9+i,4+i,z0+length-5,x-9+i,4+i,z0+length-2,"minecraft:blackstone_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]");
            Prims.box(v,x-9+i,5+i,z0+length-5,x-9+i,7+i,z0+length-2,FireModules.AIR);
        }
        // The stairwell opening also removes its railing segments; do not leave fence pieces above a cut slab.
        for(int z=z0;z<z0+length;z+=3)if(FENCE.equals(v.get(x-5,12,z))&&FireModules.AIR.equals(v.get(x-5,11,z)))v.set(x-5,12,z,FireModules.AIR);
    }
    private static void gate(Voxels v,int x,int z,int h,boolean broken) {
        for(int dx:new int[]{-7,7}) {
            Prims.box(v,x+dx-1,3,z-2,x+dx+1,3,z+2,TRIM);
            Prims.box(v,x+dx-1,4,z-2,x+dx+1,h,z+2,BRICK);
            for(int y=7;y<h;y+=5)Prims.box(v,x+dx-2,y,z-3,x+dx+2,y,z+3,TRIM);
            v.setClipped(x+dx,8,z-3,FireModules.GLOW);
        }
        for(int dx=-7;dx<=7;dx++) {
            int y=h+3-(int)Math.round(Math.abs(dx)*.6);
            if(!broken||dx<2||dx>4)Prims.box(v,x+dx,y,z-2,x+dx,y+2,z+2,BRICK);
        }
        for(int dx=-7;dx<=7;dx+=2)if(!broken||dx<0) {
            int top=h+3-(int)Math.round(Math.abs(dx)*.6)+2;
            Prims.column(v,x+dx,top+1,top+3,z,TRIM);
        }
    }
    private static void ceilingTeeth(Voxels v,boolean[][][] protectedStructure,int kind,int variant) {
        for(int x=7;x<59;x+=5)for(int z=7;z<59;z+=5) {
            if(Prims.hash(x,variant,z,kind+180)<.45)continue;
            int top=ceiling(kind,variant,x,z),length=3+(int)(Prims.hash(z,kind,x,193)*7);
            for(int y=top-length;y<top;y++) {
                double radius=.4+(y-(top-length))*.22;
                for(int px=(int)Math.floor(x-radius);px<=Math.ceil(x+radius);px++)for(int pz=(int)Math.floor(z-radius);pz<=Math.ceil(z+radius);pz++) {
                    if(v.inBounds(px,y,pz)&&Math.hypot(px-x,pz-z)<=radius&&!protectedStructure[px][y][pz])v.set(px,y,pz,BASALT);
                }
            }
            if((x+z)%3==0&&!protectedStructure[x][top-length][z])v.setClipped(x,top-length,z,FireModules.MAGMA);
        }
    }
}
