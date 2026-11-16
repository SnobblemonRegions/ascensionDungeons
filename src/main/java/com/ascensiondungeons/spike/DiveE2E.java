package com.ascensiondungeons.spike;

import com.ascensiondungeons.AscensionDungeonsLog;
import com.ascensiondungeons.mc.AuthoredDirector;
import com.ascensiondungeons.mc.OutbreakDive;
import com.ascensiondungeons.mc.RunLoot;
import com.ascensiondungeons.run.Run;
import com.ascensiondungeons.run.RunManager;
import com.ascensiondungeons.run.RunState;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Headless end-to-end check of an Outbreak dive with the whole mod set loaded (Cobblemon included), which a protocol bot cannot
 * join. A stand-in player is put on the server through the normal login path with a connection that goes nowhere, then driven
 * from code: it starts a water run, takes hits from the dungeon's enemies, loots chests, hides loot on a Pokémon, banks, is
 * knocked out, and in a second run extracts through the portal. Env {@code DUNGEONSPIKE_DIVE=1}. Logs "DIVE-E2E ..." lines and
 * halts the server when done. Run it with {@code -Dascensiondungeons.outbreakSpeed=6} or it takes many minutes.
 */
public final class DiveE2E {
    private DiveE2E() {}

    private record Step(String name, int timeoutTicks, BooleanSupplier done) {}

    private static final UUID ID = UUID.nameUUIDFromBytes("OfflinePlayer:DiveBot".getBytes(StandardCharsets.UTF_8));
    private static ServerPlayer bot;
    private static EmbeddedChannel channel;
    private static final List<Step> steps = new ArrayList<>();
    private static int index, passed, failed;
    private static long stepStart = -1;
    private static int carried;
    private static Object firstPokemon, secondPokemon;

    public static void install(Supplier<RunManager> manager) {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long t = server.getTickCount();
            if (t < 100) return;
            try {
                if (steps.isEmpty()) {
                    // DUNGEONSPIKE_DIVE=all plays a short dive in every type dungeon; any other value plays the long water script.
                    if ("all".equalsIgnoreCase(System.getenv("DUNGEONSPIKE_DIVE"))) smoke(server, manager);
                    else script(server, manager);
                }
                if (channel != null) for (Object out; (out = channel.readOutbound()) != null; ) capture(out);
                if (bot != null && !bot.isRemoved() && bot.connection != null) {
                    // A real client confirms each dimension change; until then the server holds the player invulnerable.
                    if (bot.isChangingDimension()) bot.hasChangedDimension();
                    bot.doTick();
                }
                if (index >= steps.size()) return;
                Step s = steps.get(index);
                if (stepStart < 0) stepStart = t;
                boolean ok;
                try { ok = s.done.getAsBoolean(); }
                catch (RuntimeException | LinkageError e) { AscensionDungeonsLog.LOG.error("DIVE-E2E step threw: " + s.name, e); ok = false; stepStart = t - s.timeoutTicks - 1; }
                if (ok) { passed++; AscensionDungeonsLog.LOG.info("DIVE-E2E PASS {} (after {} ticks)", s.name, t - stepStart); next(server); }
                else if (t - stepStart > s.timeoutTicks) { failed++; AscensionDungeonsLog.LOG.error("DIVE-E2E FAIL {} ({})", s.name, state(server)); next(server); }
            } catch (RuntimeException | LinkageError e) {
                AscensionDungeonsLog.LOG.error("DIVE-E2E harness failed", e);
                finish(server);
            }
        });
    }

    /** What the server has sent the stand-in player: chat lines and screen titles, as a real client would get them. */
    private static final List<String> chat = new ArrayList<>(), titles = new ArrayList<>();

    private static void capture(Object out) {
        if (out instanceof net.minecraft.network.protocol.BundlePacket<?> bundle) { for (Object sub : bundle.subPackets()) capture(sub); return; }
        if (out instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket c && !c.overlay()) chat.add(c.content().getString());
        else if (out instanceof net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket t) titles.add(t.text().getString());
        io.netty.util.ReferenceCountUtil.release(out);
    }

    private static boolean heard(String text) { for (String line : chat) if (line.contains(text)) return true; return false; }

    private static com.ascensiondungeons.outbreak.Onboarding.Progress progress() {
        return com.ascensiondungeons.mc.OnboardingStore.of(bot.server).progress(ID);
    }

    private static void next(MinecraftServer server) {
        index++;
        stepStart = -1;
        if (index >= steps.size()) finish(server);
    }

    private static void finish(MinecraftServer server) {
        AscensionDungeonsLog.LOG.info("DIVE-E2E finished: {}/{} passed", passed, passed + failed);
        index = Integer.MAX_VALUE;
        server.halt(false);
    }

    private static String state(MinecraftServer server) {
        OutbreakDive d = dive();
        return "bot in " + (bot == null ? "?" : bot.level().dimension().location()) + " health " + (bot == null ? "?" : bot.getHealth()) + " | " + (d == null ? "no dive" : d.describe());
    }

    private static AuthoredDirector.Session session() {
        AuthoredDirector d = AuthoredDirector.current;
        return d == null || bot == null ? null : d.sessionFor(bot.level());
    }

    private static OutbreakDive dive() { AuthoredDirector.Session s = session(); return s == null ? null : s.dive(); }

    private static int[] count() {   // {stacks, marked}
        int stacks = 0, marked = 0;
        for (int i = 0; i < bot.getInventory().getContainerSize(); i++) {
            ItemStack s = bot.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            stacks++;
            if (RunLoot.runOf(s) != null) marked++;
        }
        return new int[] {stacks, marked};
    }

    private static List<int[]> features(String kind) {
        List<int[]> out = new ArrayList<>();
        for (String line : dive().featureLines()) {
            String[] p = line.split(" ");
            if (p[0].equals(kind)) out.add(new int[] {Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4])});
        }
        return out;
    }

    private static final java.util.Set<String> opened = new java.util.HashSet<>();

    /** A rarity chest this run has not opened yet (Lootr rolls a chest once per player, so a second visit finds it empty). */
    private static int[] freshChest() {
        for (int[] c : features("CHEST")) if (opened.add(c[0] + "," + c[1] + "," + c[2])) return c;
        throw new IllegalStateException("no unopened chest left");
    }

    /** Stand next to a block and right-click it, the way a player would. */
    private static void use(int[] at) {
        ServerLevel level = (ServerLevel) bot.level();
        BlockPos pos = new BlockPos(at[0], at[1], at[2]);
        bot.teleportTo(level, at[0] + 1.5, at[1], at[2] + 0.5, 90f, 0f);
        bot.gameMode.useItemOn(bot, level, bot.getMainHandItem(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    /** Take everything out of the open chest. Returns false if no chest is open. */
    private static boolean takeAll() {
        if (bot.containerMenu == bot.inventoryMenu) return false;
        for (int slot = 0; slot < 27; slot++) bot.containerMenu.quickMoveStack(bot, slot);
        bot.closeContainer();
        return true;
    }

    private static ItemStack takeMarked() {
        for (int i = 0; i < bot.getInventory().getContainerSize(); i++) {
            ItemStack s = bot.getInventory().getItem(i);
            if (!s.isEmpty() && RunLoot.runOf(s) != null) { bot.getInventory().setItem(i, ItemStack.EMPTY); return s; }
        }
        return ItemStack.EMPTY;
    }

    /** True when this world position is inside a room of the Depths. */
    private static boolean deep(double x, double y, double z) {
        AuthoredDirector.Session s = session();
        int[] sc = s.generator().toSchematic(BlockPos.containing(x, y, z));
        return com.ascensiondungeons.gen.authored.AuthoredLayout.inDepths(s.layout().roomAt(s.run().seed(), sc[0], sc[1], sc[2]));
    }

    /** Where the maze's first room is, in the world. */
    private static BlockPos depthsEntrance() {
        AuthoredDirector.Session s = session();
        int[] e = s.layout().depths(s.run().seed()).entrance();
        return s.generator().toWorld(s.layout().link().toSchem(e[0], e[1], e[2]));
    }

    /** Whether a player's body fits in this block of the real world: nothing to collide with and no fluid. */
    private static boolean clear(ServerLevel level, BlockPos p) {
        var state = level.getBlockState(p);
        return state.getCollisionShape(level, p).isEmpty() && state.getFluidState().isEmpty();
    }

    /** A half step: a block no taller than a slab, which a player walks onto without jumping. */
    private static boolean halfStep(ServerLevel level, BlockPos p) {
        var state = level.getBlockState(p);
        var shape = state.getCollisionShape(level, p);
        return !shape.isEmpty() && shape.max(Direction.Axis.Y) <= 0.5 && state.getFluidState().isEmpty();
    }

    /** A block a player can stand in: open with ground under it, or a half step with room above for the half block it raises them. */
    private static boolean standable(ServerLevel level, BlockPos p) {
        if (halfStep(level, p)) return clear(level, p.above()) && clear(level, p.above(2));
        // Ground is a block taller than a half step: over a slab the player is really standing in the slab's own cell.
        return clear(level, p) && clear(level, p.above()) && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty() && !halfStep(level, p.below());
    }

    /**
     * The shortest walk between two standing blocks through the blocks that are really in the world, or null. The walker is
     * two blocks tall, moves on the four compass points, climbs one block where there is headroom to jump and drops up to three.
     */
    private static List<BlockPos> path(ServerLevel level, BlockPos from, BlockPos to) {
        if (!standable(level, from) || !standable(level, to)) return null;
        java.util.Map<BlockPos, BlockPos> parent = new java.util.HashMap<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        parent.put(from, from);
        queue.add(from);
        Direction[] sides = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        while (!queue.isEmpty() && parent.size() < 600_000) {
            BlockPos at = queue.poll();
            if (at.equals(to)) {
                List<BlockPos> out = new ArrayList<>();
                for (BlockPos p = at; !p.equals(from); p = parent.get(p)) out.add(p);
                out.add(from);
                java.util.Collections.reverse(out);
                return out;
            }
            for (Direction side : sides) {
                for (int dy = 1; dy >= -3; dy--) {
                    BlockPos next = at.relative(side).above(dy);
                    if (!standable(level, next)) continue;
                    if (dy == 1 && !clear(level, at.above(2))) continue;
                    boolean open = true;
                    for (int y = next.getY() + 2; y <= at.getY() + 1 && open; y++) open = clear(level, new BlockPos(next.getX(), y, next.getZ()));
                    if (!open) continue;
                    if (parent.putIfAbsent(next, at) == null) queue.add(next);
                    break;
                }
            }
        }
        return null;
    }

    /** Moves the stand-in player along a real walk, a few blocks a tick, then waits for {@code arrived}. Nothing teleports them past a wall. */
    private static final class Walk implements BooleanSupplier {
        final String label;
        final Supplier<BlockPos> target;
        final BooleanSupplier arrived;
        List<BlockPos> path;
        int at;
        Walk(String label, Supplier<BlockPos> target, BooleanSupplier arrived) { this.label = label; this.target = target; this.arrived = arrived; }
        @Override public boolean getAsBoolean() {
            ServerLevel level = (ServerLevel) bot.level();
            bot.setInvulnerable(true);
            if (path == null) {
                BlockPos from = bot.blockPosition(), to = target.get();
                for (int i = 0; i < 3 && !standable(level, from); i++) from = from.below();
                path = path(level, from, to);
                if (path == null) throw new IllegalStateException("no walkable way from " + from.toShortString() + " to " + to.toShortString());
                Vec3 mouth = dive().tunnelMouth();
                double nearest = Double.MAX_VALUE;
                for (BlockPos p : path) nearest = Math.min(nearest, Math.sqrt(p.distToCenterSqr(mouth.x, mouth.y, mouth.z)));
                int halfSteps = 0;
                for (BlockPos p : path) if (halfStep(level, p)) halfSteps++;
                AscensionDungeonsLog.LOG.info("DIVE-E2E walk {}: {} blocks from {} to {}, {} levels {} over {} half steps ({}), passing {} from the tunnel mouth at {}", label, path.size() - 1,
                        from.toShortString(), to.toShortString(), Math.abs(from.getY() - to.getY()), to.getY() < from.getY() ? "down" : "up", halfSteps,
                        halfSteps == 0 ? "none" : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(path.stream().filter(p -> halfStep(level, p)).findFirst().get()).getBlock()),
                        String.format("%.1f", nearest), BlockPos.containing(mouth).toShortString());
                // Every block the stairway descends is taken in two half steps, so nobody has to jump.
                if (halfSteps < com.ascensiondungeons.gen.depths.DepthsLink.DROP) throw new IllegalStateException("the stairway has only " + halfSteps + " half steps");
                // Where the room's wall slopes, the walker may step into the tunnel a few blocks along it rather than at its very mouth.
                if (nearest > com.ascensiondungeons.gen.depths.DepthsLink.OPENING + 1) throw new IllegalStateException("the walk does not go through the tunnel");
            }
            if (at < path.size()) {
                at = Math.min(path.size(), at + 3);
                BlockPos p = path.get(at - 1);
                bot.teleportTo(level, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, bot.getYRot(), 0f);
                return false;
            }
            return arrived.getAsBoolean();
        }
    }

    private static void script(MinecraftServer server, Supplier<RunManager> manager) {
        boolean cobblemon = FabricLoader.getInstance().isModLoaded("cobblemon");
        join(server);
        addRun(manager, "run 1");
        steps.add(new Step("a first-time player is welcomed by the tutorial instead of the briefing, with a Guide bar pointing at a chest", 200, new BooleanSupplier() {
            boolean logged;
            @Override public boolean getAsBoolean() {
                OutbreakDive d = dive();
                if (!d.lessonsTold(ID).contains("WELCOME") || d.guideText(ID) == null) return false;
                if (!logged) AscensionDungeonsLog.LOG.info("DIVE-E2E tutorial: told {}, title {}, guide bar \"{}\", chat {}", d.lessonsTold(ID), titles, d.guideText(ID), chat);
                logged = true;
                return heard("Guide: Welcome to your first dive") && heard("at risk until you leave through the extraction portal") && titles.contains("Welcome to your first dive")
                        && !heard("An Outbreak is building here") && "LOOT".equals(d.guideGoal(ID))
                        && d.guideText(ID).matches("Guide: Open a loot chest {2}[\u2190-\u2199] \\d+m.*") && progress().dives() == 1 && progress().rookie();
            }
        }));
        steps.add(new Step("a wave spawns" + (cobblemon ? " and its enemies are uncatchable, unbattleable Pokémon from the water pools" : ""), 1200, () -> {
            List<Mob> mobs = dive().enemyMobs();
            if (mobs.isEmpty()) return false;
            List<String> names = new ArrayList<>();
            boolean allPokemon = true;
            for (Mob m : mobs) { names.add(m.getName().getString()); allPokemon &= m.getClass().getName().contains("PokemonEntity"); }
            AscensionDungeonsLog.LOG.info("DIVE-E2E enemies: {} ({})", names, mobs.get(0).getClass().getSimpleName());
            return !cobblemon || (allPokemon && Party.areEnemies(mobs));
        }));
        steps.add(new Step("an enemy reaches the player and hurts them (combat and targeting work)", 2400, () -> {
            if (bot.getHealth() < bot.getMaxHealth()) { AscensionDungeonsLog.LOG.info("DIVE-E2E player health {} of {}", bot.getHealth(), bot.getMaxHealth()); return true; }
            return false;
        }));
        steps.add(new Step("the Outbreak and the enemies are explained one at a time, not all at once", 900, () -> {
            bot.setInvulnerable(true);
            bot.setHealth(bot.getMaxHealth());
            List<String> told = dive().lessonsTold(ID);
            if (!told.contains("OUTBREAK") || !told.contains("ENEMIES")) return false;
            AscensionDungeonsLog.LOG.info("DIVE-E2E tutorial so far: {}", told);
            return told.indexOf("WELCOME") == 0 && heard("Guide: The Outbreak") && heard("Guide: Enemies");
        }));
        steps.add(new Step("the player walks out of the entrance room through the tunnel, down the stairway and into the generated Depths", 600, new Walk("down",
                DiveE2E::depthsEntrance, new BooleanSupplier() {
            boolean logged;
            @Override public boolean getAsBoolean() {
                OutbreakDive d = dive();
                if (d.descentCentre() != null) throw new IllegalStateException("this dive still has a teleporting stairway");
                ServerLevel level = (ServerLevel) bot.level();
                BlockPos feet = bot.blockPosition();
                AuthoredDirector.Session s = session();
                int[] sc = s.generator().toSchematic(feet);
                var room = s.layout().roomAt(s.run().seed(), sc[0], sc[1], sc[2]);
                String[] palette = s.layout().depthsPalette();
                // Look along the floor and up a wall: the Depths are built from the dungeon's own four blocks.
                java.util.Set<String> seen = new java.util.TreeSet<>();
                for (int dx = -12; dx <= 12; dx++) for (int dz = -12; dz <= 12; dz++) for (int dy = -1; dy <= 9; dy++) {
                    var state = level.getBlockState(feet.offset(dx, dy, dz));
                    // An invisible light block counts as air, so it is looked for by name.
                    if (!state.isAir() || state.is(net.minecraft.world.level.block.Blocks.LIGHT)) seen.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                }
                if (!logged) AscensionDungeonsLog.LOG.info("DIVE-E2E in the Depths: room {} of {} rooms, standing on {}, blocks nearby {}, palette {}", room == null ? null : room.id,
                        s.layout().rooms(s.run().seed()).length, level.getBlockState(feet.below()).getBlock(), seen, java.util.Arrays.toString(palette));
                logged = true;
                boolean built = seen.contains(palette[0]) && seen.contains(palette[1]) && seen.contains("minecraft:light");
                // The dive itself noticed the player walk in (nothing carried them).
                return room != null && com.ascensiondungeons.gen.authored.AuthoredLayout.inDepths(room) && built && d.inDepths(ID);
            }
        })));
        steps.add(new Step("a chest down in the Depths gives marked loot", 200, new BooleanSupplier() {
            boolean used;
            @Override public boolean getAsBoolean() {
                bot.setInvulnerable(true);
                if (!used) {
                    for (int[] c : features("CHEST")) if (deep(c[0], c[1], c[2]) && opened.add(c[0] + "," + c[1] + "," + c[2])) { use(c); used = true; AscensionDungeonsLog.LOG.info("DIVE-E2E opening the Depths chest at {} {} {}", c[0], c[1], c[2]); return false; }
                    throw new IllegalStateException("no chest in the Depths");
                }
                if (!takeAll()) return false;
                int[] c = count();
                for (int i = 0; i < bot.getInventory().getContainerSize(); i++) bot.getInventory().setItem(i, ItemStack.EMPTY);
                return c[0] > 0 && c[0] == c[1];
            }
        }));
        steps.add(new Step("the tutorial explains the Depths and unbanked loot, and the Guide bar turns to getting out", 1500, () -> {
            OutbreakDive d = dive();
            List<String> told = d.lessonsTold(ID);
            if (!told.contains("DEPTHS") || !told.contains("UNBANKED")) return false;
            AscensionDungeonsLog.LOG.info("DIVE-E2E tutorial in the Depths: told {}, guide bar \"{}\"", told, d.guideText(ID));
            return "EXTRACT".equals(d.guideGoal(ID)) && d.guideText(ID).startsWith("Guide: Get back to the extraction portal and leave") && heard("Guide: Your loot is at risk");
        }));
        steps.add(new Step("a wave follows the party into the Depths", 1200, () -> {
            for (Mob m : dive().enemyMobs()) if (deep(m.getX(), m.getY(), m.getZ())) { AscensionDungeonsLog.LOG.info("DIVE-E2E enemy {} in the Depths at {}", m.getName().getString(), m.blockPosition().toShortString()); return true; }
            return false;
        }));
        steps.add(new Step("the player walks back up the stairway to the entrance room", 900, new Walk("up", () -> BlockPos.containing(dive().tunnelMouth()), () -> {
            AuthoredDirector.Session s = session();
            int[] sc = s.generator().toSchematic(bot.blockPosition());
            var room = s.layout().roomAt(s.run().seed(), sc[0], sc[1], sc[2]);
            return room != null && room == s.layout().entryRoom() && !dive().inDepths(ID);
        })));
        steps.add(new Step("an enemy that spawned up in the dungeon chases the player down the stairway", 4800, new BooleanSupplier() {
            int t = -1;
            final java.util.Set<UUID> fromAbove = new java.util.HashSet<>();
            @Override public boolean getAsBoolean() {
                AuthoredDirector.Session s = session();
                var link = s.layout().link();
                ServerLevel level = (ServerLevel) bot.level();
                final int foot = 22;                                       // the foot of the stairway, eight blocks below the dungeon's floor
                BlockPos top = s.generator().toWorld(link.stair(-1));
                bot.setInvulnerable(false);
                bot.setHealth(bot.getMaxHealth());
                if (t < 0) {
                    for (Mob m : dive().enemyMobs()) m.discard();          // nothing left over from the Depths
                    // Stand at the head of the stairway: the entrance room is the nearest room, so that is where the next wave spawns.
                    t = 1;
                    BlockPos head = s.generator().toWorld(link.stair(t));
                    bot.teleportTo(level, head.getX() + 0.5, head.getY(), head.getZ() + 0.5, 0f, 0f);
                    return false;
                }
                Mob nearest = null;
                for (Mob m : dive().enemyMobs()) {
                    if (m.getY() >= top.getY() - 0.5 && fromAbove.add(m.getUUID()))
                        AscensionDungeonsLog.LOG.info("DIVE-E2E {} is up on the dungeon floor at {}", m.getName().getString(), m.blockPosition().toShortString());
                    if (fromAbove.contains(m.getUUID()) && (nearest == null || m.distanceToSqr(bot) < nearest.distanceToSqr(bot))) nearest = m;
                }
                if (nearest == null) return false;
                // Flee like a player would: an enemy sees about 26 blocks, so stay just ahead of it, one step at a time.
                if (t < foot) {
                    if (nearest.distanceToSqr(bot) < 10 * 10) {
                        BlockPos next = s.generator().toWorld(link.stair(++t));
                        bot.teleportTo(level, next.getX() + 0.5, next.getY(), next.getZ() + 0.5, 0f, 0f);
                    }
                    return false;
                }
                if (nearest.getY() > top.getY() - 5 || nearest.distanceToSqr(bot) > 6 * 6) return false;
                AscensionDungeonsLog.LOG.info("DIVE-E2E {} ({}) came down the stairway from the dungeon floor (y {}) to {} beside the player at {}", nearest.getName().getString(),
                        nearest.getClass().getSimpleName(), top.getY(), nearest.blockPosition().toShortString(), bot.blockPosition().toShortString());
                return true;
            }
        }));
        steps.add(new Step("a rarity chest opens and its loot is marked", 100, new BooleanSupplier() {
            boolean used;
            @Override public boolean getAsBoolean() {
                bot.setInvulnerable(true);
                bot.setHealth(bot.getMaxHealth());
                if (!used) { use(freshChest()); used = true; return false; }
                if (!takeAll()) return false;
                int[] c = count();
                AscensionDungeonsLog.LOG.info("DIVE-E2E chest 1: {} stacks, {} marked: {}", c[0], c[1], describeInventory());
                carried = c[0];
                return c[0] > 0 && c[0] == c[1];
            }
        }));
        if (cobblemon) steps.add(new Step("marked loot can be put on a party Pokémon as a held item", 40, () -> Party.hide(bot)));
        steps.add(new Step("a checkpoint banks: items stay, marks go" + (cobblemon ? ", the held item too" : ""), 40, new BooleanSupplier() {
            boolean banked;
            @Override public boolean getAsBoolean() {
                if (!banked) { dive().operatorCheckpoint(); banked = true; }
                int[] c = count();
                boolean heldOk = !cobblemon || Party.heldIsBanked();
                return c[1] == 0 && c[0] > 0 && heldOk;
            }
        }));
        steps.add(new Step("a second chest gives loot that is at risk again", 100, new BooleanSupplier() {
            boolean used;
            @Override public boolean getAsBoolean() {
                if (!used) { use(freshChest()); used = true; return false; }
                if (!takeAll()) return false;
                carried = count()[0];
                return count()[1] > 0;
            }
        }));
        if (cobblemon) steps.add(new Step("unbanked loot is hidden on the second Pokémon", 40, () -> Party.hideSecond(bot)));
        steps.add(new Step("a lethal hit knocks the player out alive, with only unbanked loot taken" + (cobblemon ? " (the hidden item too)" : ""), 100, new BooleanSupplier() {
            boolean hit; int banked;
            @Override public boolean getAsBoolean() {
                if (!hit) {
                    int[] c = count();
                    banked = c[0] - c[1];
                    bot.setInvulnerable(false);
                    bot.invulnerableTime = 0;
                    bot.hurt(bot.level().damageSources().generic(), 100000f);
                    hit = true;
                    return false;
                }
                if (bot.level() != server.overworld()) return false;
                int[] c = count();
                boolean heldOk = !cobblemon || Party.afterKnockOut();
                AscensionDungeonsLog.LOG.info("DIVE-E2E after knock-out: alive {} health {} stacks {} marked {} (banked before: {})", bot.isAlive(), bot.getHealth(), c[0], c[1], banked);
                return bot.isAlive() && bot.getHealth() > 0 && c[1] == 0 && c[0] == banked && heldOk;
            }
        }));
        steps.add(new Step("the knocked-out player is out of the run", 100, () -> manager.get().runOf(ID).isEmpty()));
        steps.add(new Step("the knock-out is explained on screen and in chat, and the player is still being taught", 40, () -> {
            AscensionDungeonsLog.LOG.info("DIVE-E2E after the knock-out: titles {}, knock-outs {}, graduated {}", titles, progress().knockOuts(), progress().graduated());
            boolean ok = heard("Guide: Knocked out") && heard("everything you brought with you, is safe") && titles.contains("Knocked out") && progress().knockOuts() == 1 && progress().rookie();
            chat.clear();
            titles.clear();
            return ok;
        }));
        steps.add(new Step("the dive guide opens as a window with a page for every lesson, and nothing can be taken from it", 40, new BooleanSupplier() {
            boolean opened;
            @Override public boolean getAsBoolean() {
                if (!opened) { com.ascensiondungeons.mc.GuideMenu.open(bot, progress(), 35); opened = true; return false; }
                if (!(bot.containerMenu instanceof com.ascensiondungeons.mc.GuideMenu menu)) return false;
                int pages = 0, met = 0;
                for (int i = 0; i < 27; i++) {
                    ItemStack page = menu.getSlot(i).getItem();
                    if (page.isEmpty()) continue;
                    pages++;
                    var lore = page.get(net.minecraft.core.component.DataComponents.LORE);
                    if (lore != null && lore.lines().get(lore.lines().size() - 1).getString().equals("You have met this.")) met++;
                }
                int before = totalItems();
                menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, bot);
                menu.clicked(3, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, bot);
                boolean untouched = totalItems() == before && menu.getCarried().isEmpty() && !menu.getSlot(0).getItem().isEmpty();
                String first = menu.getSlot(0).getItem().getHoverName().getString();
                bot.closeContainer();
                AscensionDungeonsLog.LOG.info("DIVE-E2E guide: {} pages, {} met, first \"{}\"", pages, met, first);
                return pages == com.ascensiondungeons.outbreak.Onboarding.Lesson.values().length && met >= 5 && met < pages && untouched && first.equals("Welcome to your first dive")
                        && totalItems() == before;
            }
        }));
        steps.add(new Step("what the player has learned survives a save and a reload", 20, () -> {
            var store = com.ascensiondungeons.mc.OnboardingStore.of(bot.server);
            var tag = store.save(new net.minecraft.nbt.CompoundTag(), bot.server.registryAccess());
            var back = com.ascensiondungeons.mc.OnboardingStore.load(tag, bot.server.registryAccess()).progress(ID);
            var now = progress();
            return back.seenBits() == now.seenBits() && back.seenBits() != 0 && back.dives() == 1 && back.knockOuts() == 1 && back.extractions() == 0 && !back.off();
        }));
        addRun(manager, "run 2");
        steps.add(new Step("second dive: the ordinary briefing, nothing repeated, and the Guide bar is still there", 300, () -> {
            OutbreakDive d = dive();
            if (d.guideText(ID) == null || !heard("An Outbreak is building here")) return false;
            AscensionDungeonsLog.LOG.info("DIVE-E2E second dive: told {}, guide bar \"{}\"", d.lessonsTold(ID), d.guideText(ID));
            return !d.lessonsTold(ID).contains("WELCOME") && !heard("Guide: Welcome") && "LOOT".equals(d.guideGoal(ID)) && progress().dives() == 2;
        }));
        steps.add(new Step("run 2: loot a chest", 100, new BooleanSupplier() {
            boolean used;
            @Override public boolean getAsBoolean() {
                bot.setInvulnerable(true);
                if (!used) { for (int i = 0; i < bot.getInventory().getContainerSize(); i++) bot.getInventory().setItem(i, ItemStack.EMPTY); use(freshChest()); used = true; return false; }
                if (!takeAll()) return false;
                carried = count()[0];
                return count()[1] > 0;
            }
        }));
        steps.add(new Step("run 2: loot a second chest", 100, new BooleanSupplier() {
            boolean used;
            @Override public boolean getAsBoolean() {
                if (!used) { use(freshChest()); used = true; return false; }
                return takeAll();
            }
        }));
        if (cobblemon) steps.add(new Step("run 2: some of the loot is hidden on a Pokémon", 40, () -> Party.hideSecond(bot)));
        steps.add(new Step("run 2: stepping on the portal shows exactly what the 35% toll would take", 200, new BooleanSupplier() {
            boolean moved;
            @Override public boolean getAsBoolean() {
                OutbreakDive d = dive();
                if (!moved) { Vec3 p = d.portalCentre(); bot.teleportTo((ServerLevel) bot.level(), p.x, p.y, p.z, 0f, 0f); moved = true; return false; }
                if (!(bot.containerMenu instanceof com.ascensiondungeons.mc.TollMenu menu)) return false;
                int marked = 0;
                for (int i = 0; i < bot.getInventory().getContainerSize(); i++) { ItemStack s = bot.getInventory().getItem(i); if (RunLoot.runOf(s) != null) marked += s.getCount(); }
                shown = 0;
                List<String> names = new ArrayList<>();
                for (int i = 0; i < com.ascensiondungeons.mc.TollMenu.SHOWN; i++) {
                    ItemStack s = menu.getSlot(i).getItem();
                    if (!s.isEmpty()) { shown += s.getCount(); names.add(s.getCount() + "x" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath()); }
                }
                itemsBefore = totalItems();
                int expected = Math.max(1, (int) Math.round(marked * 0.35));
                AscensionDungeonsLog.LOG.info("DIVE-E2E toll screen: {} unbanked item(s), shows {} to lose {} (expected {}); pay button: {}", marked, shown, names, expected,
                        menu.getSlot(com.ascensiondungeons.mc.TollMenu.PAY).getItem().getHoverName().getString());
                // Before paying, try to take a shown item: nothing may move.
                menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, bot);
                menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, bot);
                return marked > 0 && shown == expected && totalItems() == itemsBefore && menu.getCarried().isEmpty();
            }
        }));
        steps.add(new Step("run 2: paying the toll takes exactly the shown items" + (cobblemon ? " plus the hidden one" : "") + " and lets the player out", 100, new BooleanSupplier() {
            boolean paid;
            @Override public boolean getAsBoolean() {
                if (!paid) {
                    if (!(bot.containerMenu instanceof com.ascensiondungeons.mc.TollMenu menu)) return false;
                    menu.clicked(com.ascensiondungeons.mc.TollMenu.PAY, 0, net.minecraft.world.inventory.ClickType.PICKUP, bot);
                    paid = true;
                    return false;
                }
                if (bot.level() != server.overworld()) return false;
                int[] c = count();
                boolean heldOk = !cobblemon || Party.secondIsEmpty();
                AscensionDungeonsLog.LOG.info("DIVE-E2E after the toll: {} item(s) (had {}, toll showed {}), {} still marked", totalItems(), itemsBefore, shown, c[1]);
                return totalItems() == itemsBefore - shown && c[1] == 0 && heldOk;
            }
        }));
        steps.add(new Step("the tolled player is out of the run", 100, () -> manager.get().runOf(ID).isEmpty()));
        steps.add(new Step("leaving through the portal finishes the tutorial", 40, () -> {
            AscensionDungeonsLog.LOG.info("DIVE-E2E after the toll: titles {}, extractions {}, graduated {}", titles, progress().extractions(), progress().graduated());
            boolean ok = heard("Guide: Extracted") && heard("You paid the toll and kept the rest") && titles.contains("Extracted") && progress().graduated() && !progress().rookie();
            chat.clear();
            titles.clear();
            return ok;
        }));
        addRun(manager, "run 3");
        steps.add(new Step("a graduate has no Guide bar and is told only about what they have never met", 300, () -> {
            OutbreakDive d = dive();
            if (!heard("An Outbreak is building here")) return false;
            return d.guideText(ID) == null && d.guideGoal(ID) == null && !heard("Guide: Welcome") && !heard("Guide: The Outbreak");
        }));
        steps.add(new Step("run 3: loot a chest", 100, new BooleanSupplier() {
            boolean used;
            @Override public boolean getAsBoolean() {
                bot.setInvulnerable(true);
                if (!used) { for (int i = 0; i < bot.getInventory().getContainerSize(); i++) bot.getInventory().setItem(i, ItemStack.EMPTY); use(freshChest()); used = true; return false; }
                if (!takeAll()) return false;
                itemsBefore = totalItems();
                return count()[1] > 0;
            }
        }));
        steps.add(new Step("run 3: challenging calls out a tough gatekeeper" + (cobblemon ? " from the boss pool" : ""), 200, new BooleanSupplier() {
            boolean moved, clicked;
            @Override public boolean getAsBoolean() {
                OutbreakDive d = dive();
                if (!moved) { Vec3 p = d.portalCentre(); bot.teleportTo((ServerLevel) bot.level(), p.x, p.y, p.z, 0f, 0f); moved = true; return false; }
                if (!clicked) {
                    if (!(bot.containerMenu instanceof com.ascensiondungeons.mc.TollMenu menu)) return false;
                    menu.clicked(com.ascensiondungeons.mc.TollMenu.FIGHT, 0, net.minecraft.world.inventory.ClickType.PICKUP, bot);
                    clicked = true;
                    return false;
                }
                Mob g = d.gatekeeperMob();
                if (g == null) return false;
                // A gatekeeper is tough: a 12-point hit must cost it far less than 12.
                float before = g.getHealth();
                g.invulnerableTime = 0;
                g.hurt(g.level().damageSources().generic(), 12f);
                float lost = before - g.getHealth();
                AscensionDungeonsLog.LOG.info("DIVE-E2E gatekeeper: {} ({}) health {} of {}, toughness x{}, a 12-point hit cost it {} | {}", g.getName().getString(),
                        g.getClass().getSimpleName(), g.getHealth(), g.getMaxHealth(), d.toughness(g), lost, d.describe());
                boolean tough = cobblemon ? d.toughness(g) >= 6.0 && lost > 0 && lost <= 12f / 5f : g.getMaxHealth() >= 100;
                return tough && (!cobblemon || g.getClass().getName().contains("PokemonEntity"));
            }
        }));
        steps.add(new Step("run 3: beating the gatekeeper opens the portal and the player leaves with everything", 400, new BooleanSupplier() {
            boolean killed;
            @Override public boolean getAsBoolean() {
                OutbreakDive d = dive();
                if (d != null) {
                    if (!killed) { Mob g = d.gatekeeperMob(); if (g != null) { g.kill(); killed = true; } }
                    return false;
                }
                int[] c = count();
                AscensionDungeonsLog.LOG.info("DIVE-E2E after beating the gatekeeper: {} item(s) (had {}), {} marked", totalItems(), itemsBefore, c[1]);
                return killed && bot.level() == server.overworld() && totalItems() == itemsBefore && c[1] == 0;
            }
        }));
        steps.add(new Step("the extracted player is out of the run", 100, () -> manager.get().runOf(ID).isEmpty()));
    }

    private static void addRun(Supplier<RunManager> manager, String label) { addRun(manager, label, "water"); }

    private static void join(MinecraftServer server) {
        steps.add(new Step("stand-in player joins through the normal login path", 100, () -> {
            if (bot == null) {
                GameProfile profile = new GameProfile(ID, "DiveBot");
                bot = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
                Connection connection = new Connection(PacketFlow.SERVERBOUND);
                channel = new EmbeddedChannel(connection);
                server.getPlayerList().placeNewPlayer(connection, bot, CommonListenerCookie.createInitial(profile, false));
            }
            return server.getPlayerList().getPlayer(ID) == bot;
        }));
    }

    /** A short dive in every type dungeon: it starts, enemies come, the Depths exist and hold loot, and the toll lets the player out. */
    private static void smoke(MinecraftServer server, Supplier<RunManager> manager) {
        boolean cobblemon = FabricLoader.getInstance().isModLoaded("cobblemon");
        join(server);
        for (String type : com.ascensiondungeons.gen.authored.AuthoredDungeons.ids()) {
            if (com.ascensiondungeons.gen.authored.AuthoredLayout.get(type).outbreak() == null) continue;
            addRun(manager, type, type);
            steps.add(new Step(type + ": a wave of " + (cobblemon ? "Pokémon " : "") + "enemies spawns", 1200, () -> {
                bot.setInvulnerable(true);
                List<Mob> mobs = dive().enemyMobs();
                if (mobs.isEmpty()) return false;
                List<String> names = new ArrayList<>();
                boolean allPokemon = true;
                for (Mob m : mobs) { names.add(m.getName().getString()); allPokemon &= m.getClass().getName().contains("PokemonEntity"); }
                AscensionDungeonsLog.LOG.info("DIVE-E2E {} enemies: {}", type, names);
                return !cobblemon || allPokemon;
            }));
            steps.add(new Step(type + ": the tunnel and stairway walk down into Depths built from its own blocks", 600, new Walk(type + " down", DiveE2E::depthsEntrance, () -> {
                OutbreakDive d = dive();
                if (d.descentCentre() != null) throw new IllegalStateException("still a teleporting stairway");
                ServerLevel level = (ServerLevel) bot.level();
                AuthoredDirector.Session s = session();
                int[] sc = s.generator().toSchematic(bot.blockPosition());
                var room = s.layout().roomAt(s.run().seed(), sc[0], sc[1], sc[2]);
                String[] palette = s.layout().depthsPalette();
                java.util.Set<String> seen = new java.util.TreeSet<>();
                for (int dx = -12; dx <= 12; dx++) for (int dz = -12; dz <= 12; dz++) for (int dy = -1; dy <= 9; dy++) {
                    var state = level.getBlockState(bot.blockPosition().offset(dx, dy, dz));
                    if (!state.isAir() || state.is(net.minecraft.world.level.block.Blocks.LIGHT)) seen.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                }
                return room != null && com.ascensiondungeons.gen.authored.AuthoredLayout.inDepths(room) && seen.contains(palette[0]) && seen.contains(palette[1])
                        && seen.contains("minecraft:light") && d.inDepths(ID);
            })));
            steps.add(new Step(type + ": a chest in the Depths gives marked loot", 200, new BooleanSupplier() {
                boolean used;
                @Override public boolean getAsBoolean() {
                    if (!used) {
                        for (int i = 0; i < bot.getInventory().getContainerSize(); i++) bot.getInventory().setItem(i, ItemStack.EMPTY);
                        for (int[] c : features("CHEST")) if (deep(c[0], c[1], c[2]) && opened.add(c[0] + "," + c[1] + "," + c[2])) { use(c); used = true; return false; }
                        throw new IllegalStateException("no chest in the Depths");
                    }
                    if (!takeAll()) return false;
                    int[] c = count();
                    AscensionDungeonsLog.LOG.info("DIVE-E2E {} chest: {}", type, describeInventory());
                    return c[0] > 0 && c[0] == c[1];
                }
            }));
            steps.add(new Step(type + ": the stairway walks back up to the entrance room", 900, new Walk(type + " up", () -> BlockPos.containing(dive().tunnelMouth()), () -> {
                AuthoredDirector.Session s = session();
                int[] sc = s.generator().toSchematic(bot.blockPosition());
                var room = s.layout().roomAt(s.run().seed(), sc[0], sc[1], sc[2]);
                return room != null && room == s.layout().entryRoom() && !dive().inDepths(ID);
            })));
            steps.add(new Step(type + ": the portal shows the toll, and paying it lets the player out", 300, new BooleanSupplier() {
                boolean moved, paid;
                int before, shownNow;
                @Override public boolean getAsBoolean() {
                    OutbreakDive d = dive();
                    if (!paid) {
                        if (!moved) { Vec3 p = d.portalCentre(); bot.teleportTo((ServerLevel) bot.level(), p.x, p.y, p.z, 0f, 0f); moved = true; return false; }
                        if (!(bot.containerMenu instanceof com.ascensiondungeons.mc.TollMenu menu)) return false;
                        before = totalItems();
                        for (int i = 0; i < com.ascensiondungeons.mc.TollMenu.SHOWN; i++) shownNow += menu.getSlot(i).getItem().getCount();
                        menu.clicked(com.ascensiondungeons.mc.TollMenu.PAY, 0, net.minecraft.world.inventory.ClickType.PICKUP, bot);
                        paid = true;
                        return false;
                    }
                    if (bot.level() != server.overworld()) return false;
                    AscensionDungeonsLog.LOG.info("DIVE-E2E {} toll: had {} item(s), shown {}, left with {}", type, before, shownNow, totalItems());
                    return shownNow == Math.max(1, (int) Math.round(before * 0.35)) && totalItems() == before - shownNow && count()[1] == 0;
                }
            }));
            steps.add(new Step(type + ": the player is out of the run", 100, () -> manager.get().runOf(ID).isEmpty()));
        }
    }

    private static void addRun(Supplier<RunManager> manager, String label, String type) {
        steps.add(new Step(label + ": a " + type + " run is accepted", 40, new BooleanSupplier() {
            Run run;
            @Override public boolean getAsBoolean() {
                if (run == null) {
                    opened.clear();
                    var result = manager.get().request(Set.of(ID), type);
                    if (!result.ok()) throw new IllegalStateException("request refused: " + result.error());
                    run = result.run();
                }
                return run != null;
            }
        }));
        steps.add(new Step(label + ": the player is inside an Outbreak dive", 2400, () -> {
            OutbreakDive d = dive();
            if (d == null || manager.get().runOf(ID).map(r -> r.state() != RunState.ACTIVE).orElse(true)) return false;
            AscensionDungeonsLog.LOG.info("DIVE-E2E {}", d.describe());
            return true;
        }));
    }

    private static int shown, itemsBefore;

    private static int totalItems() {
        int n = 0;
        for (int i = 0; i < bot.getInventory().getContainerSize(); i++) n += bot.getInventory().getItem(i).getCount();
        return n;
    }

    private static String describeInventory() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < bot.getInventory().getContainerSize(); i++) {
            ItemStack s = bot.getInventory().getItem(i);
            if (!s.isEmpty()) out.add(s.getCount() + "x" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()));
        }
        return out.toString();
    }

    /** Cobblemon calls, kept in their own class so it only loads when Cobblemon is present. */
    private static final class Party {
        /** Dive enemies cannot be caught or pulled into a turn-based battle. */
        static boolean areEnemies(List<Mob> mobs) {
            for (Mob m : mobs) {
                var e = (com.cobblemon.mod.common.entity.pokemon.PokemonEntity) m;
                boolean unbattleable = e.getEntityData().get(com.cobblemon.mod.common.entity.pokemon.PokemonEntity.getUNBATTLEABLE());
                if (!e.isUncatchable() || !unbattleable || e.canBattle(bot)) {
                    AscensionDungeonsLog.LOG.error("DIVE-E2E {} uncatchable={} unbattleable={} canBattle={}", m.getName().getString(), e.isUncatchable(), unbattleable, e.canBattle(bot));
                    return false;
                }
            }
            return true;
        }

        static boolean hide(ServerPlayer player) {
            var party = com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player);
            var first = com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse("magikarp level=5").create();
            var second = com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse("psyduck level=5").create();
            party.add(first);
            party.add(second);
            firstPokemon = first;
            secondPokemon = second;
            ItemStack marked = takeMarked();
            if (marked.isEmpty()) return false;
            first.swapHeldItem(marked, false, false);
            second.swapHeldItem(new ItemStack(Items.APPLE), false, false);
            return RunLoot.runOf(first.heldItem()) != null;
        }

        static boolean heldIsBanked() {
            var first = (com.cobblemon.mod.common.pokemon.Pokemon) firstPokemon;
            return !first.heldItem().isEmpty() && RunLoot.runOf(first.heldItem()) == null;
        }

        static boolean hideSecond(ServerPlayer player) {
            var second = (com.cobblemon.mod.common.pokemon.Pokemon) secondPokemon;
            ItemStack marked = takeMarked();
            if (marked.isEmpty()) return false;
            second.swapHeldItem(marked, false, false);
            return RunLoot.runOf(second.heldItem()) != null;
        }

        static boolean secondIsEmpty() { return ((com.cobblemon.mod.common.pokemon.Pokemon) secondPokemon).heldItem().isEmpty(); }

        /** The first Pokémon still holds its banked item; the second lost the unbanked one. */
        static boolean afterKnockOut() {
            var first = (com.cobblemon.mod.common.pokemon.Pokemon) firstPokemon;
            var second = (com.cobblemon.mod.common.pokemon.Pokemon) secondPokemon;
            AscensionDungeonsLog.LOG.info("DIVE-E2E held items after knock-out: first {} second {}", first.heldItem(), second.heldItem());
            return !first.heldItem().isEmpty() && second.heldItem().isEmpty();
        }
    }
}
