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
                if (steps.isEmpty()) script(server, manager);
                if (channel != null) channel.releaseOutbound();
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

    private static void script(MinecraftServer server, Supplier<RunManager> manager) {
        boolean cobblemon = FabricLoader.getInstance().isModLoaded("cobblemon");
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
        addRun(manager, "run 1");
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
        steps.add(new Step("a rarity chest opens and its loot is marked", 100, new BooleanSupplier() {
            boolean used;
            @Override public boolean getAsBoolean() {
                bot.setInvulnerable(true);
                bot.setHealth(bot.getMaxHealth());
                if (!used) { use(features("CHEST").get(0)); used = true; return false; }
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
                if (!used) { use(features("CHEST").get(1)); used = true; return false; }
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
        addRun(manager, "run 2");
        steps.add(new Step("run 2: loot a chest", 100, new BooleanSupplier() {
            boolean used;
            @Override public boolean getAsBoolean() {
                bot.setInvulnerable(true);
                if (!used) { for (int i = 0; i < bot.getInventory().getContainerSize(); i++) bot.getInventory().setItem(i, ItemStack.EMPTY); use(features("CHEST").get(0)); used = true; return false; }
                if (!takeAll()) return false;
                carried = count()[0];
                return count()[1] > 0;
            }
        }));
        steps.add(new Step("run 2: kills near the portal charge it, and standing on it extracts with everything", 3600, () -> {
            OutbreakDive d = dive();
            if (d == null) {
                int[] c = count();
                AscensionDungeonsLog.LOG.info("DIVE-E2E after extraction: stacks {} marked {} (carried {})", c[0], c[1], carried);
                return bot.level() == server.overworld() && c[0] == carried && c[1] == 0;
            }
            Vec3 p = d.portalCentre();
            if (bot.position().distanceToSqr(p) > 1) bot.teleportTo((ServerLevel) bot.level(), p.x, p.y, p.z, 0f, 0f);
            for (Mob m : d.enemyMobs()) if (m.position().distanceToSqr(p) < 20 * 20) m.kill();
            return false;
        }));
        steps.add(new Step("the extracted player is out of the run", 100, () -> manager.get().runOf(ID).isEmpty()));
    }

    private static void addRun(Supplier<RunManager> manager, String label) {
        steps.add(new Step(label + ": a water run is accepted", 40, new BooleanSupplier() {
            Run run;
            @Override public boolean getAsBoolean() {
                if (run == null) {
                    var result = manager.get().request(Set.of(ID), "water");
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

        /** The first Pokémon still holds its banked item; the second lost the unbanked one. */
        static boolean afterKnockOut() {
            var first = (com.cobblemon.mod.common.pokemon.Pokemon) firstPokemon;
            var second = (com.cobblemon.mod.common.pokemon.Pokemon) secondPokemon;
            AscensionDungeonsLog.LOG.info("DIVE-E2E held items after knock-out: first {} second {}", first.heldItem(), second.heldItem());
            return !first.heldItem().isEmpty() && second.heldItem().isEmpty();
        }
    }
}
