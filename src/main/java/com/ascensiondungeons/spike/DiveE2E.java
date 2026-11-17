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
    /** A second stand-in player, for the party steps. */
    private static final UUID ID2 = UUID.nameUUIDFromBytes("OfflinePlayer:DiveBot2".getBytes(StandardCharsets.UTF_8));
    private static ServerPlayer bot2;
    private static EmbeddedChannel channel2;
    private static final List<String> chat2 = new ArrayList<>();
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
                if (channel2 != null) for (Object out; (out = channel2.readOutbound()) != null; ) {
                    if (out instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket c && !c.overlay()) chat2.add(c.content().getString());
                    io.netty.util.ReferenceCountUtil.release(out);
                }
                if (bot2 != null && !bot2.isRemoved() && bot2.connection != null) {
                    if (bot2.isChangingDimension()) bot2.hasChangedDimension();
                    bot2.doTick();
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

    /** The number that follows {@code prefix} in the last chat line that has it (the recap's "Kept: 12 items"); -1 if none. */
    private static int recapNumber(String prefix) {
        int found = -1;
        for (String line : chat) {
            int at = line.indexOf(prefix);
            if (at < 0) continue;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(line.substring(at + prefix.length()));
            if (m.find()) found = Integer.parseInt(m.group());
        }
        return found;
    }

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
        steps.add(new Step("/dive opens the picker: every type dungeon, gentlest first, the gentlest recommended, and what this player is not ready for", 40, new BooleanSupplier() {
            boolean sent;
            @Override public boolean getAsBoolean() {
                if (!sent) { opened.clear(); command("dive"); sent = true; return false; }
                if (!(bot.containerMenu instanceof com.ascensiondungeons.mc.DiveMenu menu)) return false;
                int offered = 0;
                List<String> names = new ArrayList<>();
                for (int i = 0; i < 18; i++) { ItemStack it = menu.getSlot(i).getItem(); if (!it.isEmpty()) { offered++; names.add(it.getHoverName().getString()); } }
                List<String> first = lore(menu.getSlot(0).getItem());
                int water = slotOf(menu, "The Drowned Reservoir"), dragon = slotOf(menu, "The Elder Wyrm Roost");
                List<String> waterLore = lore(menu.getSlot(water).getItem());
                AscensionDungeonsLog.LOG.info("DIVE-E2E picker: {} dungeons {}; first: {}; water (slot {}): {}", offered, names, first, water, waterLore);
                String all = String.join(" ", waterLore);
                // 17 dungeons; Normal first and recommended; Dragon (tier 4) last; Water warns about the tier and, with Cobblemon, the missing team.
                return offered == 17 && names.get(0).equals("The Hearthwarden Village") && first.contains("Recommended for your first dive") && first.contains("Normal type, tier 1")
                        && dragon == 16 && waterLore.contains("Water type, tier 3") && waterLore.contains("Enemies: level 12 to 30") && all.contains("This is a tier 3 dungeon.")
                        && (!cobblemon || all.contains("You have no Pokémon with you.")) && (!cobblemon || String.join(" ", first).contains("You have no Pokémon with you."))
                        && menu.getSlot(com.ascensiondungeons.mc.DiveMenu.PARTY).getItem().getHoverName().getString().equals("Diving alone");
            }
        }));
        steps.add(new Step("the leader picks a difficulty in the picker: four buttons, Normal chosen at first, and Hard is written on every dungeon and warned about for a first dive", 60, new BooleanSupplier() {
            int phase;
            @Override public boolean getAsBoolean() {
                if (!(bot.containerMenu instanceof com.ascensiondungeons.mc.DiveMenu menu)) return false;
                int base = com.ascensiondungeons.mc.DiveMenu.DIFFICULTY;
                if (phase == 0) {
                    String normal = menu.getSlot(base + 1).getItem().getHoverName().getString(), hard = menu.getSlot(base + 2).getItem().getHoverName().getString();
                    AscensionDungeonsLog.LOG.info("DIVE-E2E difficulty buttons: {} | {} | {} | {}", menu.getSlot(base).getItem().getHoverName().getString(), normal, hard,
                            menu.getSlot(base + 3).getItem().getHoverName().getString());
                    if (!normal.equals("» Normal «") || !hard.equals("Hard")) throw new IllegalStateException("Normal should be chosen at first");
                    menu.clicked(base + 2, 0, net.minecraft.world.inventory.ClickType.PICKUP, bot);
                    phase = 1;
                    return false;
                }
                if (phase == 1) {
                    String water = String.join(" ", lore(menu.getSlot(slotOf(menu, "The Drowned Reservoir")).getItem()));
                    boolean hardShown = menu.getSlot(base + 2).getItem().getHoverName().getString().equals("» Hard «") && water.contains("Difficulty: Hard") && water.contains("first dive, on Hard");
                    String hardLore = String.join(" ", lore(menu.getSlot(base + 2).getItem()));
                    AscensionDungeonsLog.LOG.info("DIVE-E2E Hard chosen: water says {}; Hard says {}", water, hardLore);
                    if (!hardShown || !hardLore.contains("pseudo-legendaries")) throw new IllegalStateException("Hard was not shown as chosen");
                    menu.clicked(base + 1, 0, net.minecraft.world.inventory.ClickType.PICKUP, bot);    // back to Normal for this first dive
                    phase = 2;
                    return false;
                }
                return menu.getSlot(base + 1).getItem().getHoverName().getString().equals("» Normal «")
                        && String.join(" ", lore(menu.getSlot(slotOf(menu, "The Drowned Reservoir")).getItem())).contains("Difficulty: Normal");
            }
        }));
        steps.add(new Step("picking a harder dungeon for a first dive warns once, takes nothing, and goes in on the second click", 60, new BooleanSupplier() {
            int phase;
            int before;
            @Override public boolean getAsBoolean() {
                if (phase == 0) {
                    if (!(bot.containerMenu instanceof com.ascensiondungeons.mc.DiveMenu menu)) return false;
                    before = totalItems();
                    menu.clicked(slotOf(menu, "The Drowned Reservoir"), 0, net.minecraft.world.inventory.ClickType.PICKUP, bot);
                    phase = 1;
                    return false;
                }
                if (phase == 1) {
                    if (!(bot.containerMenu instanceof com.ascensiondungeons.mc.DiveMenu menu)) return false;
                    boolean warned = heard("The Drowned Reservoir: before you go in") && heard("For a first dive, The Hearthwarden Village (tier 1) is the one to start with.")
                            && heard("Click it again to dive anyway.") && manager.get().runOf(ID).isEmpty() && totalItems() == before && menu.getCarried().isEmpty();
                    if (!warned) throw new IllegalStateException("the first click should only warn");
                    if (!lore(menu.getSlot(slotOf(menu, "The Drowned Reservoir")).getItem()).contains("Click again to dive anyway.")) throw new IllegalStateException("the item should say to click again");
                    menu.clicked(slotOf(menu, "The Drowned Reservoir"), 0, net.minecraft.world.inventory.ClickType.PICKUP, bot);
                    phase = 2;
                    return false;
                }
                return manager.get().runOf(ID).map(r -> "water".equals(r.dungeonType())).orElse(false) && heard("Entering The Drowned Reservoir (Water, tier 3, Normal).")
                        && !(bot.containerMenu instanceof com.ascensiondungeons.mc.DiveMenu);
            }
        }));
        inside(manager, "run 1");
        steps.add(new Step("the portal's bar points this player's own way out, and says their first extraction is free", 100, () -> {
            String text = dive().portalText(ID);
            if (text == null) return false;
            AscensionDungeonsLog.LOG.info("DIVE-E2E portal bar: \"{}\"", text);
            return text.matches("Extraction portal ([\u2190-\u2199] \\d+m(, (above|below) you)?|right here): your first extraction is free");
        }));
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
            AscensionDungeonsLog.LOG.info("DIVE-E2E portal bar in the Depths: \"{}\"", d.portalText(ID));
            return "EXTRACT".equals(d.guideGoal(ID)) && d.guideText(ID).startsWith("Guide: Get back to the extraction portal and leave") && heard("Guide: Your loot is at risk")
                    && d.portalText(ID).matches("Extraction portal [\u2190-\u2199] \\d+m.*: your first extraction is free");
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
                // Within reach to fight: a ranged Pokémon keeps about seven blocks off by design.
                if (nearest.getY() > top.getY() - 5 || nearest.distanceToSqr(bot) > 9 * 9) return false;
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
        steps.add(new Step("the first knock-out is softer: the player is alive, keeps what was banked and half of what was not" + (cobblemon ? ", and loses the item hidden on a Pokémon" : ""), 100,
                new BooleanSupplier() {
            boolean hit; int unbanked, banked;
            @Override public boolean getAsBoolean() {
                if (!hit) {
                    unbanked = markedItems();
                    banked = totalItems() - unbanked;
                    if (!dive().firstKnockOutSoft(bot)) throw new IllegalStateException("a first knock-out should be the softer one");
                    bot.setInvulnerable(false);
                    bot.invulnerableTime = 0;
                    bot.hurt(bot.level().damageSources().generic(), 100000f);
                    hit = true;
                    return false;
                }
                if (bot.level() != server.overworld()) return false;
                // Half is lost, rounded the player's way: of seven items three go and four stay.
                int kept = unbanked - unbanked / 2;
                boolean heldOk = !cobblemon || Party.afterKnockOut();
                AscensionDungeonsLog.LOG.info("DIVE-E2E after the first knock-out: alive {} health {}; had {} banked and {} unbanked item(s), now {} with {} marked (expected {})", bot.isAlive(),
                        bot.getHealth(), banked, unbanked, totalItems(), markedItems(), banked + kept);
                return bot.isAlive() && bot.getHealth() > 0 && unbanked > 1 && markedItems() == 0 && totalItems() == banked + kept && heldOk && !progress().firstKnockOutSoft()
                        && heard("Knocked out. It was your first, so you keep half of what you had not banked");
            }
        }));
        steps.add(new Step("the knocked-out player is out of the run", 100, () -> manager.get().runOf(ID).isEmpty()));
        steps.add(new Step("the knock-out ends with a recap: how it ended, the stage, what was kept and lost, and a tip", 40, () -> {
            List<String> recap = new ArrayList<>();
            boolean in = false;
            for (String line : chat) { if (line.startsWith("Dive recap:")) in = true; if (in && recap.size() < 5) recap.add(line.trim()); }
            AscensionDungeonsLog.LOG.info("DIVE-E2E recap after the knock-out: {}", recap);
            return recap.size() == 5 && recap.get(0).equals("Dive recap: The Drowned Reservoir") && recap.get(1).startsWith("You were knocked out")
                    && recap.get(1).endsWith("It was your first, so you kept half of what was unbanked.") && recap.get(4).startsWith("Tip: Next time a knock-out takes everything unbanked.") && recap.get(2).matches("Outbreak: stage \\d \\(.+\\) after \\d+m \\d\\ds\\. Chests opened: \\d+(\\. Deepest: The [A-Za-z ]+)?\\.")
                    && recap.get(3).matches("Kept: \\d+ items?, \\d+ wins?\\. Lost: \\d+ items?, \\d+ wins?\\.") && !recap.get(3).contains("Lost: 0 items") && recap.get(4).startsWith("Tip: ");
        }));
        steps.add(new Step("the knock-out is explained on screen and in chat, and the player is still being taught", 40, () -> {
            AscensionDungeonsLog.LOG.info("DIVE-E2E after the knock-out: titles {}, knock-outs {}, graduated {}", titles, progress().knockOuts(), progress().graduated());
            boolean ok = heard("Guide: Knocked out") && heard("This was your first knock-out, so you kept half of what you had not banked.") && heard("everything you brought with you, is always safe")
                    && titles.contains("Knocked out") && progress().knockOuts() == 1 && progress().rookie() && dive() == null;
            chat.clear();
            titles.clear();
            return ok;
        }));
        steps.add(new Step("back at the picker, the check at the door now names the weak team" + (cobblemon ? "" : " (skipped without Cobblemon: only the tier is named)"), 40, new BooleanSupplier() {
            boolean sent;
            @Override public boolean getAsBoolean() {
                if (!sent) { command("dive"); sent = true; return false; }
                if (!(bot.containerMenu instanceof com.ascensiondungeons.mc.DiveMenu menu)) return false;
                String normal = String.join(" ", lore(menu.getSlot(0).getItem()));
                AscensionDungeonsLog.LOG.info("DIVE-E2E picker after run 1: {}", normal);
                bot.closeContainer();
                // The stand-in player now has two level 5 Pokémon; the gentlest dungeon's enemies start at 12.
                return cobblemon ? normal.contains("Your strongest Pokémon is level 5. Enemies here start at level 12 and reach 30.") && !normal.contains("You look ready.")
                        : normal.contains("You look ready.");
            }
        }));
        steps.add(new Step("the party commands answer: alone, and nobody invites themselves", 20, () -> {
            command("dive party");
            command("dive invite DiveBot");
            command("dive accept");
            return heard("You are diving alone.") && heard("You are already in your own party.") && heard("You have no invitation to accept")
                    && com.ascensiondungeons.mc.DiveEntry.current.party(ID).equals(Set.of(ID));
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
        portalRun(server, manager, cobblemon, "run 2", true);
        steps.add(new Step("the recap of the free extraction says nothing was lost", 40, () -> {
            AscensionDungeonsLog.LOG.info("DIVE-E2E recap after the free extraction: {}", chat.stream().filter(l -> l.startsWith("Dive recap") || l.startsWith("  ")).toList());
            // Everything carried was kept: what was in the inventory and, with Cobblemon, the item hidden on a Pokémon.
            return heard("Dive recap: The Drowned Reservoir") && heard("You extracted free: your first extraction cost nothing.") && recapNumber("Kept: ") >= itemsBefore
                    && heard("Lost: 0 items, 0 wins.") && heard("Tip: From now on leaving costs 35% of your unbanked loot");
        }));
        steps.add(new Step("the free first extraction is explained, finishes the tutorial and is used up", 40, () -> {
            AscensionDungeonsLog.LOG.info("DIVE-E2E after the free extraction: titles {}, extractions {}, graduated {}, still free {}", titles, progress().extractions(),
                    progress().graduated(), progress().firstExtractionFree());
            boolean ok = heard("Guide: Extracted") && heard("Your first extraction was free") && heard("From now on, leaving costs 35% of your unbanked loot") && titles.contains("Extracted")
                    && progress().graduated() && !progress().rookie() && !progress().firstExtractionFree();
            chat.clear();
            titles.clear();
            return ok;
        }));
        steps.add(new Step("starting the tutorial over does not give the free extraction back", 20, () -> {
            var p = progress();
            long seen = p.seenBits();
            p.reset();
            boolean ok = p.rookie() && !p.firstExtractionFree();
            // put things back as they were: a graduate who has heard those lessons
            var store = com.ascensiondungeons.mc.OnboardingStore.of(bot.server);
            var tag = store.save(new net.minecraft.nbt.CompoundTag(), bot.server.registryAccess());
            ok &= !com.ascensiondungeons.mc.OnboardingStore.load(tag, bot.server.registryAccess()).progress(ID).firstExtractionFree();
            return ok;
        }));
        addRun(manager, "run 3");
        steps.add(new Step("after starting over the player is taught again from the welcome, with a Guide bar, and the toll is the toll", 300, () -> {
            OutbreakDive d = dive();
            if (!d.lessonsTold(ID).contains("WELCOME") || d.guideText(ID) == null) return false;
            return "LOOT".equals(d.guideGoal(ID)) && !d.firstExtractionFree(bot) && heard("Guide: Welcome to your first dive");
        }));
        portalRun(server, manager, cobblemon, "run 3", false);
        steps.add(new Step("the recap of the toll counts exactly what it took", 40, () -> {
            AscensionDungeonsLog.LOG.info("DIVE-E2E recap after the toll: {}", chat.stream().filter(l -> l.startsWith("Dive recap") || l.startsWith("  ")).toList());
            // The toll took the shown items, and with Cobblemon the one hidden on a Pokémon as well.
            int lost = recapNumber("Lost: ");
            return heard("You extracted by paying the toll.") && recapNumber("Kept: ") == itemsBefore - shown && (cobblemon ? lost > shown : lost == shown) && heard("Tip: ");
        }));
        steps.add(new Step("paying the toll is explained, and graduates the player", 40, () -> {
            boolean ok = heard("Guide: Extracted") && heard("You paid the toll and kept the rest") && progress().graduated();
            chat.clear();
            titles.clear();
            return ok;
        }));
        addRun(manager, "run 4");
        steps.add(new Step("a graduate has no Guide bar and is told only about what they have never met", 300, () -> {
            OutbreakDive d = dive();
            if (!heard("An Outbreak is building here")) return false;
            return d.guideText(ID) == null && d.guideGoal(ID) == null && !heard("Guide: Welcome") && !heard("Guide: The Outbreak");
        }));
        steps.add(new Step("run 4: loot a chest", 100, new BooleanSupplier() {
            boolean used;
            @Override public boolean getAsBoolean() {
                bot.setInvulnerable(true);
                if (!used) { for (int i = 0; i < bot.getInventory().getContainerSize(); i++) bot.getInventory().setItem(i, ItemStack.EMPTY); use(freshChest()); used = true; return false; }
                if (!takeAll()) return false;
                itemsBefore = totalItems();
                return count()[1] > 0;
            }
        }));
        steps.add(new Step("run 4: challenging calls out a tough gatekeeper" + (cobblemon ? " from the boss pool" : ""), 200, new BooleanSupplier() {
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
        steps.add(new Step("run 4: beating the gatekeeper opens the portal and the player leaves with everything", 400, new BooleanSupplier() {
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
        steps.add(new Step("the recap after beating the gatekeeper says everything was kept", 40, () ->
                heard("You extracted past the gatekeeper and kept everything.") && heard("Lost: 0 items, 0 wins.") && heard("Tip: ")));
        depthSteps(manager, "run 5");
        // A second knock-out is the ordinary one: everything unbanked goes.
        addRun(manager, "run 6");
        steps.add(new Step("run 6: loot a chest", 100, new BooleanSupplier() {
            boolean used;
            @Override public boolean getAsBoolean() {
                bot.setInvulnerable(true);
                if (!used) { chat.clear(); titles.clear(); for (int i = 0; i < bot.getInventory().getContainerSize(); i++) bot.getInventory().setItem(i, ItemStack.EMPTY); use(freshChest()); used = true; return false; }
                if (!takeAll()) return false;
                return markedItems() > 0;
            }
        }));
        steps.add(new Step("run 6: a second knock-out takes everything unbanked, and says so", 100, new BooleanSupplier() {
            boolean hit; int unbanked;
            @Override public boolean getAsBoolean() {
                if (!hit) {
                    unbanked = markedItems();
                    if (dive().firstKnockOutSoft(bot)) throw new IllegalStateException("the softer knock-out was already used");
                    bot.setInvulnerable(false);
                    bot.invulnerableTime = 0;
                    bot.hurt(bot.level().damageSources().generic(), 100000f);
                    hit = true;
                    return false;
                }
                if (bot.level() != server.overworld()) return false;
                AscensionDungeonsLog.LOG.info("DIVE-E2E after the second knock-out: had {} unbanked item(s), now {} item(s); knock-outs {}", unbanked, totalItems(), progress().knockOuts());
                // The count restarted when the tutorial was started over in run 3; that the softer one is used did not.
                return bot.isAlive() && unbanked > 0 && totalItems() == 0 && progress().knockOuts() == 1 && progress().everKnockedOut() && heard("Knocked out. You keep what you banked")
                        && heard("Guide: Knocked out") && heard("You lost the loot you had not banked.") && heard("You were knocked out.") && !heard("It was your first")
                        && recapNumber("Lost: ") == unbanked;
            }
        }));
        steps.add(new Step("run 6: the player is out of the run", 100, () -> manager.get().runOf(ID).isEmpty()));
        partySteps(server, manager, cobblemon);
    }

    /** A set piece of this dive, from the dive's own list: {kind, room, band, x, y, z, state}. */
    private static String[] piece(String kind) {
        for (String line : dive().pieceLines()) if (line.startsWith(kind + " ")) return line.split(" ");
        throw new IllegalStateException("this dive has no " + kind);
    }

    private static String alphaRoom;

    /** The first Pokémon capsule in the stand-in player's inventory, or null. */
    private static ItemStack capsule() {
        for (int i = 0; i < bot.getInventory().getContainerSize(); i++) {
            ItemStack s = bot.getInventory().getItem(i);
            if (com.ascensiondungeons.mc.Capsules.read(s) != null) return s;
        }
        return null;
    }

    private static void teleport(int x, int y, int z) { bot.teleportTo((ServerLevel) bot.level(), x + 0.5, y, z + 0.5, 0f, 0f); }

    /** The Depths' bands and set pieces, in one dive: a band's name on screen, a nest cleared, an Alpha slain, a hazard room. */
    private static void depthSteps(Supplier<RunManager> manager, String label) {
        addRun(manager, label, "water", "hard");
        steps.add(new Step(label + ": the run is on the difficulty it was asked for, and says so at the door", 40, () ->
                dive().difficulty() == com.ascensiondungeons.outbreak.Tactics.Difficulty.HARD && heard("Difficulty: Hard")));
        steps.add(new Step(label + ": the Depths hold two nests, two Alphas in the deep band, and three hazard rooms, with each nest's heart built in the world", 40, () -> {
            List<String> lines = dive().pieceLines();
            AscensionDungeonsLog.LOG.info("DIVE-E2E set pieces: {}", lines);
            long nests = lines.stream().filter(l -> l.startsWith("NEST ")).count(), alphas = lines.stream().filter(l -> l.startsWith("ALPHA ")).count(),
                    hazards = lines.stream().filter(l -> l.startsWith("HAZARD ")).count();
            boolean deepAlphas = lines.stream().filter(l -> l.startsWith("ALPHA ")).allMatch(l -> l.split(" ")[2].equals("2"));
            String[] n = piece("NEST");
            var heart = bot.level().getBlockState(new BlockPos(Integer.parseInt(n[3]), Integer.parseInt(n[4]), Integer.parseInt(n[5])));
            return nests == 2 && alphas == 2 && hazards == 3 && deepAlphas && heart.is(net.minecraft.world.level.block.Blocks.REINFORCED_DEEPSLATE);
        }));
        steps.add(new Step(label + ": crossing into the deep band puts its name on screen", 100, new BooleanSupplier() {
            boolean moved;
            @Override public boolean getAsBoolean() {
                bot.setInvulnerable(true);
                String[] a = piece("ALPHA");
                if (!moved) { titles.clear(); teleport(Integer.parseInt(a[3]) + 9, Integer.parseInt(a[4]), Integer.parseInt(a[5])); moved = true; return false; }
                AscensionDungeonsLog.LOG.info("DIVE-E2E band: now {}, deepest {}, titles {}", dive().bandOf(ID), dive().deepestBand(ID), titles);
                return dive().bandOf(ID) == 2 && titles.contains("The Abyssal Cistern");
            }
        }));
        steps.add(new Step(label + ": a skirmisher put next to the player backs off and keeps its distance", 400, new BooleanSupplier() {
            Mob m;
            int samples, inRange;
            @Override public boolean getAsBoolean() {
                bot.setInvulnerable(true);
                bot.setHealth(bot.getMaxHealth());
                if (m == null) {
                    for (Mob o : dive().enemyMobs()) if (dive().bossPhase(o) < 0) o.discard();
                    BlockPos at = depthsEntrance();
                    teleport(at.getX(), at.getY(), at.getZ());
                    List<Mob> wave = dive().testWave(1, "skirmisher", bot.position());
                    if (wave.isEmpty()) return false;
                    m = wave.get(0);
                    m.teleportTo(bot.getX() + 2, bot.getY(), bot.getZ());
                    samples = -60;                                   // three seconds to get its bearings
                    return false;
                }
                if (!m.isAlive()) throw new IllegalStateException("the skirmisher died");
                if (bot.server.getTickCount() % 10 != 0) return false;
                samples += 10;
                if (samples <= 0) return false;
                double d = m.distanceTo(bot);
                if (d >= 4.5 && d <= 11.5) inRange++;
                if (samples < 120) return false;
                AscensionDungeonsLog.LOG.info("DIVE-E2E skirmisher {} ({}): within range in {} of 12 samples, last {}", m.getName().getString(), m.getClass().getSimpleName(), inRange, String.format("%.1f", d));
                m.discard();
                return inRange >= 9;
            }
        }));
        steps.add(new Step(label + ": a wave of six round the player takes turns: only a few press in while the rest hold a ring", 600, new BooleanSupplier() {
            List<Mob> wave;
            int samples, ringSeen, worst;
            @Override public boolean getAsBoolean() {
                bot.setInvulnerable(true);
                bot.setHealth(bot.getMaxHealth());
                if (wave == null) {
                    for (Mob o : dive().enemyMobs()) if (dive().bossPhase(o) < 0) o.discard();
                    wave = dive().testWave(6, "hunter", bot.position());
                    if (wave.size() < 4) { for (Mob o : wave) o.discard(); wave = null; return false; }
                    samples = -120;
                    return false;
                }
                if (bot.server.getTickCount() % 10 != 0) return false;
                samples += 10;
                if (samples <= 0) return false;
                int close = 0, ring = 0, pressing = 0;
                for (Mob o : wave) {
                    if (!o.isAlive()) continue;
                    double d = o.distanceTo(bot);
                    if (d < 2.8) close++;
                    if (d >= 4.5 && d <= 8.5) ring++;
                    if (dive().pressing(o)) pressing++;
                }
                worst = Math.max(worst, close);
                if (ring > 0) ringSeen++;
                if (samples < 100) return false;
                int allowed = com.ascensiondungeons.outbreak.Tactics.profile(dive().difficulty(), 0, 0).maxPressing();
                AscensionDungeonsLog.LOG.info("DIVE-E2E wave of {}: at most {} within reach at once (allowed {} pressing +1), some on the ring in {} of 10 samples, {} marked pressing",
                        wave.size(), worst, allowed, ringSeen, pressing);
                for (Mob o : wave) o.discard();
                return worst <= allowed + 1 && ringSeen >= 5 && pressing <= allowed + 1;
            }
        }));
        steps.add(new Step(label + ": an Alpha worn below two thirds calls help, below a third is enraged, and winds up a charge with a line on the ground", 800, new BooleanSupplier() {
            int phase;
            Mob alpha;
            String report;
            @Override public boolean getAsBoolean() {
                bot.setInvulnerable(true);
                bot.setHealth(bot.getMaxHealth());
                String[] a = piece("ALPHA");
                if (phase == 0) {
                    // the second Alpha, so the first is left for the hoard step
                    String[] second = null;
                    for (String line : dive().pieceLines()) if (line.startsWith("ALPHA ") && !line.split(" ")[1].equals(a[1])) second = line.split(" ");
                    if (second == null) throw new IllegalStateException("only one alpha");
                    teleport(Integer.parseInt(second[3]) + 4, Integer.parseInt(second[4]), Integer.parseInt(second[5]));
                    a = second;
                    alphaRoom = second[1];
                    phase = 1;
                    return false;
                }
                if (phase == 1) {
                    List<Mob> mobs = dive().pieceMobs(alphaRoom);
                    for (Mob m : mobs) if (dive().bossPhase(m) >= 0) alpha = m;
                    if (alpha == null) return false;
                    chat.clear();
                    alpha.setHealth(alpha.getMaxHealth() * 0.6f);
                    phase = 2;
                    return false;
                }
                if (phase == 2) {
                    if (dive().bossPhase(alpha) < 1) return false;
                    report = dive().tacticsReport();
                    alpha.setHealth(alpha.getMaxHealth() * 0.3f);
                    phase = 3;
                    return false;
                }
                if (phase == 3) {
                    if (dive().bossPhase(alpha) < 2) return false;
                    phase = 4;
                    return false;
                }
                // keep the player in front of it, inside its ground, until it winds up; then stand still on the line
                if (phase == 4 && (bot.distanceTo(alpha) < 3.5 || bot.distanceTo(alpha) > 7)) teleport((int) Math.floor(alpha.getX()) + 5, (int) Math.floor(alpha.getY()), (int) Math.floor(alpha.getZ()));
                if (phase == 4) {
                    if (!dive().charging(alpha)) return false;
                    phase = 5;                                        // wound up: now stand on the line and let it come
                    return false;
                }
                if (dive().charging(alpha) || !dive().tacticsReport().contains("charges=1")) return false;
                AscensionDungeonsLog.LOG.info("DIVE-E2E alpha phases: after help {}; after the charge {}; health {} of {}", report, dive().tacticsReport(), alpha.getHealth(), alpha.getMaxHealth());
                boolean helped = !report.contains("summons=0"), hit = !dive().tacticsReport().contains("chargeHits=0");
                alpha.kill();
                return helped && hit;
            }
        }));
        steps.add(new Step(label + ": the Alpha's hoard is sealed, the Alpha takes its post as the player comes near (a new one if it is removed), and slaying it opens the hoard", 600, new BooleanSupplier() {
            int phase;
            int[] hoard;
            UUID gone;
            @Override public boolean getAsBoolean() {
                bot.setInvulnerable(true);
                bot.setHealth(bot.getMaxHealth());
                String[] a = piece("ALPHA");
                if (phase == 0) {
                    // The hoard is the one nearest this Alpha's post.
                    double best = Double.MAX_VALUE;
                    for (String line : dive().featureLines()) {
                        String[] f = line.split(" ");
                        if (!f[0].equals("HOARD")) continue;
                        int[] c = {Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4])};
                        double d = Math.hypot(c[0] - Integer.parseInt(a[3]), c[2] - Integer.parseInt(a[5]));
                        if (d < best) { best = d; hoard = c; }
                    }
                    if (hoard == null) throw new IllegalStateException("no hoard beside the alpha");
                    chat.clear();
                    use(hoard);
                    phase = 1;
                    return false;
                }
                if (phase == 1) {
                    if (bot.containerMenu != bot.inventoryMenu) throw new IllegalStateException("a sealed hoard opened");
                    List<Mob> alpha = dive().pieceMobs(a[1]);
                    if (alpha.isEmpty()) return false;
                    // First, an Alpha that leaves the world without falling: a new one must take the post, or the hoard stays sealed for good.
                    if (gone == null) { gone = alpha.get(0).getUUID(); alpha.get(0).discard(); return false; }
                    if (alpha.get(0).getUUID().equals(gone)) return false;
                    AscensionDungeonsLog.LOG.info("DIVE-E2E a removed alpha was replaced by {}", alpha.get(0).getName().getString());
                    Mob m = alpha.get(0);
                    AscensionDungeonsLog.LOG.info("DIVE-E2E alpha: {} health {} of {} ({})", m.getName().getString(), m.getHealth(), m.getMaxHealth(), m.getClass().getSimpleName());
                    if (!m.getName().getString().startsWith("Alpha ") || !m.hasGlowingTag()) throw new IllegalStateException("not an alpha: " + m.getName().getString());
                    m.kill();
                    phase = 2;
                    return false;
                }
                if (phase == 2) {
                    if (!piece("ALPHA")[6].equals("SLAIN") && !String.join(" ", piece("ALPHA")).endsWith("done")) return false;
                    if (!heard("has fallen! Its hoard is unsealed.") || !heard("A win is added to your haul.")) return false;
                    for (int i = 0; i < bot.getInventory().getContainerSize(); i++) bot.getInventory().setItem(i, ItemStack.EMPTY);
                    use(hoard);
                    phase = 3;
                    return false;
                }
                if (!takeAll()) return false;
                AscensionDungeonsLog.LOG.info("DIVE-E2E hoard: {}", describeInventory());
                return markedItems() > 0;
            }
        }));
        steps.add(new Step(label + ": a chest can hold a Pokémon capsule: run loot, bound to its finder, and the party hears of a pseudo-legendary", 200, new BooleanSupplier() {
            boolean used;
            @Override public boolean getAsBoolean() {
                bot.setInvulnerable(true);
                if (!used) {
                    chat.clear();
                    for (int i = 0; i < bot.getInventory().getContainerSize(); i++) bot.getInventory().setItem(i, ItemStack.EMPTY);
                    command("dungeon dive find pseudo");                 // the operator command a playtest uses
                    use(freshChest());
                    used = true;
                    return false;
                }
                if (!takeAll()) return false;
                ItemStack capsule = capsule();
                if (capsule == null) throw new IllegalStateException("no capsule in the chest: " + describeInventory());
                var c = com.ascensiondungeons.mc.Capsules.read(capsule);
                AscensionDungeonsLog.LOG.info("DIVE-E2E capsule: {} ({}), data {}, lore {}", capsule.getHoverName().getString(),
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(capsule.getItem()), c, lore(capsule));
                return RunLoot.runOf(capsule) != null && c.getUUID("owner").equals(ID) && c.getString("tier").equals("PSEUDO")
                        && List.of("lapras", "feebas").contains(c.getString("species")) && c.getString("floor").equals("rare")
                        && capsule.getHoverName().getString().startsWith("Capsule: ") && heard("found a Pseudo-legendary");
            }
        }));
        steps.add(new Step(label + ": a capsule cannot be released while it is at risk", 20, () -> {
            ItemStack capsule = capsule();
            var r = com.ascensiondungeons.mc.Capsules.use(bot, capsule);
            return r == net.minecraft.world.InteractionResult.FAIL && capsule() != null && capsule().getCount() == 1;
        }));
        steps.add(new Step(label + ": banked, the capsule releases its Pokémon to the finder's team" + (FabricLoader.getInstance().isModLoaded("cobblemon") ? "" : " (needs Cobblemon)"), 40, new BooleanSupplier() {
            String species;
            net.minecraft.world.InteractionResult result;
            @Override public boolean getAsBoolean() {
                if (!FabricLoader.getInstance().isModLoaded("cobblemon")) return true;
                if (result == null) {
                    dive().operatorCheckpoint();
                    ItemStack capsule = capsule();
                    if (capsule == null || RunLoot.runOf(capsule) != null) return false;
                    species = com.ascensiondungeons.mc.Capsules.read(capsule).getString("species");
                    int before = Party.size(bot);
                    chat.clear();
                    result = com.ascensiondungeons.mc.Capsules.use(bot, capsule);
                    AscensionDungeonsLog.LOG.info("DIVE-E2E released {}: {}, party {} -> {}", species, result, before, Party.size(bot));
                    return false;
                }
                // the message is read off the connection on the next tick
                return result == net.minecraft.world.InteractionResult.SUCCESS && capsule() == null && Party.has(bot, species) && heard("joined your team!");
            }
        }));
        steps.add(new Step(label + ": an Outbreak Nest wakes when the player comes near, sends its waves one after another, and opens its cache when cleared", 2400, new BooleanSupplier() {
            boolean moved, opened;
            int lastWave;
            int[] cache;
            @Override public boolean getAsBoolean() {
                bot.setInvulnerable(true);
                bot.setHealth(bot.getMaxHealth());
                String[] n = piece("NEST");
                if (!moved) {
                    for (String line : dive().featureLines()) {
                        String[] f = line.split(" ");
                        if (f[0].equals("NEST_CHEST")) {
                            int[] c = {Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4])};
                            if (Math.abs(c[0] - Integer.parseInt(n[3])) < 24 && Math.abs(c[2] - Integer.parseInt(n[5])) < 24) cache = c;
                        }
                    }
                    chat.clear();
                    titles.clear();
                    teleport(Integer.parseInt(n[3]) + 3, Integer.parseInt(n[4]), Integer.parseInt(n[5]));
                    moved = true;
                    return false;
                }
                String state = piece("NEST")[6];
                if (state.startsWith("AWAKE")) {
                    int wave = Integer.parseInt(state.split("/")[1]);
                    if (wave != lastWave) { AscensionDungeonsLog.LOG.info("DIVE-E2E nest wave {}: {} enemies", wave, dive().pieceMobs(n[1]).size()); lastWave = wave; }
                    // Stand in for the fight: the wave's enemies fall, a few at a time.
                    for (Mob m : dive().pieceMobs(n[1])) { if (bot.server.getTickCount() % 10 == 0) { m.kill(); break; } }
                    return false;
                }
                if (!state.startsWith("CLEARED")) return false;
                if (!opened) {
                    if (!titles.contains("The Nest wakes") || !titles.contains("Nest cleared") || lastWave < 2 || !heard("The Outbreak Nest is cleared!")) throw new IllegalStateException("the nest was not run as it should be: " + titles + " wave " + lastWave);
                    for (int i = 0; i < bot.getInventory().getContainerSize(); i++) bot.getInventory().setItem(i, ItemStack.EMPTY);
                    use(cache);
                    opened = true;
                    return false;
                }
                if (!takeAll()) return false;
                AscensionDungeonsLog.LOG.info("DIVE-E2E nest cache: {}", describeInventory());
                return markedItems() > 0;
            }
        }));
        steps.add(new Step(label + ": a hazard room casts the dungeon's spells at a player inside it", 900, new BooleanSupplier() {
            boolean moved;
            int before;
            @Override public boolean getAsBoolean() {
                bot.setInvulnerable(true);
                bot.setHealth(bot.getMaxHealth());
                String[] h = piece("HAZARD");
                if (!moved) { before = dive().hazardPulses(); teleport(Integer.parseInt(h[3]), Integer.parseInt(h[4]), Integer.parseInt(h[5])); moved = true; return false; }
                return dive().hazardPulses() > before;
            }
        }));
        steps.add(new Step(label + ": walking out, the recap names the deepest band and the set pieces beaten", 200, new BooleanSupplier() {
            boolean left;
            @Override public boolean getAsBoolean() {
                if (!left) { chat.clear(); manager.get().leave(ID); left = true; return false; }
                if (manager.get().runOf(ID).isPresent()) return false;
                return heard("Deepest: The Abyssal Cistern") && heard("Nests and Alphas beaten: 3");
            }
        }));
    }

    private static boolean heard2(String text) { for (String line : chat2) if (line.contains(text)) return true; return false; }

    /** Two players: an invitation, an acceptance, one dive together, and each told only what is theirs to hear. */
    private static void partySteps(MinecraftServer server, Supplier<RunManager> manager, boolean cobblemon) {
        steps.add(new Step("a second player joins the server", 100, () -> {
            if (bot2 == null) {
                GameProfile profile = new GameProfile(ID2, "DiveBot2");
                bot2 = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
                Connection connection = new Connection(PacketFlow.SERVERBOUND);
                channel2 = new EmbeddedChannel(connection);
                server.getPlayerList().placeNewPlayer(connection, bot2, CommonListenerCookie.createInitial(profile, false));
            }
            return server.getPlayerList().getPlayer(ID2) == bot2;
        }));
        steps.add(new Step("an invitation alone changes nothing; accepting it makes a party of two with the inviter as leader", 40, new BooleanSupplier() {
            int phase;
            @Override public boolean getAsBoolean() {
                var entry = com.ascensiondungeons.mc.DiveEntry.current;
                if (phase == 0) {
                    chat.clear();
                    chat2.clear();
                    command("dive invite DiveBot2");
                    phase = 1;
                    return false;
                }
                if (phase == 1) {
                    if (!heard("Invited DiveBot2.") || !heard2("DiveBot invites you to dive with them.")) return false;
                    if (!entry.party(ID).equals(Set.of(ID)) || !entry.party(ID2).equals(Set.of(ID2))) throw new IllegalStateException("nobody joins without accepting");
                    server.getCommands().performPrefixedCommand(bot2.createCommandSourceStack(), "dive accept");
                    phase = 2;
                    return false;
                }
                List<UUID> party = List.copyOf(entry.party(ID2));
                AscensionDungeonsLog.LOG.info("DIVE-E2E party: {}", party);
                return party.equals(List.of(ID, ID2)) && heard("DiveBot2 joined your party.") && heard2("You joined the party.");
            }
        }));
        steps.add(new Step("only the leader chooses: the guest is told so, and the leader's picker shows the party and the guest's own concerns", 60, new BooleanSupplier() {
            int phase;
            @Override public boolean getAsBoolean() {
                if (phase == 0) {
                    server.getCommands().performPrefixedCommand(bot2.createCommandSourceStack(), "dive");
                    command("dive");
                    phase = 1;
                    return false;
                }
                if (!(bot.containerMenu instanceof com.ascensiondungeons.mc.DiveMenu menu)) return false;
                String partyItem = menu.getSlot(com.ascensiondungeons.mc.DiveMenu.PARTY).getItem().getHoverName().getString();
                String normal = String.join(" ", lore(menu.getSlot(0).getItem()));
                AscensionDungeonsLog.LOG.info("DIVE-E2E party picker: \"{}\"; Normal: {}", partyItem, normal);
                return partyItem.equals("Your party: 2 players") && heard2("Your party's leader, DiveBot, chooses the dungeon.") && !(bot2.containerMenu instanceof com.ascensiondungeons.mc.DiveMenu)
                        && (!cobblemon || normal.contains("DiveBot2: You have no Pokémon with you."));
            }
        }));
        steps.add(new Step("the leader picks and both players go into the same dive", 2400, new BooleanSupplier() {
            int clicks;
            @Override public boolean getAsBoolean() {
                bot.setInvulnerable(true);
                bot2.setInvulnerable(true);
                var run = manager.get().runOf(ID);
                if (run.isEmpty()) {
                    // One click when there is nothing to warn about, two when there is.
                    if (clicks < 2 && bot.containerMenu instanceof com.ascensiondungeons.mc.DiveMenu menu) { menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, bot); clicks++; }
                    return false;
                }
                OutbreakDive d = dive();
                if (d == null || run.get().state() != RunState.ACTIVE || bot2.level() != bot.level()) return false;
                AscensionDungeonsLog.LOG.info("DIVE-E2E party dive: {} members in {}, {} click(s)", run.get().members().size(), run.get().dungeonType(), clicks);
                return run.get().members().size() == 2 && manager.get().runOf(ID2).map(r -> r.id().equals(run.get().id())).orElse(false) && "normal".equals(run.get().dungeonType())
                        && bot.level() != server.overworld() && heard2("DiveBot is taking the party into The Hearthwarden Village");
            }
        }));
        steps.add(new Step("in one party each player hears only what is theirs: the newcomer is taught, the graduate is not, and each has their own way out", 400, () -> {
            OutbreakDive d = dive();
            if (!d.lessonsTold(ID2).contains("WELCOME") || d.guideText(ID2) == null || d.portalText(ID) == null || d.portalText(ID2) == null) return false;
            AscensionDungeonsLog.LOG.info("DIVE-E2E party: newcomer told {} with guide \"{}\" and portal \"{}\"; graduate told {} with portal \"{}\"", d.lessonsTold(ID2), d.guideText(ID2),
                    d.portalText(ID2), d.lessonsTold(ID), d.portalText(ID));
            return heard2("Guide: Welcome to your first dive") && !heard("Guide: Welcome to your first dive") && heard("An Outbreak is building here") && !heard2("An Outbreak is building here")
                    && d.guideText(ID) == null && !d.lessonsTold(ID).contains("WELCOME")
                    && d.portalText(ID2).endsWith(": your first extraction is free") && d.portalText(ID).endsWith(": beat its gatekeeper, or pay the 35% toll");
        }));
        steps.add(new Step("both walk out, each with their own recap, and the party is still together for another go", 200, new BooleanSupplier() {
            boolean left;
            @Override public boolean getAsBoolean() {
                if (!left) { manager.get().leave(ID); manager.get().leave(ID2); left = true; return false; }
                if (manager.get().runOf(ID).isPresent() || manager.get().runOf(ID2).isPresent() || bot.level() != server.overworld() || bot2.level() != server.overworld()) return false;
                return heard("You left without extracting.") && heard2("You left without extracting.") && heard("Dive recap: The Hearthwarden Village") && heard2("Dive recap: The Hearthwarden Village")
                        && com.ascensiondungeons.mc.DiveEntry.current.party(ID).size() == 2;
            }
        }));
        steps.add(new Step("a capsule is bound: another player cannot release it", 20, () -> {
            ItemStack c = com.ascensiondungeons.mc.Capsules.make(new com.ascensiondungeons.outbreak.PokemonFinds.Find(com.ascensiondungeons.outbreak.PokemonFinds.Tier.STANDARD, "psyduck", 12),
                    "uncommon", 3, bot, "The Drowned Reservoir", "Normal");
            var r = com.ascensiondungeons.mc.Capsules.use(bot2, c);
            return r == net.minecraft.world.InteractionResult.FAIL && c.getCount() == 1;
        }));
        steps.add(new Step("the leader leaving ends the party for both", 20, () -> {
            command("dive leave");
            var entry = com.ascensiondungeons.mc.DiveEntry.current;
            return entry.party(ID).equals(Set.of(ID)) && entry.party(ID2).equals(Set.of(ID2)) && heard("You ended your party.") && heard2("DiveBot ended the party.");
        }));
    }

    /** Loot two chests, hide some of it, read the extraction window and leave through it: free the first time, by the toll after. */
    private static void portalRun(MinecraftServer server, Supplier<RunManager> manager, boolean cobblemon, String label, boolean free) {
        steps.add(new Step(label + ": loot a chest", 100, new BooleanSupplier() {
            boolean used;
            @Override public boolean getAsBoolean() {
                bot.setInvulnerable(true);
                if (!used) { for (int i = 0; i < bot.getInventory().getContainerSize(); i++) bot.getInventory().setItem(i, ItemStack.EMPTY); use(freshChest()); used = true; return false; }
                if (!takeAll()) return false;
                carried = count()[0];
                return count()[1] > 0;
            }
        }));
        steps.add(new Step(label + ": loot a second chest", 100, new BooleanSupplier() {
            boolean used;
            @Override public boolean getAsBoolean() {
                if (!used) { use(freshChest()); used = true; return false; }
                return takeAll();
            }
        }));
        if (cobblemon) steps.add(new Step(label + ": some of the loot is hidden on a Pokémon", 40, () -> Party.hideSecond(bot)));
        steps.add(new Step(label + ": stepping on the portal shows exactly what the 35% toll would take", 200, new BooleanSupplier() {
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
                String pay = menu.getSlot(com.ascensiondungeons.mc.TollMenu.PAY).getItem().getHoverName().getString();
                AscensionDungeonsLog.LOG.info("DIVE-E2E toll screen: {} unbanked item(s), shows {} to lose {} (expected {}); pay button: {}", marked, shown, names, expected, pay);
                // A first extraction is free, and says so; after that the button is the toll.
                if (!pay.startsWith(free ? "Leave free" : "Pay the toll") || dive().firstExtractionFree(bot) != free) return false;
                // Before paying, try to take a shown item: nothing may move.
                menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, bot);
                menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, bot);
                return marked > 0 && shown == expected && totalItems() == itemsBefore && menu.getCarried().isEmpty();
            }
        }));
        steps.add(new Step(label + (free ? ": the first extraction is free: nothing shown is taken" + (cobblemon ? ", not even the hidden item" : "")
                : ": paying the toll takes exactly the shown items" + (cobblemon ? " plus the hidden one" : "")) + ", and the player is let out", 100, new BooleanSupplier() {
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
                AscensionDungeonsLog.LOG.info("DIVE-E2E after {}: {} item(s) (had {}, toll showed {}), {} still marked", free ? "the free extraction" : "the toll", totalItems(), itemsBefore, shown, c[1]);
                if (free) {
                    // Everything leaves with the player, unmarked, the item on the Pokémon too.
                    boolean held = !cobblemon || Party.secondKeptBanked();
                    return totalItems() == itemsBefore && c[1] == 0 && held && heard("Your first extraction is free: nothing was taken.");
                }
                boolean heldOk = !cobblemon || Party.secondIsEmpty();
                return totalItems() == itemsBefore - shown && c[1] == 0 && heldOk && heard("Toll paid");
            }
        }));
        steps.add(new Step(label + ": the player is out of the run", 100, () -> manager.get().runOf(ID).isEmpty()));
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
        // This script checks the toll in every dungeon, so the stand-in player's free first extraction is taken as used.
        steps.add(new Step("the free first extraction is set aside for this script", 20, () -> { progress().forfeitFirstExtraction(); return !progress().firstExtractionFree(); }));
        for (String type : com.ascensiondungeons.gen.authored.AuthoredDungeons.ids()) {
            if (com.ascensiondungeons.gen.authored.AuthoredLayout.get(type).outbreak() == null) continue;
            addRun(manager, type, type);
            steps.add(new Step(type + ": the Depths hold its set pieces, each nest's heart is built, and every Alpha is in the deep band", 40, () -> {
                List<String> lines = dive().pieceLines();
                int nests = 0, alphas = 0, hazards = 0;
                for (String line : lines) {
                    String[] f = line.split(" ");
                    switch (f[0]) {
                        case "NEST" -> {
                            nests++;
                            if (!bot.level().getBlockState(new BlockPos(Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]))).is(net.minecraft.world.level.block.Blocks.REINFORCED_DEEPSLATE)) return false;
                        }
                        case "ALPHA" -> { alphas++; if (!f[2].equals("2")) return false; }
                        case "HAZARD" -> hazards++;
                        default -> { }
                    }
                }
                AscensionDungeonsLog.LOG.info("DIVE-E2E {} set pieces: {}", type, lines);
                return nests == 2 && alphas == 2 && hazards == 3;
            }));
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

    private static void addRun(Supplier<RunManager> manager, String label, String type) { addRun(manager, label, type, null); }

    private static void addRun(Supplier<RunManager> manager, String label, String type, String difficulty) {
        steps.add(new Step(label + ": a " + type + " run is accepted" + (difficulty == null ? "" : " on " + difficulty), 40, new BooleanSupplier() {
            Run run;
            @Override public boolean getAsBoolean() {
                if (run == null) {
                    opened.clear();
                    var result = manager.get().request(Set.of(ID), type, difficulty);
                    if (!result.ok()) throw new IllegalStateException("request refused: " + result.error());
                    run = result.run();
                }
                return run != null;
            }
        }));
        inside(manager, label);
    }

    private static void inside(Supplier<RunManager> manager, String label) {
        steps.add(new Step(label + ": the player is inside an Outbreak dive", 2400, () -> {
            OutbreakDive d = dive();
            if (d == null || manager.get().runOf(ID).map(r -> r.state() != RunState.ACTIVE).orElse(true)) return false;
            AscensionDungeonsLog.LOG.info("DIVE-E2E {}", d.describe());
            return true;
        }));
    }

    /** Runs a command as the stand-in player, the way typing it would. */
    private static void command(String command) {
        bot.server.getCommands().performPrefixedCommand(bot.createCommandSourceStack(), command);
    }

    /** The lines of an item's description, as plain text. */
    private static List<String> lore(ItemStack stack) {
        List<String> out = new ArrayList<>();
        var lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
        if (lore != null) for (var line : lore.lines()) out.add(line.getString());
        return out;
    }

    /** The picker's slot for a dungeon, by its name; -1 if it is not offered. */
    private static int slotOf(com.ascensiondungeons.mc.DiveMenu menu, String name) {
        for (int i = 0; i < 18; i++) if (menu.getSlot(i).getItem().getHoverName().getString().equals(name)) return i;
        return -1;
    }

    private static int shown, itemsBefore;

    /** How many items of unbanked dive loot are in the stand-in player's inventory (items, not stacks). */
    private static int markedItems() {
        int n = 0;
        for (int i = 0; i < bot.getInventory().getContainerSize(); i++) { ItemStack s = bot.getInventory().getItem(i); if (RunLoot.runOf(s) != null) n += s.getCount(); }
        return n;
    }

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

        /** The second Pokémon still holds its item and the item is no longer marked. Takes the item away again for the next run. */
        static int size(ServerPlayer player) {
            int n = 0;
            for (var p : com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player)) if (p != null) n++;
            return n;
        }

        /** The party or the PC holds a Pokémon of this species. */
        static boolean has(ServerPlayer player, String species) {
            for (var p : com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player))
                if (p != null && p.getSpecies().getName().toLowerCase(java.util.Locale.ROOT).equals(species)) return true;
            for (var p : com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getPC(player))
                if (p != null && p.getSpecies().getName().toLowerCase(java.util.Locale.ROOT).equals(species)) return true;
            return false;
        }

        static boolean secondKeptBanked() {
            var second = (com.cobblemon.mod.common.pokemon.Pokemon) secondPokemon;
            boolean ok = !second.heldItem().isEmpty() && RunLoot.runOf(second.heldItem()) == null;
            second.swapHeldItem(ItemStack.EMPTY, false, false);
            return ok;
        }

        /** The first Pokémon still holds its banked item; the second lost the unbanked one. */
        static boolean afterKnockOut() {
            var first = (com.cobblemon.mod.common.pokemon.Pokemon) firstPokemon;
            var second = (com.cobblemon.mod.common.pokemon.Pokemon) secondPokemon;
            AscensionDungeonsLog.LOG.info("DIVE-E2E held items after knock-out: first {} second {}", first.heldItem(), second.heldItem());
            return !first.heldItem().isEmpty() && second.heldItem().isEmpty();
        }
    }
}
