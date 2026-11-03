package com.ascensiondungeons.mc;

import com.ascensiondungeons.AscensionDungeonsLog;
import com.ascensiondungeons.gen.cinderforge.CinderforgeLayout;
import com.ascensiondungeons.gen.cinderforge.CinderforgeLayout.Box;
import com.ascensiondungeons.run.Run;
import com.ascensiondungeons.run.RunManager;
import com.ascensiondungeons.run.RunState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Runs the Cinderforge mechanics of every active Cinderforge run: the three-lever cistern drain, the Forge Key and the
 * locked Barracks door, the hazards (causeway geysers, smelting-works furnaces, the rising lava in the spiral), the
 * room encounters, the Forge Overseer, the Forge Heart and the lift. Everything uses vanilla blocks, entities, boss bars
 * and sounds, so no client install is needed. All methods run on the server thread.
 */
public final class CinderforgeDirector implements ArenaDirector {
    private final MinecraftServer server;
    private final Supplier<RunManager> manager;
    private final FantasyRunHost host;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public CinderforgeDirector(MinecraftServer server, Supplier<RunManager> manager, FantasyRunHost host) {
        this.server = server;
        this.manager = manager;
        this.host = host;
    }

    @Override
    public void tick(long now) {
        RunManager m = manager.get();
        if (m == null) return;
        for (Run run : m.runs()) {
            if (!RunManager.CINDERFORGE.equals(run.dungeonType()) || run.state() != RunState.ACTIVE) continue;
            Session s = sessions.get(run.id());
            if (s == null) {
                var view = host.cinderforgeView(run.id());
                if (view.isEmpty()) continue;
                s = new Session(run, view.get().level(), view.get().generator());
                sessions.put(run.id(), s);
            }
            try { s.tick(now); }
            catch (RuntimeException e) { AscensionDungeonsLog.LOG.error("Cinderforge session tick failed for run {}", run.id(), e); }
        }
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            if (m.get(id).map(r -> r.state() != RunState.ACTIVE).orElse(true)) runReleased(id);
        }
    }

    @Override
    public void runReleased(UUID runId) {
        Session s = sessions.remove(runId);
        if (s != null) s.close();
    }

    /** The session whose level this is, or null if it is not a live Cinderforge run level. */
    public Session sessionFor(Level level) {
        for (Session s : sessions.values()) if (s.level == level) return s;
        return null;
    }

    /** A living thing died somewhere; if it was one of a run's bosses, the run reacts. */
    public void onDeath(LivingEntity entity) {
        Session s = sessionFor(entity.level());
        if (s != null) s.onDeath(entity);
    }

    // ===========================================================================================================

    /** One run's mutable mechanics state. */
    public final class Session {
        // ---- tunables (ticks unless stated) ----------------------------------------------------------------
        static final int GEYSER_PERIOD = 120, GEYSER_WARN = 50, GEYSER_BLAST = 14;
        static final int FURNACE_CYCLE = 100, FURNACE_ON = 50;
        static final int LAVA_RISE_TICKS_PER_BLOCK = 30, LAVA_HEAD_START_TICKS = 200;
        static final int VENTS_TO_COOL = 3, WINDOW_TICKS = 360;
        static final int COMPLETE_DELAY_TICKS = 20 * 45, LIFT_CHARGE_TICKS = 60;

        final Run run;
        final ServerLevel level;
        final CinderforgeChunkGenerator gen;
        final CinderforgeLayout layout;
        final Random rng;
        /** Highest cistern lever pulled so far (0 to 3). */
        int stage;
        boolean eastOpen, drained;
        private long now;
        private record Task(long at, long seq, Runnable action) {}
        private final PriorityQueue<Task> tasks = new PriorityQueue<>(java.util.Comparator.comparingLong(Task::at).thenComparingLong(Task::seq));
        private long seq;
        private final Set<String> activated = new HashSet<>();

        // R7 rising lava
        long r7Start = -1;
        // Overseer
        Mob overseer; ServerBossEvent overseerBar; boolean overseerDead; int overseerPhase = -1;
        // Forge Heart
        Mob heart; ServerBossEvent heartBar; boolean heartDead, heartShielded = true; int cooled; long windowEnds = -1;
        final boolean[] ventCool;
        // lift / completion
        boolean liftOpen; long completeAt = -1;
        final Map<UUID, Integer> liftCharge = new HashMap<>();
        int partySizeAtStart = 1;

        Session(Run run, ServerLevel level, CinderforgeChunkGenerator gen) {
            this.run = run;
            this.level = level;
            this.gen = gen;
            this.layout = gen.layout();
            this.rng = new Random(run.seed() ^ 0x4349_4e44L);
            this.ventCool = new boolean[layout.data().heatVents.length];
        }

        public Run run() { return run; }
        public ServerLevel level() { return level; }
        public CinderforgeChunkGenerator generator() { return gen; }
        public int stage() { return stage; }
        public boolean overseerDead() { return overseerDead; }
        public boolean heartDead() { return heartDead; }

        void close() {
            tasks.clear();
            if (overseerBar != null) overseerBar.removeAllPlayers();
            if (heartBar != null) heartBar.removeAllPlayers();
        }

        void later(int delayTicks, Runnable action) { tasks.add(new Task(now + delayTicks, seq++, action)); }

        // ---- the tick ----------------------------------------------------------------------------------------

        void tick(long tickNow) {
            boolean first = now == 0;
            now = tickNow;
            while (!tasks.isEmpty() && tasks.peek().at <= now) tasks.poll().action.run();
            List<ServerPlayer> players = players();
            if (first) {
                partySizeAtStart = Math.max(1, players.size());
                say("The Cinderforge Bastion. Cross the causeway and reach the Forge Heart.", ChatFormatting.GOLD);
            }
            if (players.isEmpty()) return;
            if (now % 10 == 0) activateRooms(players);
            geysers(players);
            furnaces(players);
            risingLava(players);
            bosses(players);
            lift(players);
            if (completeAt > 0) completionCountdown();
        }

        List<ServerPlayer> players() {
            List<ServerPlayer> out = new ArrayList<>();
            for (ServerPlayer p : level.players()) if (p.isAlive() && !p.isSpectator()) out.add(p);
            return out;
        }

        // ---- helpers ---------------------------------------------------------------------------------------

        BlockPos world(int x, int y, int z) { return gen.toWorld(new int[] {x, y, z}); }
        BlockPos world(int[] c) { return gen.toWorld(c); }
        int[] schem(Entity e) { return CinderforgeChunkGenerator.toSchematic(e.blockPosition()); }
        double healthFactor() { return 1.0 + 0.6 * (partySizeAtStart - 1); }

        void say(String text, ChatFormatting colour) {
            for (ServerPlayer p : level.players()) p.sendSystemMessage(Component.literal(text).withStyle(colour));
        }

        void sound(BlockPos pos, net.minecraft.sounds.SoundEvent ev, float vol, float pitch) {
            level.playSound(null, pos, ev, SoundSource.BLOCKS, vol, pitch);
        }

        /** Sets every cell of the box that satisfies the filter to air. Flag 2 only: no neighbour updates, so lava
         *  next to a freshly emptied cell never spawns flowing lava. */
        int clear(Box b, java.util.function.Predicate<BlockState> filter, int yMin, int yMax) {
            int n = 0;
            BlockState air = Blocks.AIR.defaultBlockState();
            for (int x = b.min[0]; x <= b.max[0]; x++)
                for (int y = Math.max(yMin, b.min[1]); y <= Math.min(yMax, b.max[1]); y++)
                    for (int z = b.min[2]; z <= b.max[2]; z++) {
                        BlockPos p = world(x, y, z);
                        if (filter.test(level.getBlockState(p))) { level.setBlock(p, air, 2); n++; }
                    }
            return n;
        }

        int clearAll(Box b) { return clear(b, s -> !s.isAir(), Integer.MIN_VALUE, Integer.MAX_VALUE); }
        int clearLava(Box b, int yMin, int yMax) { return clear(b, s -> s.is(Blocks.LAVA), yMin, yMax); }

        void fill(Box b, BlockState state) {
            for (int x = b.min[0]; x <= b.max[0]; x++)
                for (int y = b.min[1]; y <= b.max[1]; y++)
                    for (int z = b.min[2]; z <= b.max[2]; z++) level.setBlock(world(x, y, z), state, 2);
        }

        boolean inBox(Box b, Entity e) {
            int[] s = schem(e);
            return b.contains(s[0], s[1], s[2]);
        }

        void hurtFire(LivingEntity e, float amount, int fireTicks) {
            e.hurt(level.damageSources().inFire(), amount);
            e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), fireTicks));
        }

        // ---- the cistern ---------------------------------------------------------------------------------------

        /** A lever was pulled (the vanilla toggle has already been allowed). Starts that stage's drain. */
        void leverPulled(int leverStage) {
            stage = leverStage;
            Box res = layout.data().reservoir;
            int top = res.max[1];
            // stage 1: the top two layers, stage 2: the next two, stage 3: everything left.
            int hi = leverStage == 1 ? top : leverStage == 2 ? top - 2 : top - 4;
            int lo = leverStage == 1 ? top - 1 : leverStage == 2 ? top - 3 : Integer.MIN_VALUE;
            say(leverStage == 3 ? "The last mechanism turns. The cistern drains completely..."
                    : "Stone grinds somewhere below. The lava level drops.", ChatFormatting.GOLD);
            BlockPos centre = world((res.min[0] + res.max[0]) / 2, top, (res.min[2] + res.max[2]) / 2);
            level.playSound(null, centre, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 2f, 0.6f);
            int delay = 10;   // one layer every 8 ticks so the level visibly sinks
            for (int y = hi; y >= Math.max(lo, res.min[1]); y--) {
                final int layer = y;
                later(delay, () -> {
                    clearLava(res, layer, layer);
                    level.playSound(null, centre, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.5f, 0.8f);
                    level.sendParticles(ParticleTypes.LAVA, centre.getX() + .5, world(0, layer, 0).getY() + 1, centre.getZ() + .5, 18, 5, 0.2, 5, 0.1);
                });
                delay += 8;
            }
            if (leverStage == 3) later(delay + 20, this::openDrainedPassages);
        }

        /** After the final drain: the tunnel to the Reliquary, the reservoir stairs, and the causeway's trench. */
        private void openDrainedPassages() {
            drained = true;
            clearAll(layout.data().tunnelPlug);
            clearAll(layout.data().descentGate);
            Box tp = layout.data().tunnelPlug;
            level.playSound(null, world(tp.min[0], tp.min[1], tp.min[2]), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 2f, 0.7f);
            say("A hidden passage opens at the foot of the cistern, and the stairs below are free.", ChatFormatting.AQUA);
            Box trench = layout.data().trench;
            int delay = 20;
            for (int y = trench.max[1]; y >= trench.min[1]; y--) {
                final int layer = y;
                later(delay, () -> clearLava(trench, layer, layer));
                delay += 8;
            }
            later(delay + 10, () -> {
                for (Box b : layout.data().trenchPlugs) clearAll(b);
                for (Box b : layout.data().hiddenDoors) clearAll(b);
                say("Far across the causeway, a drain gate grinds open beneath the pylons.", ChatFormatting.AQUA);
            });
        }

        // ---- the Forge Key and the Barracks door ---------------------------------------------------------------

        /** Returns true if the click was handled (and should be consumed). */
        boolean useEastPlug(ServerPlayer player, ItemStack held) {
            if (eastOpen) return true;
            if (!CinderforgeItems.isForgeKey(held)) {
                player.sendSystemMessage(Component.literal("A heavy sealed door. It has a keyhole shaped like a Forge Key.").withStyle(ChatFormatting.GRAY));
                return true;
            }
            held.shrink(1);
            eastOpen = true;
            Box plug = layout.data().eastPlug;
            clearAll(plug);
            BlockPos at = world(plug.min[0], plug.min[1], plug.min[2]);
            level.playSound(null, at, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.5f, 0.6f);
            level.playSound(null, at, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 2f, 0.6f);
            say(player.getName().getString() + " unlocks the sealed door of the Barracks.", ChatFormatting.GOLD);
            return true;
        }

        // ---- room encounters -----------------------------------------------------------------------------------

        private void activateRooms(List<ServerPlayer> players) {
            for (ServerPlayer p : players) {
                int[] s = schem(p);
                CinderforgeLayout.Room room = layout.roomAt(s[0], s[1], s[2]);
                if (room == null || !activated.add(room.id)) continue;
                try { populate(room, p); }
                catch (RuntimeException e) { AscensionDungeonsLog.LOG.error("Cinderforge encounter {} failed", room.id, e); }
            }
        }

        private void populate(CinderforgeLayout.Room room, ServerPlayer first) {
            // Peaceful removes hostile mobs at once, so the encounters (and their bosses) would only flicker. Skip them.
            boolean peaceful = level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL;
            if (peaceful && !room.id.equals("R7")) return;
            switch (room.id) {
                case "R2" -> { spawnGroup(room, EntityType.BLAZE, 3, first, "Cinder Wisp"); spawnGroup(room, EntityType.WITHER_SKELETON, 3, first, null); }
                case "R3" -> { spawnGroup(room, EntityType.PIGLIN_BRUTE, 4, first, "Barracks Brute"); spawnGroup(room, EntityType.WITHER_SKELETON, 3, first, null); }
                case "R4" -> { spawnGroup(room, EntityType.BLAZE, 3, first, "Cinder Wisp"); spawnGroup(room, EntityType.MAGMA_CUBE, 4, first, null); }
                case "R5" -> spawnGroup(room, EntityType.WITHER_SKELETON, 4, first, "Relic Guardian");
                case "R6" -> spawnOverseer();
                case "R7" -> { if (!peaceful) spawnGroup(room, EntityType.BLAZE, 3, first, "Cinder Wisp"); startRisingLava(); }
                case "R8" -> spawnHeart();
                default -> { }
            }
        }

        /** Spawn {@code count} mobs on pre-validated standing cells of a room, away from the players. */
        private void spawnGroup(CinderforgeLayout.Room room, EntityType<? extends Mob> type, int count, ServerPlayer near, String name) {
            int[][] cells = room.spawnCells;
            if (cells.length == 0) return;
            int placed = 0;
            for (int tries = 0; tries < count * 12 && placed < count; tries++) {
                int[] c = cells[rng.nextInt(cells.length)];
                Vec3 pos = Vec3.atBottomCenterOf(world(c));
                boolean tooClose = false;
                for (ServerPlayer p : players()) if (p.position().distanceToSqr(pos) < 7 * 7) { tooClose = true; break; }
                if (tooClose) continue;
                Mob mob = spawn(type, c, 1.0, name);
                if (mob != null) placed++;
            }
        }

        <T extends Mob> T spawn(EntityType<T> type, int[] cell, double healthMul, String name) {
            T mob = type.create(level);
            if (mob == null) return null;
            BlockPos p = world(cell);
            mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, rng.nextFloat() * 360f, 0f);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
            if (mob instanceof PiglinBrute brute) brute.setImmuneToZombification(true);
            if (mob instanceof MagmaCube cube) cube.setSize(2, true);
            mob.setPersistenceRequired();
            double factor = healthMul * healthFactor();
            if (factor != 1.0) {
                var attr = mob.getAttribute(Attributes.MAX_HEALTH);
                attr.setBaseValue(attr.getBaseValue() * factor);
                mob.setHealth(mob.getMaxHealth());
            }
            if (name != null) { mob.setCustomName(Component.literal(name)); mob.setCustomNameVisible(true); }
            level.addFreshEntityWithPassengers(mob);
            return mob;
        }

        // ---- the causeway geysers -----------------------------------------------------------------------------

        private void geysers(List<ServerPlayer> players) {
            int[][] g = layout.data().geysers;
            for (int i = 0; i < g.length; i++) {
                BlockPos base = world(g[i]);
                boolean near = false;
                for (ServerPlayer p : players) if (p.distanceToSqr(base.getX() + .5, base.getY(), base.getZ() + .5) < 40 * 40) { near = true; break; }
                if (!near) continue;
                long phase = (now + i * 31L) % GEYSER_PERIOD;
                double x = base.getX() + 0.5, y = base.getY() + 1.0, z = base.getZ() + 0.5;
                if (phase >= GEYSER_PERIOD - GEYSER_WARN - GEYSER_BLAST && phase < GEYSER_PERIOD - GEYSER_BLAST) {
                    if (now % 4 == 0) level.sendParticles(ParticleTypes.SMOKE, x, y, z, 3, 0.25, 0.1, 0.25, 0.01);
                    if (phase == GEYSER_PERIOD - GEYSER_BLAST - 1) level.playSound(null, base, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1f, 0.5f);
                } else if (phase >= GEYSER_PERIOD - GEYSER_BLAST) {
                    level.sendParticles(ParticleTypes.FLAME, x, y + 1, z, 6, 0.25, 1.4, 0.25, 0.02);
                    level.sendParticles(ParticleTypes.LAVA, x, y + 2, z, 2, 0.3, 1.0, 0.3, 0.0);
                    if (phase == GEYSER_PERIOD - GEYSER_BLAST) level.playSound(null, base, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6f, 1.4f);
                    // damage lands once, on the first tick of the eruption; the rest of the blast is only visual
                    if (phase == GEYSER_PERIOD - GEYSER_BLAST)
                        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(x - 1.1, y - 0.2, z - 1.1, x + 1.1, y + 4, z + 1.1))) {
                            if (e.isInvulnerable()) continue;
                            hurtFire(e, 3f, 40);
                            e.push(0, 0.5, 0);
                            e.hurtMarked = true;
                        }
                }
            }
        }

        // ---- the Smelting Works furnaces ----------------------------------------------------------------------

        private void furnaces(List<ServerPlayer> players) {
            if (overseer == null || overseerDead || !overseer.isAlive()) return;
            CinderforgeLayout.Furnace[] f = layout.data().furnaces;
            int active = Math.min(f.length - 1, Math.max(0, overseerPhase));
            long phase = now % FURNACE_CYCLE;
            for (int i = 0; i < f.length; i++) {
                if (i != active) continue;
                BlockPos mouth = world(f[i].mouth);
                double x = mouth.getX() + 0.5, y = mouth.getY() + 0.5, z = mouth.getZ() + 1.0;
                if (phase < FURNACE_ON) {
                    level.sendParticles(ParticleTypes.FLAME, x, y, z + 2.5, 8, 0.7, 0.6, 2.4, 0.01);
                    if (phase == 0) level.playSound(null, mouth, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 2f, 0.5f);
                    if (phase % 5 == 0)
                        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(x - 1.7, y - 2.0, z, x + 1.7, y + 2.5, z + 7.5))) {
                            if (e instanceof ServerPlayer) hurtFire(e, 1.5f, 40);
                        }
                } else if (phase == FURNACE_ON + 15) {
                    level.sendParticles(ParticleTypes.SMOKE, x, y, z + 1, 10, 0.6, 0.5, 0.8, 0.02);
                }
            }
        }

        // ---- the Obsidian Spiral ------------------------------------------------------------------------------

        private void startRisingLava() {
            if (r7Start >= 0) return;
            r7Start = now;
            say("Heat floods the shaft from below. Climb!", ChatFormatting.RED);
        }

        /** The level in the well, as a block Y; players below it are scorched. Damage-based: no lava is placed. */
        int lavaLevelY() {
            var r7 = layout.data().r7;
            long t = Math.max(0, now - r7Start - LAVA_HEAD_START_TICKS);
            return Math.min(r7.topY, r7.floorY - 1 + (int) (t / LAVA_RISE_TICKS_PER_BLOCK));
        }

        private void risingLava(List<ServerPlayer> players) {
            if (r7Start < 0) return;
            var r7 = layout.data().r7;
            BlockPos centre = world(r7.centre);
            int levelY = lavaLevelY() + ORIGIN_Y();
            boolean any = false;
            for (ServerPlayer p : players) {
                double dx = p.getX() - (centre.getX() + 0.5), dz = p.getZ() - (centre.getZ() + 0.5);
                if (Math.hypot(dx, dz) > r7.radius + 2 || p.getY() > r7.topY + ORIGIN_Y() + 3 || p.getY() < r7.floorY + ORIGIN_Y() - 2) continue;
                any = true;
                if (p.getY() < levelY + 1 && now % 10 == 0) hurtFire(p, 2f, 80);
            }
            if (any && now % 5 == 0) {
                level.sendParticles(ParticleTypes.LAVA, centre.getX() + 0.5, levelY + 0.5, centre.getZ() + 0.5, 6, 3.0, 0.1, 3.0, 0.0);
                level.sendParticles(ParticleTypes.FLAME, centre.getX() + 0.5, levelY + 1.0, centre.getZ() + 0.5, 8, 4.0, 0.3, 4.0, 0.01);
            }
        }

        private int ORIGIN_Y() { return CinderforgeLayout.ORIGIN_Y; }

        // ---- the Forge Overseer --------------------------------------------------------------------------------

        private void spawnOverseer() {
            if (overseer != null) return;
            Mob m = spawn(EntityType.WITHER_SKELETON, layout.data().overseerSpawn, 9.0, "Forge Overseer");
            if (m == null) return;
            m.getAttribute(Attributes.SCALE).setBaseValue(1.6);
            m.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1);   // the netherite sword adds 7: about 8 a hit, not a one-shot
            m.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.8);
            m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
            m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
            m.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
            m.setDropChance(EquipmentSlot.MAINHAND, 0f);
            m.setDropChance(EquipmentSlot.HEAD, 0f);
            m.setDropChance(EquipmentSlot.CHEST, 0f);
            overseer = m;
            overseerBar = new ServerBossEvent(Component.literal("Forge Overseer"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_6);
            say("The Forge Overseer rises from the slag. The furnaces roar to life.", ChatFormatting.RED);
        }

        private void bosses(List<ServerPlayer> players) {
            if (overseer != null && !overseerDead) {
                if (!overseer.isAlive()) {
                    if (defeated(overseer)) overseerDied();
                    else { overseer = null; activated.remove("R6"); }   // removed some other way: not a win, spawn again on the next visit
                }
                else {
                    float ratio = overseer.getHealth() / overseer.getMaxHealth();
                    overseerBar.setProgress(Math.max(0f, Math.min(1f, ratio)));
                    int phase = ratio > 0.66f ? 0 : ratio > 0.33f ? 1 : 2;
                    if (phase != overseerPhase) {
                        overseerPhase = phase;
                        if (phase > 0) {
                            say("A new furnace ignites!", ChatFormatting.GOLD);
                            int[][] cells = layout.room("R6").spawnCells;
                            spawn(EntityType.MAGMA_CUBE, cells[rng.nextInt(cells.length)], 1.0, null);
                            spawn(EntityType.MAGMA_CUBE, cells[rng.nextInt(cells.length)], 1.0, null);
                        }
                    }
                    syncBar(overseerBar, players, layout.room("R6"));
                }
            }
            if (heart != null && !heartDead) {
                if (!heart.isAlive()) {
                    if (defeated(heart)) heartDied();
                    else { heart = null; activated.remove("R8"); }
                }
                else {
                    float ratio = heart.getHealth() / heart.getMaxHealth();
                    heartBar.setProgress(Math.max(0f, Math.min(1f, ratio)));
                    syncBar(heartBar, players, layout.room("R8"));
                    heartVents();
                }
            }
        }

        /** Killed, as opposed to removed (despawned, discarded, peaceful clean-up). */
        private boolean defeated(Mob m) {
            return m.getHealth() <= 0 || (m.isRemoved() && m.getRemovalReason() == Entity.RemovalReason.KILLED);
        }

        private void syncBar(ServerBossEvent bar, List<ServerPlayer> players, CinderforgeLayout.Room room) {
            for (ServerPlayer p : players) {
                int[] s = schem(p);
                boolean in = room.contains(s[0], s[1], s[2]);
                if (in && !bar.getPlayers().contains(p)) bar.addPlayer(p);
                else if (!in && bar.getPlayers().contains(p)) bar.removePlayer(p);
            }
        }

        void onDeath(LivingEntity e) {
            if (e == overseer && !overseerDead) overseerDied();
            if (e == heart && !heartDead) heartDied();
        }

        private void overseerDied() {
            if (overseerDead) return;
            overseerDead = true;
            if (overseerBar != null) overseerBar.removeAllPlayers();
            clearAll(layout.data().overseerGate);
            Box g = layout.data().overseerGate;
            level.playSound(null, world(g.min[0], g.min[1], g.min[2]), SoundEvents.ANVIL_DESTROY, SoundSource.BLOCKS, 2f, 0.5f);
            say("The Forge Overseer falls. The slag gate to the Obsidian Spiral grinds open.", ChatFormatting.GOLD);
            if (overseer != null) {
                BlockPos at = overseer.blockPosition();
                level.addFreshEntity(new ItemEntity(level, at.getX() + .5, at.getY() + 1, at.getZ() + .5, new ItemStack(Items.GOLDEN_APPLE, 2)));
            }
        }

        // ---- the Forge Heart -----------------------------------------------------------------------------------

        private void spawnHeart() {
            if (heart != null) return;
            Mob m = spawn(EntityType.BLAZE, layout.data().bossSpawn, 25.0, "The Forge Heart");
            if (m == null) return;
            m.getAttribute(Attributes.SCALE).setBaseValue(3.0);
            m.setNoGravity(true);
            m.setInvulnerable(true);
            heart = m;
            heartShielded = true;
            heartBar = new ServerBossEvent(Component.literal("The Forge Heart - shielded: cool the vents!"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
            say("The Forge Heart ignites. Strike the glowing heat vents to cool them and break its shield!", ChatFormatting.RED);
        }

        /** A player struck a block. Returns true if it was a hot vent (and it is now cooled). */
        boolean strikeBlock(ServerPlayer player, BlockPos pos) {
            if (heart == null || heartDead || !heartShielded) return false;
            int[] s = CinderforgeChunkGenerator.toSchematic(pos);
            Box[] vents = layout.data().heatVents;
            for (int i = 0; i < vents.length; i++) {
                if (ventCool[i] || !vents[i].contains(s[0], s[1], s[2])) continue;
                ventCool[i] = true;
                fill(vents[i], Blocks.BLACKSTONE.defaultBlockState());
                BlockPos at = world(vents[i].min[0], vents[i].min[1], vents[i].min[2]);
                level.playSound(null, at, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 2f, 0.8f);
                level.sendParticles(ParticleTypes.CLOUD, at.getX() + 1, at.getY() + 1, at.getZ() + 1, 25, 0.6, 0.3, 0.6, 0.05);
                cooled++;
                say(player.getName().getString() + " cools a vent (" + cooled + "/" + VENTS_TO_COOL + ").", ChatFormatting.AQUA);
                if (cooled >= VENTS_TO_COOL) openWindow();
                return true;
            }
            return false;
        }

        private void openWindow() {
            heartShielded = false;
            heart.setInvulnerable(false);
            heart.setGlowingTag(true);
            windowEnds = now + WINDOW_TICKS;
            heartBar.setName(Component.literal("The Forge Heart - EXPOSED!"));
            heartBar.setColor(BossEvent.BossBarColor.YELLOW);
            level.playSound(null, heart.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 3f, 0.6f);
            say("The shield shatters! Strike the Forge Heart now!", ChatFormatting.GOLD);
        }

        private void heartVents() {
            if (!heartShielded && windowEnds > 0 && now >= windowEnds) {
                // the window closes: the vents reheat, the shield returns, and the Heart summons help
                heartShielded = true; windowEnds = -1; cooled = 0;
                heart.setInvulnerable(true); heart.setGlowingTag(false);
                heartBar.setName(Component.literal("The Forge Heart - shielded: cool the vents!"));
                heartBar.setColor(BossEvent.BossBarColor.PURPLE);
                BlockState magma = Blocks.MAGMA_BLOCK.defaultBlockState();
                for (int i = 0; i < ventCool.length; i++) if (ventCool[i]) { ventCool[i] = false; fill(layout.data().heatVents[i], magma); }
                int[][] cells = layout.room("R8").spawnCells;
                spawn(EntityType.BLAZE, cells[rng.nextInt(cells.length)], 0.6, "Cinder Wisp");
                spawn(EntityType.WITHER_SKELETON, cells[rng.nextInt(cells.length)], 1.0, null);
                say("The vents flare back to life, and the Heart calls its servants.", ChatFormatting.RED);
            }
        }

        private void heartDied() {
            if (heartDead) return;
            heartDead = true;
            if (heartBar != null) heartBar.removeAllPlayers();
            say("The Forge Heart goes dark. The crucible cools and a lift rises from its core!", ChatFormatting.GOLD);
            // the crucible lava becomes the lift pad; the Barracks lift cell opens
            fill(layout.data().liftPad, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
            clearAll(layout.data().crucibleRim);   // so the pad can be walked onto
            clearAll(layout.data().westPlug);
            liftOpen = true;
            completeAt = now + COMPLETE_DELAY_TICKS;
            level.playSound(null, world(layout.data().liftPad.centre()), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3f, 0.8f);
        }

        // ---- the lift and the end of the run ---------------------------------------------------------------------

        private void lift(List<ServerPlayer> players) {
            if (!liftOpen) return;
            Box pad = layout.data().liftPad;
            for (ServerPlayer p : players) {
                int[] s = schem(p);
                boolean on = s[0] >= pad.min[0] && s[0] <= pad.max[0] && s[2] >= pad.min[2] && s[2] <= pad.max[2] && s[1] >= pad.min[1] && s[1] <= pad.min[1] + 2;
                if (!on) { liftCharge.remove(p.getUUID()); continue; }
                int c = liftCharge.merge(p.getUUID(), 1, Integer::sum);
                if (c % 10 == 0) level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 0.2, p.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
                if (c >= LIFT_CHARGE_TICKS) {
                    liftCharge.remove(p.getUUID());
                    BlockPos to = world(layout.data().liftArrival);
                    p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 90f, 0f);
                    level.playSound(null, to, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.4f, 1.2f);
                    p.sendSystemMessage(Component.literal("The lift carries you back to the Crimson Barracks.").withStyle(ChatFormatting.AQUA));
                }
            }
        }

        private void completionCountdown() {
            long left = completeAt - now;
            if (left == 20 * 30 || left == 20 * 10 || left == 20 * 5) say("The Bastion collapses into ash in " + left / 20 + " seconds.", ChatFormatting.GRAY);
            if (left <= 0) {
                completeAt = -1;
                RunManager m = manager.get();
                if (m != null) m.complete(run.id());
            }
        }
    }
}
