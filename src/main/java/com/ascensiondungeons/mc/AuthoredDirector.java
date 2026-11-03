package com.ascensiondungeons.mc;

import com.ascensiondungeons.AscensionDungeonsLog;
import com.ascensiondungeons.gen.authored.AuthoredLayout;
import com.ascensiondungeons.gen.authored.AuthoredLayout.Box;
import com.ascensiondungeons.gen.authored.Jx;
import com.ascensiondungeons.run.Run;
import com.ascensiondungeons.run.RunManager;
import com.ascensiondungeons.run.RunState;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Runs the declared mechanics of every active authored-dungeon run (see docs/type-dungeons-plan.md for the DSL): ordered
 * levers, key locks, tides and zones, room encounters, bosses with a strike-target shield, and completion. Everything uses
 * vanilla blocks, entities, boss bars and sounds, so no client install is needed. All methods run on the server thread.
 */
public final class AuthoredDirector implements ArenaDirector {
    private final MinecraftServer server;
    private final Supplier<RunManager> manager;
    private final FantasyRunHost host;
    private final Map<UUID, Session> sessions = new HashMap<>();
    /** The live director (one per server), for operator test commands. */
    public static volatile AuthoredDirector current;
    private AuthoredBridges.PokemonSpawner pokemon;
    private AuthoredBridges.RaidBridge raids;
    private AuthoredBridges.Ascension ascension;
    private AuthoredBridges.Combat combat;
    private com.ascensiondungeons.run.RunConfig cfg = new com.ascensiondungeons.run.RunConfig();

    public void bridges(com.ascensiondungeons.run.RunConfig cfg, AuthoredBridges.PokemonSpawner pokemon, AuthoredBridges.RaidBridge raids, AuthoredBridges.Ascension ascension, AuthoredBridges.Combat combat) {
        this.cfg = cfg; this.pokemon = pokemon; this.raids = raids; this.ascension = ascension; this.combat = combat;
    }

    /** Operator/test aid: sends the player's first Pokemon out. */
    public String testSendOut(net.minecraft.server.level.ServerPlayer player) {
        return combat == null ? "Real-time combat is off." : combat.sendOut(player);
    }

    public String testInflict(net.minecraft.server.level.ServerPlayer player, String kind) {
        return combat == null ? "Real-time combat is off." : combat.testInflict(player, kind);
    }

    /** Operator/test aid: ends the raid of every raid-mode boss in this level as a victory. */
    public boolean forceBossVictory(Level level) {
        Session s = sessionFor(level);
        return s != null && s.forceBossVictory();
    }

    public AuthoredDirector(MinecraftServer server, Supplier<RunManager> manager, FantasyRunHost host) {
        this.server = server;
        this.manager = manager;
        this.host = host;
    }

    @Override
    public void tick(long now) {
        if (ascension != null) ascension.tick(now);
        RunManager m = manager.get();
        if (m == null) return;
        for (Run run : m.runs()) {
            if (run.state() != RunState.ACTIVE || !com.ascensiondungeons.gen.authored.AuthoredDungeons.isAuthored(run.dungeonType())) continue;
            Session s = sessions.get(run.id());
            if (s == null) {
                var view = host.authoredView(run.id());
                if (view.isEmpty()) continue;
                s = new Session(run, view.get().level(), view.get().generator());
                sessions.put(run.id(), s);
            }
            try { s.tick(now); }
            catch (RuntimeException e) { AscensionDungeonsLog.LOG.error("Authored session tick failed for run {}", run.id(), e); }
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

    /** The session whose level this is, or null if it is not a live authored run level. */
    public Session sessionFor(Level level) {
        for (Session s : sessions.values()) if (s.level == level) return s;
        return null;
    }

    public void onDeath(LivingEntity entity) {
        Session s = sessionFor(entity.level());
        if (s != null) s.onDeath(entity);
    }

    /** Operator/test aid: the session's summary line. */
    public String describe(Level level) {
        Session s = sessionFor(level);
        return s == null ? "no authored session" : s.describe();
    }

    // ===========================================================================================================

    private static final class BossState {
        final JsonObject def;
        Mob mob;
        ServerBossEvent bar;
        boolean dead, shielded;
        int phase = -1, struck;
        long windowEnds = -1;
        final Set<Integer> hit = new HashSet<>();
        boolean raidMode;
        UUID raidId;
        volatile AuthoredBridges.RaidOutcome outcome;
        BossState(JsonObject def) { this.def = def; this.shielded = def.has("shield"); }
        String id() { return Jx.s(def, "id", "boss"); }
    }

    private static final class LeverState { int step; }

    /** One run's mutable mechanics state. */
    public final class Session {
        static final int COMPLETE_DELAY_DEFAULT = 45;

        final Run run;
        final ServerLevel level;
        final AuthoredChunkGenerator gen;
        final AuthoredLayout layout;
        final Random rng;
        private long now;
        private record Task(long at, long seq, Runnable action) {}
        private final PriorityQueue<Task> tasks = new PriorityQueue<>(java.util.Comparator.comparingLong(Task::at).thenComparingLong(Task::seq));
        private long seq;
        private final Set<String> activated = new HashSet<>();
        private final Set<String> done = new HashSet<>();            // finished mechanic ids
        private final Map<String, LeverState> levers = new HashMap<>();
        private final Map<String, BossState> bosses = new HashMap<>();
        private final Map<Integer, Boolean> tideHigh = new HashMap<>();
        private long completeAt = -1;
        private int partySizeAtStart = 1;
        private final List<JsonObject> mechanics = new ArrayList<>();

        Session(Run run, ServerLevel level, AuthoredChunkGenerator gen) {
            this.run = run;
            this.level = level;
            this.gen = gen;
            this.layout = gen.layout();
            this.rng = new Random(run.seed() ^ 0x4155_5448L);
            for (JsonElement e : layout.data().mechanics) mechanics.add(e.getAsJsonObject());
            for (JsonElement e : layout.data().bosses) {
                BossState b = new BossState(e.getAsJsonObject());
                bosses.put(b.id(), b);
            }
        }

        public Run run() { return run; }
        public ServerLevel level() { return level; }
        public AuthoredChunkGenerator generator() { return gen; }
        public boolean isDone(String mechanic) { return done.contains(mechanic); }
        public boolean bossDead(String id) { BossState b = bosses.get(id); return b != null && b.dead; }

        String describe() {
            StringBuilder sb = new StringBuilder(layout.id()).append(": done=").append(done).append(" bosses=");
            bosses.forEach((k, b) -> sb.append(k).append(b.dead ? ":dead " : b.mob != null ? ":alive " : ":idle "));
            return sb.toString();
        }

        void close() {
            tasks.clear();
            if (combat != null) combat.release(level);
            for (BossState b : bosses.values()) {
                if (b.bar != null) b.bar.removeAllPlayers();
                if (b.raidId != null && raids != null) raids.abort(b.raidId);
            }
        }

        void later(long delayTicks, Runnable action) { tasks.add(new Task(now + delayTicks, seq++, action)); }

        void tick(long tickNow) {
            boolean first = now == 0;
            now = tickNow;
            while (!tasks.isEmpty() && tasks.peek().at <= now) tasks.poll().action.run();
            List<ServerPlayer> players = players();
            if (first) {
                partySizeAtStart = Math.max(1, players.size());
                String intro = Jx.s(layout.data().completion, "intro", layout.data().name);
                say(intro, ChatFormatting.AQUA);
            }
            if (combat != null && cfg.authoredCombat && now % 2 == 0) {
                aggressors.removeIf(m -> !m.isAlive());
                combat.tick(level, now, aggressors);
            }
            if (players.isEmpty()) return;
            if (now % 10 == 0) activateRooms(players);
            for (JsonElement e : layout.data().hazards) hazard(e.getAsJsonObject(), players);
            for (JsonElement e : layout.data().pads) pad(e.getAsJsonObject(), players);
            for (JsonObject m : mechanics) if ("trial".equals(Jx.s(m, "type", ""))) trialTick(m, players);
            for (BossState b : bosses.values()) bossTick(b, players);
            if (completeAt > 0) completionCountdown();
        }

        /** Dungeon Pokemon that hunt the players while real-time combat is on. */
        final List<Mob> aggressors = new ArrayList<>();

        List<ServerPlayer> players() {
            List<ServerPlayer> out = new ArrayList<>();
            for (ServerPlayer p : level.players()) if (p.isAlive() && !p.isSpectator()) out.add(p);
            return out;
        }

        // ---- helpers ---------------------------------------------------------------------------------------

        BlockPos world(int[] c) { return gen.toWorld(c); }
        int[] schem(Entity e) { return gen.toSchematic(e.blockPosition()); }
        double healthFactor() { return 1.0 + 0.6 * (partySizeAtStart - 1); }
        boolean peaceful() { return level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL; }

        void say(String text, ChatFormatting colour) {
            for (ServerPlayer p : level.players()) p.sendSystemMessage(Component.literal(text).withStyle(colour));
        }

        ChatFormatting colour(String name, ChatFormatting def) {
            ChatFormatting c = name == null ? null : ChatFormatting.getByName(name);
            return c == null ? def : c;
        }

        BlockPos centreOf(JsonObject spec) {
            Box b = Box.of(spec.get("box"));
            if (b != null) return world(b.centre());
            int[] at = Jx.ints(spec, "at");
            return at != null ? world(at) : world(layout.data().entry);
        }

        boolean inBox(Box b, Entity e) {
            int[] s = schem(e);
            return b.contains(s[0], s[1], s[2]);
        }

        // ---- effects -----------------------------------------------------------------------------------------

        /** Runs a list of effects in order; a {@code delay} effect pauses the rest of the list. */
        void play(JsonArray effects) {
            long wait = 0;
            for (JsonElement el : effects) {
                JsonObject fx = el.getAsJsonObject();
                if ("delay".equals(Jx.s(fx, "do", ""))) { wait += Jx.i(fx, "ticks", 20); continue; }
                if (wait == 0) applyEffect(fx); else later(wait, () -> applyEffect(fx));
            }
        }

        void applyEffect(JsonObject fx) {
            try {
                switch (Jx.s(fx, "do", "")) {
                    case "clear" -> setBox(Box.of(fx.get("box")), Blocks.AIR.defaultBlockState(), Jx.s(fx, "only", null));
                    case "fill" -> setBox(Box.of(fx.get("box")), AuthoredChunkGenerator.state(Jx.s(fx, "block", "minecraft:air")), Jx.s(fx, "only", null));
                    case "say" -> say(Jx.s(fx, "text", ""), colour(Jx.s(fx, "color", null), ChatFormatting.AQUA));
                    case "sound" -> sound(centreOf(fx), Jx.s(fx, "id", "minecraft:block.anvil.use"), (float) Jx.d(fx, "vol", 1.5), (float) Jx.d(fx, "pitch", 0.8));
                    case "particles" -> particles(fx);
                    case "spawn" -> spawnEffect(fx);
                    case "effect" -> potion(fx, players());
                    case "give" -> {
                        BlockPos at = centreOf(fx);
                        String key = Jx.s(fx, "key", null);
                        ItemStack stack = key != null ? AuthoredItems.key(key, layout.data().keys.has(key) ? layout.data().keys.getAsJsonObject(key) : null)
                                : new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(Jx.s(fx, "item", "minecraft:apple"))), Jx.i(fx, "count", 1));
                        level.addFreshEntity(new ItemEntity(level, at.getX() + .5, at.getY() + 1, at.getZ() + .5, stack));
                    }
                    case "mark" -> done.add(Jx.s(fx, "id", "?"));
                    default -> AscensionDungeonsLog.LOG.warn("Unknown effect {} in {}", fx, layout.id());
                }
            } catch (RuntimeException e) {
                AscensionDungeonsLog.LOG.error("Effect {} failed in {}", fx, layout.id(), e);
            }
        }

        /** Sets every cell of the box (flag 2: no neighbour updates, so water or lava beside it never flows). */
        int setBox(Box b, BlockState state, String only) {
            if (b == null) return 0;
            int n = 0;
            for (int x = b.min[0]; x <= b.max[0]; x++)
                for (int y = b.min[1]; y <= b.max[1]; y++)
                    for (int z = b.min[2]; z <= b.max[2]; z++) {
                        BlockPos p = gen.toWorld(new int[] {x, y, z});
                        BlockState cur = level.getBlockState(p);
                        if (only != null && !matches(cur, only)) continue;
                        if (cur == state) continue;
                        level.setBlock(p, state, 2);
                        n++;
                    }
            return n;
        }

        boolean matches(BlockState s, String only) {
            return switch (only) {
                case "air" -> s.isAir();
                case "water" -> s.is(Blocks.WATER) || s.is(Blocks.BUBBLE_COLUMN);
                case "lava" -> s.is(Blocks.LAVA);
                case "solid" -> !s.isAir() && s.getFluidState().isEmpty();
                default -> BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString().equals(only);
            };
        }

        void sound(BlockPos at, String id, float vol, float pitch) {
            BuiltInRegistries.SOUND_EVENT.getOptional(ResourceLocation.parse(id)).ifPresent(ev -> level.playSound(null, at, ev, SoundSource.BLOCKS, vol, pitch));
        }

        void particles(JsonObject fx) {
            BlockPos at = centreOf(fx);
            var type = BuiltInRegistries.PARTICLE_TYPE.getOptional(ResourceLocation.parse(Jx.s(fx, "type", "minecraft:cloud"))).orElse(null);
            if (!(type instanceof SimpleParticleType simple)) return;
            double[] sp = Jx.doubles(fx, "spread");
            if (sp == null) sp = new double[] {1, 1, 1};
            level.sendParticles((ParticleOptions) simple, at.getX() + .5, at.getY() + .5 + Jx.d(fx, "lift", 0), at.getZ() + .5, Jx.i(fx, "count", 20), sp[0], sp[1], sp[2], Jx.d(fx, "speed", 0.05));
        }

        void potion(JsonObject fx, List<ServerPlayer> targets) {
            var effect = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(Jx.s(fx, "effect", "minecraft:speed")));
            if (effect.isEmpty()) return;
            Box box = Box.of(fx.get("box"));
            for (ServerPlayer p : targets) {
                if (box != null && !inBox(box, p)) continue;
                p.addEffect(new MobEffectInstance(effect.get(), Jx.i(fx, "duration", 100), Jx.i(fx, "amp", 0), true, Jx.b(fx, "visible", false)));
            }
        }

        // ---- levers and locks ----------------------------------------------------------------------------------

        /** Returns true if the position is a lever of a sequence (then {@code verdict[0]} says allow or refuse). */
        public boolean leverClicked(ServerPlayer player, int[] sc, boolean[] allow) {
            for (JsonObject m : mechanics) {
                if (!"lever_sequence".equals(Jx.s(m, "type", ""))) continue;
                JsonArray lv = Jx.a(m, "levers");
                String id = Jx.s(m, "id", "?");
                for (int i = 0; i < lv.size(); i++) {
                    int[] p = Jx.ints(lv.get(i));
                    if (p[0] != sc[0] || p[1] != sc[1] || p[2] != sc[2]) continue;
                    LeverState st = levers.computeIfAbsent(id, k -> new LeverState());
                    if (i == st.step) {
                        st.step++;
                        allow[0] = true;
                        final int index = i;
                        later(1, () -> leverStep(m, id, index, st));
                    } else {
                        allow[0] = false;
                        player.sendSystemMessage(Component.literal(i < st.step ? "This mechanism has already been thrown."
                                : Jx.s(m, "jam", "The mechanism is jammed. Another lever must be pulled first.")).withStyle(ChatFormatting.GRAY));
                    }
                    return true;
                }
            }
            for (JsonObject m : mechanics) {
                String type = Jx.s(m, "type", "");
                String id = Jx.s(m, "id", "?");
                if (type.equals("toggle_pattern") && !done.contains(id)) {
                    JsonArray lv = Jx.a(m, "levers");
                    for (int i = 0; i < lv.size(); i++) {
                        int[] p = Jx.ints(lv.get(i).getAsJsonObject(), "pos");
                        if (p[0] == sc[0] && p[1] == sc[1] && p[2] == sc[2]) { allow[0] = true; later(2, () -> checkToggle(m)); return true; }
                    }
                } else if (type.equals("sequence") && !done.contains(id)) {
                    JsonArray bt = Jx.a(m, "buttons");
                    for (int i = 0; i < bt.size(); i++) {
                        int[] p = Jx.ints(bt.get(i));
                        if (p[0] != sc[0] || p[1] != sc[1] || p[2] != sc[2]) continue;
                        allow[0] = true;
                        pressed(m, id, i, player);
                        return true;
                    }
                }
            }
            return false;
        }

        private final Map<String, Integer> seqStep = new HashMap<>();

        private void pressed(JsonObject m, String id, int index, ServerPlayer player) {
            int[] order = Jx.ints(m, "order");
            if (order == null) { order = new int[Jx.a(m, "buttons").size()]; for (int i = 0; i < order.length; i++) order[i] = i; }
            int step = seqStep.getOrDefault(id, 0);
            BlockPos at = world(Jx.ints(Jx.a(m, "buttons").get(index)));
            if (order[step] == index) {
                step++;
                sound(at, Jx.s(m, "okSound", "minecraft:block.note_block.chime"), 1.5f, 0.8f + 0.15f * step);
                if (step >= order.length) {
                    seqStep.put(id, 0);
                    done.add(id);
                    say(Jx.s(m, "doneText", "The pattern is complete."), ChatFormatting.GOLD);
                    play(Jx.a(m, "onDone"));
                    return;
                }
            } else {
                sound(at, "minecraft:block.note_block.bass", 1.5f, 0.5f);
                if (step > 0) say(Jx.s(m, "failText", "Wrong. The sequence resets."), ChatFormatting.GRAY);
                step = order[0] == index ? 1 : 0;
            }
            seqStep.put(id, step);
        }

        private void checkToggle(JsonObject m) {
            String id = Jx.s(m, "id", "?");
            if (done.contains(id)) return;
            for (JsonElement e : Jx.a(m, "levers")) {
                JsonObject lv = e.getAsJsonObject();
                BlockState st = level.getBlockState(world(Jx.ints(lv, "pos")));
                if (!st.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)) return;
                if (st.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED) != Jx.b(lv, "on", true)) return;
            }
            done.add(id);
            say(Jx.s(m, "doneText", "The mechanisms settle into place."), ChatFormatting.GOLD);
            play(Jx.a(m, "onDone"));
        }

        private void leverStep(JsonObject m, String id, int index, LeverState st) {
            JsonArray steps = Jx.a(m, "onStep");
            if (index < steps.size() && steps.get(index).isJsonArray()) play(steps.get(index).getAsJsonArray());
            if (st.step >= Jx.a(m, "levers").size()) {
                done.add(id);
                play(Jx.a(m, "onDone"));
            }
        }

        /** A block was used; if it belongs to a lock, handle the key. Returns true when the click was consumed. */
        public boolean lockClicked(ServerPlayer player, int[] sc, ItemStack held) {
            for (JsonObject m : mechanics) {
                if (!"lock".equals(Jx.s(m, "type", ""))) continue;
                Box at = Box.of(m.get("at"));
                if (at == null || !at.contains(sc[0], sc[1], sc[2])) continue;
                String id = Jx.s(m, "id", "?");
                if (done.contains(id)) return true;
                String requires = Jx.s(m, "requires", null);
                if (requires != null && !done.contains(requires)) {
                    player.sendSystemMessage(Component.literal(Jx.s(m, "locked", "Nothing happens. Something else must be done first.")).withStyle(ChatFormatting.GRAY));
                    return true;
                }
                String key = Jx.s(m, "key", null);
                if (key != null && !AuthoredItems.isKey(held, key)) {
                    player.sendSystemMessage(Component.literal(Jx.s(m, "hint", "It is locked.")).withStyle(ChatFormatting.GRAY));
                    return true;
                }
                if (key != null && Jx.b(m, "consume", true)) held.shrink(1);
                done.add(id);
                say(player.getName().getString() + " " + Jx.s(m, "verb", "unlocks the way."), ChatFormatting.GOLD);
                play(Jx.a(m, "onOpen"));
                return true;
            }
            return false;
        }

        // ---- encounters ----------------------------------------------------------------------------------------

        private void activateRooms(List<ServerPlayer> players) {
            for (ServerPlayer p : players) {
                int[] s = schem(p);
                AuthoredLayout.Room room = layout.roomAt(s[0], s[1], s[2]);
                if (room == null || !activated.add(room.id)) continue;
                try { populate(room, p); }
                catch (RuntimeException e) { AscensionDungeonsLog.LOG.error("Encounter {} failed in {}", room.id, layout.id(), e); }
            }
        }

        private void populate(AuthoredLayout.Room room, ServerPlayer first) {
            // Peaceful removes hostile mobs at once, so the encounters would only flicker. Skip them.
            for (JsonElement e : layout.data().encounters) {
                JsonObject enc = e.getAsJsonObject();
                if (!room.id.equals(Jx.s(enc, "room", ""))) continue;
                String note = Jx.s(enc, "say", null);
                if (note != null) say(note, ChatFormatting.GRAY);
                for (JsonElement g : Jx.a(enc, "spawns")) {
                    JsonObject spec = g.getAsJsonObject();
                    if (peaceful() && pick(spec) == null && !spec.has("pokemon")) continue;
                    if (peaceful() && !pokemonMode()) continue;
                    spawnGroup(room, spec, first);
                }
            }
            for (BossState b : bosses.values())
                if (room.id.equals(Jx.s(b.def, "room", "")) && !b.dead && b.mob == null && !b.raidMode && (!peaceful() || raidAvailable(b))) spawnBoss(b);
        }

        private void spawnEffect(JsonObject fx) {
            AuthoredLayout.Room room = layout.room(Jx.s(fx, "room", ""));
            spawnGroup(room, fx, null);
        }

        /** Spawn {@code count} mobs on pre-validated standing cells of a room, away from the players. */
        private void spawnGroup(AuthoredLayout.Room room, JsonObject g, ServerPlayer near) { spawnGroup(room, g, near, null); }

        private void spawnGroup(AuthoredLayout.Room room, JsonObject g, ServerPlayer near, List<Mob> sink) {
            int[][] cells = room.spawnCells;
            if (cells.length == 0) return;
            int count = Jx.i(g, "count", 1), placed = 0;
            for (int tries = 0; tries < count * 12 && placed < count; tries++) {
                int[] c = cells[rng.nextInt(cells.length)];
                Vec3 pos = Vec3.atBottomCenterOf(world(c));
                boolean tooClose = false;
                for (ServerPlayer p : players()) if (p.position().distanceToSqr(pos) < 7 * 7) { tooClose = true; break; }
                if (tooClose) continue;
                Mob m = spawn(g, c, Jx.d(g, "health", 1.0));
                if (m != null) { placed++; if (sink != null) sink.add(m); }
            }
        }

        boolean pokemonMode() { return pokemon != null && cfg.authoredPokemon && layout.data().pokemon.has("pools"); }

        /** A pool entry for this spec, or null when the spec is not a Pokemon spec or Pokemon mode is off. */
        private com.ascensiondungeons.run.SpawnTable.Entry pick(JsonObject def) {
            String pool = Jx.s(def, "pokemon", null);
            if (pool == null || !pokemonMode()) return null;
            JsonArray arr = Jx.a(Jx.o(layout.data().pokemon, "pools"), pool);
            List<com.ascensiondungeons.run.SpawnTable.Entry> entries = new ArrayList<>();
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                entries.add(new com.ascensiondungeons.run.SpawnTable.Entry(Jx.s(o, "species", ""), Jx.i(o, "weight", 10), Jx.i(o, "min", 10), Jx.i(o, "max", 20)));
            }
            return com.ascensiondungeons.run.SpawnTable.pick(entries, rng);
        }

        Mob spawn(JsonObject def, int[] cell, double healthMul) {
            var chosen = pick(def);
            if (chosen != null) {
                int lvl = com.ascensiondungeons.run.SpawnTable.level(chosen, rng) + Math.max(0, partySizeAtStart - 1) * Jx.i(Jx.o(layout.data().pokemon, "scaling"), "perPlayer", 0);
                Mob pk = pokemon.spawn(level, chosen.species, Math.min(100, lvl), world(cell), rng.nextFloat() * 360f);
                if (pk != null) { aggressors.add(pk); return pk; }
            }
            var type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(Jx.s(def, "entity", "minecraft:zombie"))).orElse(null);
            if (type == null || !(type.create(level) instanceof Mob mob)) return null;
            BlockPos p = world(cell);
            mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, rng.nextFloat() * 360f, 0f);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
            mob.setPersistenceRequired();
            double factor = healthMul * healthFactor();
            if (factor != 1.0) {
                var attr = mob.getAttribute(Attributes.MAX_HEALTH);
                if (attr != null) { attr.setBaseValue(attr.getBaseValue() * factor); mob.setHealth(mob.getMaxHealth()); }
            }
            String name = Jx.s(def, "name", null);
            if (name != null) { mob.setCustomName(Component.literal(name)); mob.setCustomNameVisible(true); }
            JsonObject eq = Jx.o(def, "equipment");
            if (eq != null) {
                equip(mob, EquipmentSlot.MAINHAND, eq, "mainhand");
                equip(mob, EquipmentSlot.OFFHAND, eq, "offhand");
                equip(mob, EquipmentSlot.HEAD, eq, "head");
                equip(mob, EquipmentSlot.CHEST, eq, "chest");
            }
            level.addFreshEntityWithPassengers(mob);
            return mob;
        }

        private void equip(Mob mob, EquipmentSlot slot, JsonObject eq, String key) {
            if (!eq.has(key)) return;
            mob.setItemSlot(slot, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(eq.get(key).getAsString()))));
            mob.setDropChance(slot, 0f);
        }

        // ---- hazards -------------------------------------------------------------------------------------------

        private void hazard(JsonObject h, List<ServerPlayer> players) {
            String after = Jx.s(h, "after", null);
            if (after != null && !done.contains(after)) return;
            String until = Jx.s(h, "until", null);
            if (until != null && done.contains(until)) return;
            switch (Jx.s(h, "type", "")) {
                case "tide" -> tide(h, players);
                case "zone" -> zone(h, players);
                case "pulse" -> pulse(h, players);
                case "crumble" -> crumble(h, players);
                default -> { }
            }
        }

        private boolean nearBox(Box b, List<ServerPlayer> players, double dist) {
            BlockPos c = world(b.centre());
            double r = dist + Math.max(b.max[0] - b.min[0], Math.max(b.max[1] - b.min[1], b.max[2] - b.min[2])) / 2.0;
            for (ServerPlayer p : players) if (p.distanceToSqr(c.getX() + .5, c.getY() + .5, c.getZ() + .5) < r * r) return true;
            return false;
        }

        /** A basin that floods and drains on a timer; while flooded, a current pushes everything inside along {@code push}. */
        private void tide(JsonObject h, List<ServerPlayer> players) {
            Box water = Box.of(h.get("water"));
            Box zone = Box.of(h.get("box"));
            if (water == null) return;
            if (zone == null) zone = water;
            int period = Jx.i(h, "period", 600), high = Jx.i(h, "high", 240), warn = Jx.i(h, "warn", 80);
            int key = System.identityHashCode(h);
            long phase = (now + Jx.i(h, "offset", 0)) % period;
            boolean isHigh = phase >= period - high;
            Boolean was = tideHigh.get(key);
            BlockPos c = world(water.centre());
            if (was == null || was != isHigh) {
                tideHigh.put(key, isHigh);
                if (isHigh) { setBox(water, Blocks.WATER.defaultBlockState(), "air"); sound(c, "minecraft:entity.generic.splash", 2f, 0.5f); }
                else { setBox(water, Blocks.AIR.defaultBlockState(), "water"); sound(c, "minecraft:block.bubble_column.upwards_inside", 2f, 0.6f); }
            }
            if (!nearBox(zone, players, 24)) return;
            if (!isHigh && phase >= period - high - warn && now % 10 == 0) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, c.getX() + .5, world(new int[] {0, water.min[1] + 1, 0}).getY(), c.getZ() + .5, 30,
                        (water.max[0] - water.min[0]) / 2.0, 0.3, (water.max[2] - water.min[2]) / 2.0, 0.1);
                if (phase == period - high - warn) say(Jx.s(h, "warning", "The tide is turning!"), ChatFormatting.AQUA);
            }
            if (!isHigh) return;
            double[] push = Jx.doubles(h, "push");
            if (push == null) return;
            double force = Jx.d(h, "force", 0.06);
            BlockPos lo = world(zone.min), hi = world(zone.max);
            AABB box = new AABB(lo.getX(), lo.getY(), lo.getZ(), hi.getX() + 1, hi.getY() + 1, hi.getZ() + 1);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (!e.isInWater() || e.isInvulnerable()) continue;
                e.push(push[0] * force, 0, push[1] * force);
                e.hurtMarked = true;
            }
        }

        /** Applies an effect or damage to players standing in a box, every {@code every} ticks. */
        private void zone(JsonObject h, List<ServerPlayer> players) {
            Box box = Box.of(h.get("box"));
            if (box == null || now % Jx.i(h, "every", 20) != 0) return;
            if (h.has("effect")) potion(h, players);
            float dmg = (float) Jx.d(h, "damage", 0);
            if (dmg > 0) for (ServerPlayer p : players) if (inBox(box, p)) p.hurt(level.damageSources().magic(), dmg);
        }

        // ---- pulses: telegraphed bursts at fixed cells or on top of players ------------------------------------------

        private void pulse(JsonObject h, List<ServerPlayer> players) {
            int period = Jx.i(h, "period", 120), warn = Jx.i(h, "warn", 40);
            JsonArray cells = Jx.a(h, "cells");
            String target = Jx.s(h, "target", cells.size() > 0 ? "cells" : "players");
            if (target.equals("cells")) {
                for (int i = 0; i < cells.size(); i++) {
                    int[] c = Jx.ints(cells.get(i));
                    BlockPos base = world(c);
                    boolean near = false;
                    for (ServerPlayer p : players) if (p.distanceToSqr(base.getX() + .5, base.getY(), base.getZ() + .5) < 40 * 40) { near = true; break; }
                    if (!near) continue;
                    if ((now + (long) i * Jx.i(h, "stagger", 31)) % period == 0) startPulse(h, base, warn);
                }
            } else {
                if (now % period != 0 || players.isEmpty()) return;
                int count = Jx.i(h, "count", 1);
                for (int k = 0; k < count; k++) {
                    ServerPlayer p = players.get(rng.nextInt(players.size()));
                    int[] sc = schem(p);
                    Box area = Box.of(h.get("box"));
                    if (area != null && !area.contains(sc[0], sc[1], sc[2])) continue;
                    BlockPos at = p.blockPosition();
                    if (Jx.b(h, "scatter", false)) at = at.offset(rng.nextInt(7) - 3, 0, rng.nextInt(7) - 3);
                    startPulse(h, at, warn);
                }
            }
        }

        private void startPulse(JsonObject h, BlockPos at, int warn) {
            double x = at.getX() + .5, y = at.getY() + Jx.d(h, "lift", 0.2), z = at.getZ() + .5;
            var warnP = BuiltInRegistries.PARTICLE_TYPE.getOptional(ResourceLocation.parse(Jx.s(h, "warnParticle", "minecraft:smoke"))).orElse(null);
            var blastP = BuiltInRegistries.PARTICLE_TYPE.getOptional(ResourceLocation.parse(Jx.s(h, "particle", "minecraft:explosion"))).orElse(null);
            double r = Jx.d(h, "radius", 1.5);
            for (int t = 0; t < warn; t += 4) {
                later(t, () -> { if (warnP instanceof SimpleParticleType sp) level.sendParticles(sp, x, y, z, 4, r * 0.4, 0.1, r * 0.4, 0.01); });
            }
            later(warn, () -> {
                if (blastP instanceof SimpleParticleType sp) level.sendParticles(sp, x, y + 0.5, z, h.has("cells") ? Jx.i(h, "count", 12) : Jx.i(h, "burst", 12), r * 0.5, Jx.d(h, "height", 1.0) * 0.5, r * 0.5, 0.05);
                sound(at, Jx.s(h, "sound", "minecraft:entity.generic.explode"), (float) Jx.d(h, "vol", 0.8), (float) Jx.d(h, "pitch", 1.2));
                if (Jx.b(h, "bolt", false)) {
                    var bolt = EntityType.LIGHTNING_BOLT.create(level);
                    if (bolt != null) { bolt.moveTo(x, at.getY(), z); bolt.setVisualOnly(true); level.addFreshEntity(bolt); }
                }
                AABB box = new AABB(x - r, y - 0.3, z - r, x + r, y + Jx.d(h, "height", 3.0), z + r);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
                    if (e.isInvulnerable() || !(e instanceof ServerPlayer) && !Jx.b(h, "hurtMobs", false)) continue;
                    float dmg = (float) Jx.d(h, "damage", 3);
                    if (dmg > 0) e.hurt(damageSource(Jx.s(h, "kind", "magic")), dmg);
                    if (Jx.i(h, "fire", 0) > 0) e.setRemainingFireTicks(Jx.i(h, "fire", 0));
                    double[] push = Jx.doubles(h, "push");
                    if (push != null) { e.push(push[0], push[1], push[2]); e.hurtMarked = true; }
                    if (h.has("effect")) {
                        var eff = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(Jx.s(h, "effect", "minecraft:slowness")));
                        eff.ifPresent(holder -> e.addEffect(new MobEffectInstance(holder, Jx.i(h, "duration", 60), Jx.i(h, "amp", 0))));
                    }
                }
            });
        }

        private net.minecraft.world.damagesource.DamageSource damageSource(String kind) {
            var ds = level.damageSources();
            return switch (kind) {
                case "fire" -> ds.inFire();
                case "freeze" -> ds.freeze();
                case "lightning" -> ds.lightningBolt();
                case "fall" -> ds.fall();
                case "wither" -> ds.wither();
                default -> ds.magic();
            };
        }

        // ---- crumbling floors -----------------------------------------------------------------------------------

        private final Set<BlockPos> crumbling = new HashSet<>();

        private void crumble(JsonObject h, List<ServerPlayer> players) {
            Box box = Box.of(h.get("box"));
            if (box == null || now % 2 != 0) return;
            String only = Jx.s(h, "only", null);
            for (ServerPlayer p : players) {
                if (!p.onGround() || !inBox(box, p)) continue;
                BlockPos below = p.blockPosition().below();
                if (crumbling.contains(below)) continue;
                BlockState cur = level.getBlockState(below);
                if (cur.isAir() || (only != null && !matches(cur, only)) || !cur.getFluidState().isEmpty()) continue;
                crumbling.add(below);
                int delay = Jx.i(h, "delay", 20), restore = Jx.i(h, "restore", 120);
                sound(below, Jx.s(h, "sound", "minecraft:block.gravel.break"), 1f, 0.7f);
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, cur), below.getX() + .5, below.getY() + 1, below.getZ() + .5, 12, 0.3, 0.05, 0.3, 0.02);
                later(delay, () -> {
                    level.setBlock(below, Blocks.AIR.defaultBlockState(), 2);
                    sound(below, "minecraft:block.stone.break", 1f, 0.6f);
                });
                later(delay + restore, () -> { level.setBlock(below, cur, 2); crumbling.remove(below); });
            }
        }

        // ---- teleport pads -------------------------------------------------------------------------------------

        private final Map<String, Integer> padCharge = new HashMap<>();

        private void pad(JsonObject pd, List<ServerPlayer> players) {
            Box from = Box.of(pd.get("from"));
            String req = Jx.s(pd, "requires", null);
            if (from == null || (req != null && !done.contains(req))) return;
            int charge = Jx.i(pd, "charge", 50);
            for (ServerPlayer p : players) {
                String key = Jx.s(pd, "id", "pad") + p.getUUID();
                if (!inBox(from, p)) { padCharge.remove(key); continue; }
                int c = padCharge.merge(key, 1, Integer::sum);
                if (c % 10 == 0) level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, p.getX(), p.getY() + 0.3, p.getZ(), 10, 0.3, 0.5, 0.3, 0.3);
                if (c >= charge) {
                    padCharge.remove(key);
                    BlockPos to = world(Jx.ints(pd, "to"));
                    p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, p.getYRot(), 0f);
                    sound(to, "minecraft:entity.enderman.teleport", 0.6f, 1.2f);
                    String msg = Jx.s(pd, "text", null);
                    if (msg != null) p.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.AQUA));
                }
            }
        }

        // ---- trials: stand on the plate, then fight the waves ------------------------------------------------------

        private final class TrialState { int held, wave = -1; boolean started; final List<Mob> mobs = new ArrayList<>(); }
        private final Map<String, TrialState> trials = new HashMap<>();

        private void trialTick(JsonObject m, List<ServerPlayer> players) {
            String id = Jx.s(m, "id", "?");
            if (done.contains(id) || (peaceful() && !pokemonMode())) return;
            TrialState t = trials.computeIfAbsent(id, k -> new TrialState());
            JsonArray waves = Jx.a(m, "waves");
            if (!t.started) {
                Box plate = Box.of(m.get("plate"));
                boolean on = false;
                for (ServerPlayer p : players) if (inBox(plate, p)) { on = true; break; }
                t.held = on ? t.held + 1 : 0;
                if (t.held < Jx.i(m, "hold", 40)) return;
                t.started = true;
            }
            t.mobs.removeIf(e -> !e.isAlive());
            if (!t.mobs.isEmpty()) return;
            t.wave++;
            if (t.wave >= waves.size()) {
                done.add(id);
                play(Jx.a(m, "onDone"));
                return;
            }
            JsonObject w = waves.get(t.wave).getAsJsonObject();
            String note = Jx.s(w, "say", null);
            if (note != null) say(note, ChatFormatting.AQUA);
            for (JsonElement g : Jx.a(w, "spawns")) {
                JsonObject spec = g.getAsJsonObject();
                spawnGroup(layout.room(Jx.s(spec, "room", Jx.s(m, "room", ""))), spec, null, t.mobs);
            }
        }

        // ---- bosses --------------------------------------------------------------------------------------------

        boolean realtimeBoss(BossState b) { return combat != null && cfg.authoredCombat && cfg.authoredRealtimeBoss && b.def.has("pokemon") && pokemonMode(); }

        boolean raidAvailable(BossState b) { return raids != null && cfg.authoredRaids && b.def.has("raid") && !realtimeBoss(b); }

        private void spawnBoss(BossState b) {
            JsonObject d = b.def;
            if (raidAvailable(b)) {
                // Raid mode: the guardian is a CobbleRaids encounter; the wards are the key that wakes it.
                b.raidMode = true;
                b.shielded = true;
                say(Jx.s(d, "intro", Jx.s(d, "name", "Boss") + " stirs."), ChatFormatting.RED);
                setTargets(b, true);
                return;
            }
            Mob m = spawn(d, Jx.ints(d, "spawn"), Jx.d(d, "health", 9.0));
            if (m == null) return;
            if (realtimeBoss(b) && aggressors.contains(m)) {
                // A real-time Pokemon boss: more health per player, and it hits harder than its level alone says.
                double hp = Jx.d(d, "pokemonHealth", 0) * healthFactor();
                var maxHp = m.getAttribute(Attributes.MAX_HEALTH);
                if (hp > 0 && maxHp != null) { maxHp.setBaseValue(hp); m.setHealth((float) hp); }
                combat.empower(m, Jx.d(d, "damageMultiplier", 1.8));
                if (Boolean.getBoolean("ascensiondungeons.debug")) AscensionDungeonsLog.LOG.info("Real-time boss {} hp {}/{}", m.getName().getString(), m.getHealth(), m.getMaxHealth());
            }
            // A Pokemon boss already has its own size; "pokemonScale" overrides the vanilla-sized "scale".
            double scale = Jx.d(d, d.has("pokemonScale") && aggressors.contains(m) ? "pokemonScale" : "scale", 1.5);
            var sc = m.getAttribute(Attributes.SCALE);
            if (sc != null) sc.setBaseValue(scale);
            var atk = m.getAttribute(Attributes.ATTACK_DAMAGE);
            if (d.has("attack") && atk != null) atk.setBaseValue(Jx.d(d, "attack", 3));
            var kb = m.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (kb != null) kb.setBaseValue(Jx.d(d, "knockbackResistance", 0.7));
            done.add("boss_awake:" + b.id());
            if (Jx.b(d, "noGravity", false)) m.setNoGravity(true);
            b.mob = m;
            if (b.shielded) m.setInvulnerable(true);
            BossEvent.BossBarColor colour = BossEvent.BossBarColor.valueOf(Jx.s(d, "color", "RED"));
            String name = Jx.s(d, "name", "Boss");
            b.bar = new ServerBossEvent(Component.literal(b.shielded ? name + " - shielded" : name), colour, BossEvent.BossBarOverlay.NOTCHED_6);
            say(Jx.s(d, "intro", name + " awakens."), ChatFormatting.RED);
            if (b.shielded) setTargets(b, true);
        }

        private void setTargets(BossState b, boolean lit) {
            JsonObject shield = Jx.o(b.def, "shield");
            String block = Jx.s(shield, lit ? "onBlock" : "offBlock", "minecraft:sea_lantern");
            BlockState state = AuthoredChunkGenerator.state(block);
            JsonArray targets = Jx.a(shield, "targets");
            for (int i = 0; i < targets.size(); i++) {
                if (!lit && !b.hit.contains(i)) continue;
                setBox(Box.of(targets.get(i)), state, null);
            }
        }

        private void bossTick(BossState b, List<ServerPlayer> players) {
            if (b.raidMode && !b.dead) {
                AuthoredBridges.RaidOutcome o = b.outcome;
                if (o != null) {
                    b.outcome = null;
                    b.raidId = null;
                    if (o == AuthoredBridges.RaidOutcome.VICTORY) bossDied(b);
                    else {
                        done.remove("boss_awake:" + b.id());
                        b.shielded = true; b.hit.clear();
                        setTargets(b, true);
                        say(o == AuthoredBridges.RaidOutcome.DEFEAT ? "The raid is lost. The wards flare back to life." : "The guardian settles. The wards flare back to life.", ChatFormatting.RED);
                    }
                }
                return;
            }
            if (b.mob == null || b.dead) return;
            if (!b.mob.isAlive()) {
                if (b.mob.getHealth() <= 0 || (b.mob.isRemoved() && b.mob.getRemovalReason() == Entity.RemovalReason.KILLED)) bossDied(b);
                else { b.mob = null; if (b.bar != null) b.bar.removeAllPlayers(); activated.remove(Jx.s(b.def, "room", "")); }
                return;
            }
            float ratio = b.mob.getHealth() / b.mob.getMaxHealth();
            b.bar.setProgress(Math.max(0f, Math.min(1f, ratio)));
            if (Jx.b(b.def, "aggro", true) && now % 20 == 0 && b.mob.getTarget() == null) {
                ServerPlayer nearest = null; double best = 1e9;
                for (ServerPlayer p : players) { double dd = p.distanceToSqr(b.mob); if (dd < best) { best = dd; nearest = p; } }
                if (nearest != null && best < 48 * 48) b.mob.setTarget(nearest);
            }
            JsonArray phases = Jx.a(b.def, "phases");
            int phase = -1;
            for (int i = 0; i < phases.size(); i++) if (ratio <= Jx.d(phases.get(i).getAsJsonObject(), "at", 0)) phase = i;
            if (phase > b.phase) {
                for (int i = b.phase + 1; i <= phase; i++) play(Jx.a(phases.get(i).getAsJsonObject(), "effects"));
                b.phase = phase;
            }
            AuthoredLayout.Room room = layout.room(Jx.s(b.def, "room", ""));
            for (ServerPlayer p : players) {
                int[] s = schem(p);
                boolean in = room.contains(s[0], s[1], s[2]);
                if (in && !b.bar.getPlayers().contains(p)) b.bar.addPlayer(p);
                else if (!in && b.bar.getPlayers().contains(p)) b.bar.removePlayer(p);
            }
            if (b.shielded) return;
            JsonObject shield = Jx.o(b.def, "shield");
            if (shield != null && b.windowEnds > 0 && now >= b.windowEnds) relapse(b, shield);
        }

        /** A player struck a block. Returns true if it was one of the active boss's shield targets. */
        public boolean strike(ServerPlayer player, BlockPos pos) {
            int[] s = gen.toSchematic(pos);
            for (BossState b : bosses.values()) {
                if ((b.mob == null && !b.raidMode) || b.dead || !b.shielded || (b.raidMode && b.raidId != null)) continue;
                JsonObject shield = Jx.o(b.def, "shield");
                JsonArray targets = Jx.a(shield, "targets");
                for (int i = 0; i < targets.size(); i++) {
                    Box t = Box.of(targets.get(i));
                    if (b.hit.contains(i) || !t.contains(s[0], s[1], s[2])) continue;
                    b.hit.add(i);
                    setBox(t, AuthoredChunkGenerator.state(Jx.s(shield, "offBlock", "minecraft:prismarine")), null);
                    sound(pos, Jx.s(shield, "hitSound", "minecraft:block.beacon.power_select"), 2f, 1.2f);
                    int needed = Jx.i(shield, "needed", targets.size());
                    say(player.getName().getString() + " breaks a ward (" + b.hit.size() + "/" + needed + ").", ChatFormatting.AQUA);
                    if (b.hit.size() >= needed) openWindow(b, shield);
                    return true;
                }
            }
            return false;
        }

        private void startRaid(BossState b, JsonObject shield) {
            b.shielded = false;
            JsonObject raid = Jx.o(b.def, "raid");
            JsonArray pool = Jx.a(raid, "pool");
            String boss = pool.size() > 0 ? pool.get(Math.floorMod(Long.hashCode(run.seed()), pool.size())).getAsString() : Jx.s(raid, "definition", "cobbleraids:swampert");
            AuthoredLayout.Room room = layout.room(Jx.s(b.def, "room", ""));
            List<ServerPlayer> party = new ArrayList<>();
            for (ServerPlayer p : players()) { int[] c = schem(p); if (room.contains(c[0], c[1], c[2])) party.add(p); }
            if (party.isEmpty()) party = players();
            b.raidId = UUID.randomUUID();
            done.add("boss_awake:" + b.id());
            say(Jx.s(shield, "breakText", "The wards shatter! The guardian answers!"), ChatFormatting.GOLD);
            String refusal = raids.start(b.raidId, party, boss, level, world(Jx.ints(b.def, "spawn")), Jx.i(raid, "level", cfg.raidBossLevel), o -> b.outcome = o);
            if (refusal != null) {
                AscensionDungeonsLog.LOG.warn("Authored raid refused: {}", refusal);
                say("The guardian does not answer (" + refusal + "). The wards flare back.", ChatFormatting.RED);
                b.raidId = null; b.shielded = true; b.hit.clear(); setTargets(b, true);
                done.remove("boss_awake:" + b.id());
            }
        }

        boolean forceBossVictory() {
            boolean any = false;
            for (BossState b : bosses.values()) {
                if (!b.raidMode || b.dead) continue;
                if (b.raidId != null) raids.abort(b.raidId);
                b.outcome = AuthoredBridges.RaidOutcome.VICTORY; any = true;
            }
            return any;
        }

        private void openWindow(BossState b, JsonObject shield) {
            if (b.raidMode) { startRaid(b, shield); return; }
            b.shielded = false;
            b.mob.setInvulnerable(false);
            b.mob.setGlowingTag(true);
            b.windowEnds = now + Jx.i(shield, "window", 360);
            b.bar.setName(Component.literal(Jx.s(b.def, "name", "Boss") + " - EXPOSED!"));
            b.bar.setColor(BossEvent.BossBarColor.YELLOW);
            sound(b.mob.blockPosition(), "minecraft:block.beacon.deactivate", 3f, 0.6f);
            say(Jx.s(shield, "breakText", "The ward shatters! Strike now!"), ChatFormatting.GOLD);
        }

        private void relapse(BossState b, JsonObject shield) {
            b.shielded = true; b.windowEnds = -1;
            b.mob.setInvulnerable(true); b.mob.setGlowingTag(false);
            b.bar.setName(Component.literal(Jx.s(b.def, "name", "Boss") + " - shielded"));
            b.bar.setColor(BossEvent.BossBarColor.valueOf(Jx.s(b.def, "color", "RED")));
            setTargets(b, true);
            b.hit.clear();
            AuthoredLayout.Room room = layout.room(Jx.s(b.def, "room", ""));
            for (JsonElement e : Jx.a(shield, "relapseAdds")) spawnGroup(room, e.getAsJsonObject(), null);
            say(Jx.s(shield, "relapseText", "The wards reform and the guardian calls its servants."), ChatFormatting.RED);
        }

        void onDeath(LivingEntity e) {
            for (BossState b : bosses.values()) if (e == b.mob && !b.dead) bossDied(b);
        }

        private void bossDied(BossState b) {
            if (b.dead) return;
            b.dead = true;
            done.add("boss:" + b.id());
            if (b.bar != null) b.bar.removeAllPlayers();
            say(Jx.s(b.def, "deathText", Jx.s(b.def, "name", "The boss") + " falls."), ChatFormatting.GOLD);
            play(Jx.a(b.def, "onDeath"));
            JsonObject comp = layout.data().completion;
            if (b.id().equals(Jx.s(comp, "boss", ""))) {
                settleRewards();
                completeAt = now + 20L * Jx.i(comp, "delaySeconds", COMPLETE_DELAY_DEFAULT);
                say(Jx.s(comp, "message", "The dungeon is yours. It will close shortly."), ChatFormatting.AQUA);
            }
        }

        private void settleRewards() {
            int tier = Jx.i(layout.data().pokemon, "tier", 0);
            if (ascension == null || tier < 1) return;
            int wins = 0;
            for (JsonObject m : mechanics) if ("trial".equals(Jx.s(m, "type", "")) && done.contains(Jx.s(m, "id", "?"))) wins++;
            wins += 1;
            List<UUID> paid = new ArrayList<>();
            for (var e : run.members().entrySet()) if (e.getValue().presence() != Run.Presence.LEFT) paid.add(e.getKey());
            ascension.settle(run.id(), "VICTORY", tier, wins, true, paid);
        }

        // ---- completion ----------------------------------------------------------------------------------------

        private void completionCountdown() {
            long left = completeAt - now;
            if (left == 20 * 30 || left == 20 * 10 || left == 20 * 5) say("The dungeon closes in " + left / 20 + " seconds.", ChatFormatting.GRAY);
            if (left <= 0) {
                completeAt = -1;
                RunManager m = manager.get();
                if (m != null) m.complete(run.id());
            }
        }
    }
}
