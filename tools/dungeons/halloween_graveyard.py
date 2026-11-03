"""The Mourning Court: open-sky 128x128x100 Halloween boss-arena asset, vanilla/server-side only."""
from pathlib import Path
import math,sys,json,hashlib
import numpy as np
KIT=Path(__file__).resolve().parents[1]/'dungeonkit'
if not KIT.exists():KIT=Path('L:/ascensionDungeons/tools/dungeonkit')
sys.path.insert(0,str(KIT))
import kit,shapes as S,check,export
from kit import b,AIR

W=L=128;H=100;F=12
TILE=b('deepslate_tiles');BRICK=b('deepslate_bricks');COBBLE=b('cobbled_deepslate');BLACK=b('polished_blackstone_bricks')
CRY=b('crying_obsidian');AMETHYST=b('amethyst_block');BONE=b('bone_block',axis='y');DARK=b('dark_oak_log',axis='y')
SOUL=kit.lantern(soul=True);SOUL_H=kit.lantern(soul=True,hanging=True)
STONE=b('stone_bricks');MOSS=b('mossy_stone_bricks');CHAIN=kit.chain();BARS=b('iron_bars')

def support(d,x,z,y,material=BRICK,r=0): d.fill(x-r,0,z-r,x+r,y,z+r,material)
def pillar(d,x,z,y,top,r=1):
    support(d,x,z,y,BLACK,r+1);d.fill(x-r,y+1,z-r,x+r,top,z+r,BRICK)
    for h in range(y+4,top,6):d.fill(x-r-1,h,z-r-1,x+r+1,h,z+r+1,BLACK)
    d.fill(x-r-1,top+1,z-r-1,x+r+1,top+1,z+r+1,TILE)

def lamp(d,x,z,y=F,high=5,purple=False):
    support(d,x,z,y,BLACK)
    d.set(x,y+1,z,b('chiseled_deepslate'));d.fill(x,y+2,z,x,y+high-1,z,b('polished_blackstone_wall'))
    d.set(x,y+high,z,CRY if purple else b('pearlescent_froglight',axis='y'))
    # Full-height neighboring posts support the cap; lanterns stand directly on the cap.
    d.set(x,y+high+1,z,BLACK);d.set(x,y+high+2,z,SOUL)
    d.set(x+1,y+high,z,b('purple_stained_glass') if purple else b('blue_stained_glass'))
    d.set(x-1,y+high,z,b('purple_stained_glass') if purple else b('blue_stained_glass'))

def pointed_door(d,cx,z1,z2,bottom,straight,half):
    for y in range(bottom,straight+half*2+1):
        w=half if y<=straight else max(0,half-(y-straight+1)//2)
        d.air(cx-w,y,z1,cx+w,y,z2)

def grave(d,x,z,style):
    # Disturbed graves have full foundations: no sunken unsupported caps or floating headstones.
    support(d,x,z,F,COBBLE,1)
    d.fill(x-1,F+1,z-1,x+1,F+1,z+2,b('coarse_dirt') if style%3 else b('soul_soil'))
    d.fill(x-1,F+1,z-2,x+1,F+2,z-2,BLACK)
    if style%4==0:
        d.fill(x,F+3,z-2,x,F+6,z-2,STONE);d.fill(x-1,F+5,z-2,x+1,F+5,z-2,STONE)
    else:
        d.fill(x,F+3,z-2,x,F+4+style%2,z-2,MOSS if style%2 else STONE)
        d.set(x,F+5+style%2,z-2,kit.slab('stone'))
    if style%3==0:d.set(x,F+2,z+1,b('carved_pumpkin',facing='south'))
    else:d.set(x,F+2,z+2,b('purple_candle' if style%2 else 'blue_candle',candles=2,lit=True,waterlogged=False))
    if style%5==0:d.set(x+1,F+3,z-2,b('skeleton_skull',rotation=0))

def mausoleum(d,x1,z1,x2,z2,h,door=True):
    d.fill(x1,0,z1,x2,F+h,z2,BRICK);d.air(x1+2,F+1,z1+2,x2-2,F+h-2,z2-2)
    for y in (F+3,F+h-1):d.fill(x1-1,y,z1-1,x2+1,y,z2+1,BLACK);d.air(x1+2,y,z1+2,x2-2,y,z2-2)
    d.fill(x1-1,F+h+1,z1-1,x2+1,F+h+1,z2+1,TILE)
    for x in (x1,x2):
        for z in (z1,z2):d.fill(x,F+1,z,x,F+h+2,z,BLACK);d.set(x,F+h+3,z,SOUL)
    if door:pointed_door(d,(x1+x2)//2,z2-1,z2,F+1,F+5,2)
    for x in range(x1+3,x2-1,4):
        d.fill(x,F+1,z1+4,x+1,F+1,z1+7,b('polished_blackstone'))
        d.set(x,F+2,z1+4,b('skeleton_skull',rotation=0))

def chapel(d):
    x1,x2,z1,z2=43,85,9,35
    d.fill(x1,0,z1,x2,38,z2,BRICK);d.air(x1+3,F+1,z1+3,x2-3,37,z2-2)
    for x in range(x1,x2+1):
        roof=39+min(x-x1,x2-x)
        d.fill(x,39,z1,x,roof,z1+1,BRICK);d.fill(x,39,z2-1,x,roof,z2,BRICK)
        d.fill(x,roof,z1-2,x,roof+1,z2+2,TILE)
        if x!=64:d.fill(x,roof+2,z1-2,x,roof+2,z2+2,kit.stair('deepslate_tile','east' if x<64 else 'west'))
    for z in (13,23,33):
        for x in (41,87):pillar(d,x,z,F,39,1)
        for x in (43,84):
            for y in range(23,34):
                d.fill(x,y,z-2,x+1,y,z+2,b('purple_stained_glass') if z!=23 else b('blue_stained_glass'))
            d.fill(x,34,z-1,x+1,35,z+1,b('purple_stained_glass'))
    pointed_door(d,64,33,35,F+1,23,4)
    # Deep Gothic facade bays prevent the mausoleum from reading as an unbroken flat wall.
    for x in (45,57,71,83):
        d.fill(x,F+1,36,x+1,38,37,BLACK)
        for y in (22,31,38):d.fill(x-1,y,36,x+2,y,38,TILE)
        d.set(x,40,37,SOUL)
        d.set(x,39,37,BLACK)
    for cx in (51,77):
        d.fill(cx-4,22,35,cx+4,39,36,BLACK)
        for y in range(24,38):
            w=2 if y<34 else max(0,2-(y-34)//2)
            d.fill(cx-w,y,35,cx+w,y,35,b('pearlescent_froglight',axis='z'))
            d.fill(cx-w,y,36,cx+w,y,36,b('purple_stained_glass'))
        d.fill(cx,24,37,cx,35,37,b('polished_blackstone_wall'))
    for x in range(59,70):
        for y in range(32,42):
            r=math.hypot(x-64,y-37)
            if r<4.5:d.set(x,y,35,CRY);d.set(x,y,36,b('blue_stained_glass') if (x+y)%2 else b('purple_stained_glass'))
            if r<3.5 and (x==64 or y==37):d.set(x,y,37,BLACK)
    # Raised altar, reached via three full-width supported steps; facing north into the chapel.
    d.fill(55,0,13,73,15,28,TILE)
    for step in range(1,4):
        z=32-step;d.fill(59,0,z,69,F+step,z,BLACK)
        d.fill(59,F+step,z,69,F+step,z,kit.stair('polished_blackstone','north'))
    d.fill(60,16,17,68,18,20,CRY);d.fill(59,19,16,69,19,21,BLACK)
    for x in (57,71):support(d,x,16,19,BLACK);d.fill(x,20,16,x,26,16,AMETHYST);d.set(x,27,16,b('end_rod',facing='up'))
    d.add_chest((64,16,14),'south')
    # The skull is bonded to the front gable; eyes are luminous blocks behind stained glass.
    d.fill(59,43,33,69,51,36,BONE);d.fill(60,41,33,68,42,36,BONE)
    for ex in (60,66):d.fill(ex,47,35,ex+2,49,35,b('pearlescent_froglight',axis='z'));d.fill(ex,47,36,ex+2,49,36,b('purple_stained_glass'))
    d.fill(64,44,36,64,46,36,b('black_concrete'))
    for x in (61,63,65,67):d.set(x,42,36,b('black_concrete'))
    for z in (18,27):
        d.fill(64,32,z,64,59,z,CHAIN)
        for x in range(61,68):
            for zz in range(z-3,z+4):
                if x in (61,67) or zz in (z-3,z+3) or x==64 or zz==z:d.set(x,31,zz,BARS)
        for x in (61,67):
            for zz in (z-3,z+3):d.set(x,30,zz,SOUL_H)

def bell_tower(d):
    cx,cz=104,22
    d.fill(97,0,15,111,62,29,BRICK);d.air(100,F+1,18,108,61,26)
    for y in (F,25,39,50,62):
        d.fill(95,y,13,113,y,31,BLACK)
        if y>F:d.air(100,y,18,108,y,26)
    for x in (97,111):
        for z in (15,29):d.fill(x-1,F+1,z-1,x+1,64,z+1,BLACK)
    for z in (15,28):
        d.air(101,51,z,107,59,z+1);d.fill(103,60,z,105,60,z+1,b('blue_stained_glass'))
    d.fill(101,49,19,107,49,25,BONE);d.fill(102,50,20,106,55,24,b('gold_block'));d.fill(103,56,21,105,58,23,BONE)
    d.fill(104,59,22,104,63,22,CHAIN)
    for level in range(19):
        r=max(0,8-level//2);d.fill(cx-r,64+level,cz-r,cx+r,64+level,cz+r,TILE)
    d.set(104,83,22,b('end_rod',facing='up'))
    for x in (96,112):
        for z in (14,30):d.fill(x,63,z,x,69,z,b('polished_blackstone_wall'));d.set(x,70,z,SOUL)
    pointed_door(d,104,26,29,F+1,F+5,2)

def tree(d,x,z,h):
    d.fill(x-1,0,z-1,x+1,F+h,z+1,DARK)
    for dx,dz,dh in ((1,0,0),(-1,0,4),(0,1,2),(0,-1,5)):
        yy=F+h-9+dh
        for k in range(1,8):
            px,pz=x+dx*k,z+dz*k
            d.fill(px-dx,yy,pz-dz,px,yy,pz,DARK)
            if k%2: d.set(px,yy+1,pz,DARK);yy+=1
        d.fill(px,yy-4,pz,px,yy-1,pz,CHAIN);d.set(px,yy-5,pz,SOUL_H)
    for dx,dz in ((2,0),(-2,0),(0,2),(0,-2)):d.fill(x,F,z,x+dx,F+1,z+dz,DARK)

def entry_gate(d):
    # Three nested pointed arches; all jambs and pinnacles have solid ground-to-cap support.
    for z,material in ((112,BLACK),(113,BRICK),(114,TILE),(115,BLACK)):
        for y in range(F+1,49):
            outer=16 if y<=29 else max(2,16-(y-29)//2)
            d.fill(64-outer,y,z,64+outer,y,z,material)
        pointed_door(d,64,z,z,F+1,27,7)
    for x in (47,81):
        pillar(d,x,114,F,39,1);d.fill(x,41,114,x,47,114,b('polished_blackstone_wall'));d.set(x,48,114,SOUL)
    d.fill(63,43,113,65,45,114,CRY)

def resolve_connections(d):
    # The generator places literal states, so author fence/bar connections rather than relying on neighbor updates.
    snapshot=list(d.pal)
    for y,z,x in zip(*np.nonzero(np.array([kit.base(p).endswith('_wall') or kit.base(p)=='iron_bars' for p in snapshot])[d.a])):
        state=d.get(int(x),int(y),int(z));name=kit.base(state);p={'waterlogged':False}
        if name.endswith('_wall'):p['up']=True
        for direction,(dx,dz) in kit.FACINGS.items():
            neighbor=d.get(int(x)+dx,int(y),int(z)+dz)
            connects=neighbor is not None and (kit.base(neighbor)==name or (not kit.nonsolid(neighbor) and kit.base(neighbor) not in kit.LIQUID))
            p[direction]=('low' if connects else 'none') if name.endswith('_wall') else connects
        d.set(int(x),int(y),int(z),b(name,**p))

def build():
    d=kit.Dungeon('halloween_graveyard','The Mourning Court',128,100,128,biome='minecraft:dark_forest')
    d.loot_table='ascensiondungeons:chests/crimson_cathedral'
    d.fill(0,0,0,127,F,127,COBBLE)
    # Low cemetery boundary: three solid blocks above ground and a half-slab coping. No global roof.
    for x1,z1,x2,z2 in ((0,0,2,127),(125,0,127,127),(0,0,127,2),(0,125,127,127)):
        d.fill(x1,F+1,z1,x2,F+3,z2,BRICK)
        d.fill(x1,F+4,z1,x2,F+4,z2,kit.slab('deepslate_tile'))
    for x in range(128):
        for z in range(128):
            if 4<=x<124 and 4<=z<124:
                h=(x*734287+z*912931)&255
                floor=kit.b('podzol') if h<70 else b('coarse_dirt') if h<132 else MOSS if h<178 else COBBLE if h<225 else BRICK
                d.set(x,F,z,floor)
    # Scuffed processional paths and a broad clear combat court, not a paved cross through four quadrants.
    for x in range(8,120):
        for z in range(7,121):
            radius=math.hypot(x-64,z-64)
            main=abs(x-64)<=4 and z>=35
            loop=23<radius<28
            if radius<21 or main or loop:d.set(x,F,z,TILE if (x+2*z)%9 else BLACK)
            if 20<=radius<=22 and (x+z)%4==0:d.set(x,F,z,CRY if (x+z)%8 else AMETHYST)
    for z in range(7,122):
        for x in (7,120):
            d.fill(x,F+1,z,x,F+3,z,BRICK)
            if z%16==0:d.fill(x-1,F+1,z,x+1,F+5,z,BLACK);d.set(x,F+6,z,SOUL)
            d.set(x,F+4,z,kit.slab('deepslate_tile'))
    # Grave plots are interrupted by aisles, never placed in the central fighting envelope.
    count=0
    for x in (18,26,34,42,86,94,102,110):
        for z in (43,52,61,70,79,88):grave(d,x,z,count);count+=1
    for x in (18,26,34,94,102,110):grave(d,x,97,count);count+=1
    for x in (13,44,84,115):
        for z in range(38,94):
            if any(abs(z-gap)<=2 for gap in (47,68,89)):continue
            d.set(x,F+1,z,b('polished_blackstone_wall'))
    for x in (42,86):
        for z in (42,64,86,105):lamp(d,x,z,purple=(z%3==0))
    for x,z in ((21,38),(105,38),(16,101),(112,98)):lamp(d,x,z,high=4,purple=True)
    mausoleum(d,12,14,35,32,15)
    chapel(d);bell_tower(d);entry_gate(d)
    tree(d,23,109,24);tree(d,106,108,22)
    for x in (33,91):
        S.statue(d,x,33,F+1,BLACK,b('chiseled_deepslate'),eye=b('pearlescent_froglight',axis='y'),face='s')
    for x,z in ((18,102),(30,105),(100,103),(111,111),(43,99),(85,99),(50,111),(78,111)):
        d.set(x,F+1,z,b('carved_pumpkin',facing='south'));d.set(x+1,F+1,z,b('carved_pumpkin',facing='west'))
    # Grounded obelisks leave the full radius-20 battle envelope unobstructed.
    for x,z in ((39,46),(89,46),(39,84),(89,84)):
        pillar(d,x,z,F,24,1);d.set(x,26,z,CRY);d.set(x,27,z,b('end_rod',facing='up'))
    for x,z in ((10,17),(37,17),(91,13),(115,31),(43,116),(85,116)):
        d.set(x,F+1,z,b('purple_candle',candles=4,lit=True,waterlogged=False))
    # No custom mob or loot/reward policy: semantic markers reserve those decisions for the mod.
    d.entry=(64,F+1,119)
    d.room('arena','The Mourning Court',(5,F+1,5),(122,86,122))
    for name,pos in {'boss_anchor':(64,F+1,64),'party_staging':(64,F+1,103),'exit':(64,F+1,119),
                     'west_crypt':(24,F+1,29),'chapel_altar':(64,16,24),'bell_tower':(104,F+1,27)}.items():d.mark(name,pos)
    for i,(x,z) in enumerate(((28,29),(106,26),(16,91),(112,87))):
        if i>1:d.fill(x-1,F,z-1,x+1,F,z+1,BLACK)
        d.add_chest((x,F+1,z),'south')
    for name,mn,mx in [('skull_mausoleum',(40,F,7),(89,64,38)),('hollow_bell_tower',(94,F,12),(115,85,33)),
                       ('mourning_gate',(46,F,111),(82,49,116)),('west_reliquary',(10,F,12),(37,31,34)),
                       ('lantern_trees',(14,F,99),(116,44,119))]:d.landmark(name,name.replace('_',' ').title(),'focal',mn,mx)
    d.completion={'intro':'Cold lanterns watch the open graves. The Mourning Court awaits.'}
    d.grave_count=count
    resolve_connections(d)
    return d

def main():
    out=Path(sys.argv[1] if len(sys.argv)>1 else 'outputs/halloween-graveyard-open');out.mkdir(parents=True,exist_ok=True)
    d=build();model=check.Model(d);reached=check.reach_all(d,model)
    errors=[]
    for name in ('boss_anchor','party_staging','exit','west_crypt','chapel_altar','bell_tower'):
        pos=tuple(d.markers[name]);ok=pos in reached
        if not ok:errors.append('Unreachable '+name+' '+str(pos))
    # Keep the combat zone genuinely open, using the final composed array rather than a pre-decoration mask.
    for x in range(44,85):
        for z in range(44,85):
            if math.hypot(x-64,z-64)<20:
                for y in range(F+1,F+9):
                    if not kit.is_air(d.get(x,y,z)):errors.append('Combat obstruction '+str((x,y,z)));break
    for c in d.chests:
        if not check.near(model,check.box_of(c['pos']),reached,2):errors.append('Unreachable chest '+str(c['pos']))
    # Attachment/foundation checks focus on all non-geological solid components.
    solid=np.array([(not kit.nonsolid(p) and kit.base(p) not in kit.LIQUID) or kit.base(p)=='chain' for p in d.pal])[d.a]
    attached=np.zeros_like(solid);attached[0]=solid[0]
    while True:
        grown=attached.copy()
        grown[1:]|=attached[:-1];grown[:-1]|=attached[1:]
        grown[:,1:]|=attached[:,:-1];grown[:,:-1]|=attached[:,1:]
        grown[:,:,1:]|=attached[:,:,:-1];grown[:,:,:-1]|=attached[:,:,1:]
        grown&=solid
        if np.array_equal(grown,attached):break
        attached=grown
    detached=[]
    for y,z,x in np.argwhere(solid&~attached):
        state=d.get(int(x),int(y),int(z))
        if kit.base(state) not in ('skeleton_skull',):detached.append((int(x),int(y),int(z),state))
    if detached:errors.append('Detached solid components '+str(detached[:8]))
    if not solid[0].all():errors.append('Arena foundation is not continuous')
    if not all((solid[F+1:F+4,:,0].all(),solid[F+1:F+4,:,-1].all(),solid[F+1:F+4,0,:].all(),solid[F+1:F+4,-1,:].all())):errors.append('Low perimeter wall is incomplete')
    if not np.all(d.a[-1]==d.pid(AIR)):errors.append('Top boundary must be open sky')
    for x in range(44,85):
        for z in range(44,85):
            if math.hypot(x-64,z-64)<20 and not np.all(d.a[F+1:,z,x]==d.pid(AIR)):errors.append('Combat court has an overhead obstruction '+str((x,z)))
    for y,z,x in zip(*np.nonzero(np.array([kit.base(p) in ('lantern','soul_lantern') for p in d.pal])[d.a])):
        above=d.get(int(x),int(y)+1,int(z));below=d.get(int(x),int(y)-1,int(z));hanging=kit.props(d.get(int(x),int(y),int(z))).get('hanging')=='true'
        attached=above if hanging else below
        if attached is None or kit.is_air(attached):errors.append('Unattached lantern '+str((int(x),int(y),int(z))))
    if errors:
        (out/'validation-failures.json').write_text(json.dumps(errors,indent=2));raise RuntimeError('\n'.join(errors[:15]))
    count=d.write_schem(str(out/'dungeon.schem'))
    data=export.layout(d,origin=(0,0,0));data['lights']=[];data['lightLevel']=8
    (out/'layout.json').write_text(json.dumps(data,indent=1),encoding='utf-8')
    report={'size':[128,100,128],'graves':d.grave_count,'palette':count,'reachable_standing_cells':len(reached),
            'combat_clearance_radius':20,'combat_clearance_height':87,'open_sky':True,'perimeter_wall_height':3,
            'detached_masonry_components':0,'validation_errors':errors,
            'boss_binding':'reserved marker only; no boss assigned','schematic_sha256':hashlib.sha256((out/'dungeon.schem').read_bytes()).hexdigest()}
    (out/'validation.json').write_text(json.dumps(report,indent=2))
    (out/'palette.txt').write_text('\n'.join(sorted(set(d.pal))))
    import csv
    totals={}
    counts=np.bincount(d.a.reshape(-1),minlength=len(d.pal))
    for i,state in enumerate(d.pal):
        if kit.is_air(state):continue
        name=state.split('[')[0];totals[name]=totals.get(name,0)+int(counts[i])
    with (out/'materials.csv').open('w',newline='',encoding='utf-8') as f:
        writer=csv.writer(f);writer.writerow(['block_id','placed_blocks']);writer.writerows(sorted(totals.items()))
    (out/'validation-failures.json').unlink(missing_ok=True)
    print(json.dumps(report,indent=2))

if __name__=='__main__':main()
