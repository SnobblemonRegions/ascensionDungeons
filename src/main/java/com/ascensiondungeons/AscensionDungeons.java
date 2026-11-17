package com.ascensiondungeons;

import com.ascensiondungeons.mc.DungeonCommands;
import com.ascensiondungeons.mc.FantasyRunHost;
import com.ascensiondungeons.mc.OrphanSweeper;
import com.ascensiondungeons.run.RunConfig;
import com.ascensiondungeons.run.RunManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class AscensionDungeons implements ModInitializer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private volatile RunManager manager;
    private volatile java.util.List<com.ascensiondungeons.mc.ArenaDirector> arenas = java.util.List.of();
    private volatile com.ascensiondungeons.mc.CinderforgeDirector cinderforge;
    private volatile com.ascensiondungeons.mc.AuthoredDirector authored;

    @Override
    public void onInitialize() {
        if (System.getenv("DUNGEONSPIKE_BENCH") != null) com.ascensiondungeons.spike.Bench.install();
        if (System.getenv("DUNGEONSPIKE_E2E") != null) com.ascensiondungeons.spike.E2E.install();
        if (System.getenv("DUNGEONSPIKE_DIVE") != null) com.ascensiondungeons.spike.DiveE2E.install(() -> manager);

        net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.CHUNK_GENERATOR,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ascensiondungeons", "district"),
                com.ascensiondungeons.mc.DungeonChunkGenerator.CODEC);
        net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.CHUNK_GENERATOR,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ascensiondungeons", "type_pilot"),
                com.ascensiondungeons.mc.TypeDungeonChunkGenerator.CODEC);
        net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.CHUNK_GENERATOR,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ascensiondungeons", "cinderforge"),
                com.ascensiondungeons.mc.CinderforgeChunkGenerator.CODEC);
        net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.CHUNK_GENERATOR,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ascensiondungeons", "authored"),
                com.ascensiondungeons.mc.AuthoredChunkGenerator.CODEC);
        com.ascensiondungeons.gen.authored.AuthoredDungeons.ids().forEach(com.ascensiondungeons.gen.authored.AuthoredLayout::get);
        com.ascensiondungeons.mc.AuthoredEvents.register(() -> authored);
        com.ascensiondungeons.gen.cinderforge.CinderforgeLayout.get();
        com.ascensiondungeons.mc.CinderforgeEvents.register(() -> cinderforge);
        com.ascensiondungeons.gen.type.TypeCatalogue.all();
        com.ascensiondungeons.gen.type.FireModules.preload();
        RunConfig cfg = loadConfig();
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) ->
                new DungeonCommands(() -> manager).register(dispatcher));

        // A Pokémon capsule is used, not thrown: right-clicking the air or a block releases it (or says why not).
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            var stack = player.getItemInHand(hand);
            if (world.isClientSide() || !(player instanceof net.minecraft.server.level.ServerPlayer sp)) return net.minecraft.world.InteractionResultHolder.pass(stack);
            var r = com.ascensiondungeons.mc.Capsules.use(sp, stack);
            return r == null ? net.minecraft.world.InteractionResultHolder.pass(stack)
                    : r == net.minecraft.world.InteractionResult.SUCCESS ? net.minecraft.world.InteractionResultHolder.success(stack) : net.minecraft.world.InteractionResultHolder.fail(stack);
        });
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClientSide() || !(player instanceof net.minecraft.server.level.ServerPlayer sp)) return net.minecraft.world.InteractionResult.PASS;
            if (com.ascensiondungeons.mc.Capsules.read(player.getItemInHand(hand)) == null) return net.minecraft.world.InteractionResult.PASS;
            var r = com.ascensiondungeons.mc.Capsules.use(sp, player.getItemInHand(hand));
            return r == null ? net.minecraft.world.InteractionResult.PASS : r;
        });
        // A player who logs off leaves their dive party, and invitations to or from them are dropped.
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            com.ascensiondungeons.mc.DiveEntry entry = com.ascensiondungeons.mc.DiveEntry.current;
            if (entry != null) entry.forget(handler.getPlayer().getUUID());
        });
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            OrphanSweeper.sweep(server);
            com.ascensiondungeons.mc.DungeonTemplates.preload();
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            FantasyRunHost host = new FantasyRunHost(server, cfg);
            manager = new RunManager(host, cfg, () -> ThreadLocalRandom.current().nextLong());
            java.util.List<com.ascensiondungeons.mc.ArenaDirector> systems = new java.util.ArrayList<>();
            // The Cinderforge mechanics use only vanilla content, so they run whether or not Cobblemon is installed.
            com.ascensiondungeons.mc.CinderforgeDirector cinder = new com.ascensiondungeons.mc.CinderforgeDirector(server, () -> manager, host);
            cinderforge = cinder;
            systems.add(cinder);
            com.ascensiondungeons.mc.AuthoredDirector auth = new com.ascensiondungeons.mc.AuthoredDirector(server, () -> manager, host);
            authored = auth;
            systems.add(auth);
            com.ascensiondungeons.mc.RunLoot.runOfDimension = auth::diveRunOf;
            com.ascensiondungeons.mc.RunLoot.chestListener = auth::chestLooted;
            com.ascensiondungeons.mc.RunLoot.extraLoot = auth::extraLoot;
            com.ascensiondungeons.mc.AuthoredBridges.PokemonSpawner pk = FabricLoader.getInstance().isModLoaded("cobblemon") ? newPokemonSpawner() : null;
            com.ascensiondungeons.mc.AuthoredBridges.RaidBridge rb = FabricLoader.getInstance().isModLoaded("cobbleraids") ? newRaidBridge(cfg) : null;
            com.ascensiondungeons.mc.AuthoredBridges.Ascension asc = FabricLoader.getInstance().isModLoaded("ascensionlib") ? com.ascensiondungeons.mc.AscensionBridge.tryCreate() : null;
            // Built whenever Cobblemon is present: Outbreak dives always use it, other authored dungeons only with authoredCombat.
            com.ascensiondungeons.mc.AuthoredBridges.Combat combat = pk != null ? newCombat(cfg) : null;
            if (pk != null) {
                com.ascensiondungeons.mc.RunLoot.PartyItems items = newPartyItems();
                com.ascensiondungeons.mc.RunLoot.partyItems = items;
                com.ascensiondungeons.mc.DiveEntry.teamInfo = (com.ascensiondungeons.mc.DiveEntry.TeamInfo) items;
                com.ascensiondungeons.mc.Capsules.releaser = newReleaser();
            }
            com.ascensiondungeons.mc.DiveEntry.current = new com.ascensiondungeons.mc.DiveEntry(server, () -> manager, cfg);
            auth.bridges(cfg, pk, rb, asc, combat);
            host.ascension(asc);
            com.ascensiondungeons.mc.AuthoredDirector.current = auth;
            AscensionDungeonsLog.LOG.info("Authored dungeons: Pokemon {}, CobbleRaids bosses {}, AscensionLib {}, real-time combat {}", pk != null, rb != null, asc != null, combat != null);
            if (FabricLoader.getInstance().isModLoaded("cobblemon")) {
                systems.add(new com.ascensiondungeons.mc.WildPopulation(server, cfg, () -> manager, host));
                systems.add(new com.ascensiondungeons.mc.TypePopulation(cfg, () -> manager, host));
                systems.add(new com.ascensiondungeons.mc.CinderforgePopulation(cfg, () -> manager, host));
            }
            if (FabricLoader.getInstance().isModLoaded("cobbleraids")) {
                systems.add(newArenaDirector(server, cfg, host));
                var typeBoss = new com.ascensiondungeons.mc.TypeBossDirector(server, cfg, () -> manager, host);
                systems.add(typeBoss);
                host.onBoss(typeBoss::begin);
            } else {
                AscensionDungeonsLog.LOG.info("CobbleRaids is not installed: arena raids are disabled.");
            }
            host.onRelease(id -> systems.forEach(s -> s.runReleased(id)));
            arenas = systems;
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            RunManager m = manager;
            if (m == null) return;
            try {
                m.tick(server.getTickCount());
                for (var a : arenas) a.tick(server.getTickCount());
            } catch (RuntimeException e) {
                // A bug here must not take the server down; the next tick retries.
                AscensionDungeonsLog.LOG.error("RunManager tick failed", e);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            RunManager m = manager;
            if (m != null) {
                for (var run : m.runs()) for (var director : arenas) director.runReleased(run.id());
                m.shutdown();
            }
            manager = null;
            cinderforge = null;
            authored = null;
            com.ascensiondungeons.mc.AuthoredDirector.current = null;
            com.ascensiondungeons.mc.RunLoot.runOfDimension = null;
            com.ascensiondungeons.mc.RunLoot.chestListener = null;
            com.ascensiondungeons.mc.RunLoot.partyItems = null;
            com.ascensiondungeons.mc.RunLoot.extraLoot = null;
            com.ascensiondungeons.mc.Capsules.releaser = null;
            com.ascensiondungeons.mc.DiveEntry.teamInfo = null;
            com.ascensiondungeons.mc.DiveEntry.current = null;
            arenas = java.util.List.of();
        });
    }

    /** Kept separate so Cobblemon / CobbleRaids classes load only when the mod is present. */
    private com.ascensiondungeons.mc.AuthoredBridges.PokemonSpawner newPokemonSpawner() { return new com.ascensiondungeons.mc.AuthoredPokemon(); }

    private com.ascensiondungeons.mc.RunLoot.PartyItems newPartyItems() { return new com.ascensiondungeons.mc.AuthoredPartyItems(); }

    private com.ascensiondungeons.mc.Capsules.Releaser newReleaser() { return new com.ascensiondungeons.mc.CapsuleRelease(); }

    private com.ascensiondungeons.mc.AuthoredBridges.Combat newCombat(RunConfig cfg) { return new com.ascensiondungeons.mc.AuthoredCombat(cfg.combatFaintSeconds, cfg.combatDamageMultiplier, cfg.moveCooldownBaseSeconds, cfg.moveCooldownSecondsPerPower); }

    private com.ascensiondungeons.mc.AuthoredBridges.RaidBridge newRaidBridge(RunConfig cfg) { return new com.ascensiondungeons.mc.AuthoredRaidBridge(cfg); }

    private com.ascensiondungeons.mc.ArenaDirector newArenaDirector(net.minecraft.server.MinecraftServer server, RunConfig cfg, FantasyRunHost host) {
        return new com.ascensiondungeons.mc.RaidArenas(server, cfg, () -> manager, host);
    }

    private static RunConfig loadConfig() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("ascensiondungeons.json");
        try {
            if (Files.exists(file)) {
                RunConfig cfg = GSON.fromJson(Files.readString(file), RunConfig.class);
                cfg.validate();
                return cfg;
            }
            RunConfig defaults = new RunConfig();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(defaults));
            return defaults;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }
}
