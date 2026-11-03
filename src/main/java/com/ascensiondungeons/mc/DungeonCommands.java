package com.ascensiondungeons.mc;

import com.ascensiondungeons.gen.DistrictGrid;
import com.ascensiondungeons.gen.Template;
import com.ascensiondungeons.run.EndReason;
import com.ascensiondungeons.run.Run;
import com.ascensiondungeons.run.RunManager;
import com.ascensiondungeons.run.StartResult;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /dungeon start [players] | leave | status, and operator tools: list, end, retry. */
public final class DungeonCommands {
    private final Supplier<RunManager> manager;

    public DungeonCommands(Supplier<RunManager> manager) { this.manager = manager; }

    public void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("dungeon")
                .then(Commands.literal("types").executes(ctx -> {
                    com.ascensiondungeons.gen.type.TypeCatalogue.all().forEach(t -> reply(ctx, t.id() + ": 5 rooms, 15 landmarks, arena; " + (t.playable() ? "playable blockout" : "planned")));
                    return 1;
                }))
                .then(Commands.literal("type").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(com.ascensiondungeons.gen.type.TypeCatalogue.all().stream().filter(t -> t.playable()).map(t -> t.id()), java.util.stream.Stream.concat(java.util.stream.Stream.of(RunManager.CINDERFORGE), com.ascensiondungeons.gen.authored.AuthoredDungeons.ids().stream())), builder))
                                .executes(ctx -> startType(ctx, Set.of(ctx.getSource().getPlayerOrException().getUUID())))
                                .then(Commands.argument("party", EntityArgument.players()).executes(ctx -> {
                                    Set<UUID> party = new LinkedHashSet<>();
                                    party.add(ctx.getSource().getPlayerOrException().getUUID());
                                    EntityArgument.getPlayers(ctx, "party").forEach(p -> party.add(p.getUUID()));
                                    return startType(ctx, party);
                                }))))
                .then(Commands.literal("start")
                        .executes(ctx -> start(ctx, Set.of(ctx.getSource().getPlayerOrException().getUUID())))
                        .then(Commands.argument("party", EntityArgument.players()).executes(ctx -> {
                            Set<UUID> party = new LinkedHashSet<>();
                            party.add(ctx.getSource().getPlayerOrException().getUUID());
                            EntityArgument.getPlayers(ctx, "party").forEach(p -> party.add(p.getUUID()));
                            return start(ctx, party);
                        })))
                .then(Commands.literal("sendout").requires(src -> src.hasPermission(2)).executes(ctx -> {
                    var ad = AuthoredDirector.current;
                    reply(ctx, ad == null ? "No authored director." : ad.testSendOut(ctx.getSource().getPlayerOrException()));
                    return 1;
                }))
                .then(Commands.literal("inflict").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
                            var ad = AuthoredDirector.current;
                            reply(ctx, ad == null ? "No authored director." : ad.testInflict(ctx.getSource().getPlayerOrException(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind")));
                            return 1;
                        })))
                .then(Commands.literal("bosswin").requires(src -> src.hasPermission(2)).executes(ctx -> {
                    var ad = AuthoredDirector.current;
                    boolean ok = ad != null && ad.forceBossVictory(ctx.getSource().getPlayerOrException().level());
                    reply(ctx, ok ? "Raid boss defeated (admin)." : "No raid-mode boss in this level.");
                    return ok ? 1 : 0;
                }))
                .then(Commands.literal("leave").executes(ctx -> {
                    boolean left = manager.get().leave(ctx.getSource().getPlayerOrException().getUUID());
                    reply(ctx, left ? "You left the dungeon." : "You are not in a dungeon run.");
                    return left ? 1 : 0;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    RunManager m = manager.get();
                    reply(ctx, m.runOf(p.getUUID()).map(r -> describe(r, m.now())).orElse("You are not in a dungeon run."));
                    return 1;
                }))
                .then(Commands.literal("list").requires(s -> s.hasPermission(2)).executes(ctx -> {
                    RunManager m = manager.get();
                    reply(ctx, m.runs().size() + " run(s), " + m.resourceCount() + " holding levels, " + m.queued() + " queued");
                    m.runs().forEach(r -> reply(ctx, r.id() + " " + describe(r, m.now())));
                    return 1;
                }))
                .then(Commands.literal("chest").requires(s -> s.hasPermission(2)).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    var id = net.minecraft.resources.ResourceLocation.parse("lootr:lootr_chest");
                    var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(id);
                    if (block.isEmpty()) {
                        ctx.getSource().sendFailure(Component.literal("Lootr is not installed."));
                        return 0;
                    }
                    var level = p.serverLevel();
                    var pos = p.blockPosition().relative(p.getDirection(), 2);
                    level.setBlockAndUpdate(pos, block.get().defaultBlockState());
                    net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, level.random, pos,
                            net.minecraft.world.level.storage.loot.BuiltInLootTables.SIMPLE_DUNGEON);
                    reply(ctx, "Placed a Lootr chest at " + pos.toShortString() + " in " + level.dimension().location());
                    return 1;
                }))
                .then(Commands.literal("wild").requires(s -> s.hasPermission(2)).executes(ctx -> {
                    var level = ctx.getSource().getPlayerOrException().serverLevel();
                    if (!(level.getChunkSource().getGenerator() instanceof DungeonChunkGenerator)) {
                        ctx.getSource().sendFailure(Component.literal("You are not inside a dungeon run."));
                        return 0;
                    }
                    var counts = new java.util.TreeMap<String, Integer>();
                    level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(
                            com.cobblemon.mod.common.entity.pokemon.PokemonEntity.class), e -> true)
                            .forEach(e -> counts.merge(e.getPokemon().getSpecies().getName().toLowerCase(), 1, Integer::sum));
                    reply(ctx, counts.values().stream().mapToInt(Integer::intValue).sum() + " wild Pokemon in this run: " + counts);
                    return 1;
                }))
                .then(gotoNode())
                .then(Commands.literal("end").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("run", StringArgumentType.word()).executes(ctx -> {
                            boolean ok = withRun(ctx, id -> manager.get().end(id, EndReason.ADMIN));
                            reply(ctx, ok ? "Ending run." : "No such run.");
                            return ok ? 1 : 0;
                        })))
                .then(Commands.literal("retry").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("run", StringArgumentType.word()).executes(ctx -> {
                            boolean ok = withRun(ctx, id -> manager.get().retryCleanup(id));
                            reply(ctx, ok ? "Retrying cleanup." : "No such run in CLEANUP_FAILED.");
                            return ok ? 1 : 0;
                        }))));
    }

    private int startType(CommandContext<CommandSourceStack> ctx, Set<UUID> party) {
        boolean cinderforge = RunManager.CINDERFORGE.equals(StringArgumentType.getString(ctx, "type")) || com.ascensiondungeons.gen.authored.AuthoredDungeons.isAuthored(StringArgumentType.getString(ctx, "type"));
        if (!cinderforge && (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cobblemon")
                || !net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cobbleraids"))) {
            ctx.getSource().sendFailure(Component.literal("The type pilot requires Cobblemon and CobbleRaids on the server."));
            return 0;
        }
        var result = manager.get().request(party, StringArgumentType.getString(ctx, "type"));
        if (!result.ok()) ctx.getSource().sendFailure(Component.literal(result.error()));
        else reply(ctx, "Queued " + StringArgumentType.getString(ctx, "type") + " run " + result.run().id());
        return result.ok() ? 1 : 0;
    }

    /** /dungeon goto <spot> [cx cz]: spots are entry, nav (alias grove), loot, explore (alias spring) and arena. */
    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> gotoNode() {
        var node = Commands.literal("goto").requires(s -> s.hasPermission(2));
        for (String[] spot : new String[][] {{"entry", "entry"}, {"nav", "nav"}, {"grove", "nav"}, {"loot", "loot"},
                {"explore", "explore"}, {"spring", "explore"}, {"arena", "arena"}}) {
            node.then(Commands.literal(spot[0]).executes(ctx -> gotoSpot(ctx, spot[1], 0, 0)));
        }
        return node.then(Commands.literal("cell")
                .then(Commands.argument("cx", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-4, 4))
                        .then(Commands.argument("cz", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-4, 4))
                                .executes(ctx -> gotoSpot(ctx, "centre",
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "cx"),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "cz"))))));
    }

    /** Operator test aid: teleports within the run level you are standing in to a spot in a district. */
    private int gotoSpot(CommandContext<CommandSourceStack> ctx, String what, int cx, int cz)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (!(p.serverLevel().getChunkSource().getGenerator() instanceof DungeonChunkGenerator gen)) {
            ctx.getSource().sendFailure(Component.literal("You are not inside a dungeon run."));
            return 0;
        }
        var cell = gen.grid().cell(cx, cz);
        if (cell == null) {
            ctx.getSource().sendFailure(Component.literal("No district at " + cx + "," + cz));
            return 0;
        }
        int[] spot = what.equals("centre") ? new int[] {Template.CENTER, Template.CENTER}
                : com.ascensiondungeons.gen.layout.Layouts.of(cell.biome()).spots().get(what);
        int[] d = cell.orientation().toWorld(spot[0] - Template.CENTER, spot[1] - Template.CENTER);
        double x = cx * DistrictGrid.CELL + d[0] + 0.5, z = cz * DistrictGrid.CELL + d[1] + 0.5;
        // Face where the template's north points in this district.
        var north = cell.orientation().toWorld(com.ascensiondungeons.gen.Side.N);
        float yaw = switch (north) { case N -> 180f; case E -> 270f; case S -> 0f; case W -> 90f; };
        p.teleportTo(p.serverLevel(), x, 5, z, yaw, 0);
        reply(ctx, "Teleported to the " + what + " of district " + cx + "," + cz + " (" + cell.biome().displayName() + ", doors " + cell.open() + ").");
        return 1;
    }

    private int start(CommandContext<CommandSourceStack> ctx, Set<UUID> party) {
        StartResult r = manager.get().request(party);
        if (!r.ok()) {
            ctx.getSource().sendFailure(Component.literal(r.error()));
            return 0;
        }
        reply(ctx, "Dungeon requested for " + party.size() + " player(s).");
        return 1;
    }

    private static boolean withRun(CommandContext<CommandSourceStack> ctx, java.util.function.Predicate<UUID> action) {
        try {
            return action.test(UUID.fromString(StringArgumentType.getString(ctx, "run")));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String describe(Run r, long now) {
        String left = r.state() == com.ascensiondungeons.run.RunState.ACTIVE
                ? ", " + Math.max(0, (r.deadline() - now) / 20) + "s left" : "";
        return r.state() + " (" + r.members().size() + " player(s)" + left + ")";
    }

    private static void reply(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
    }
}
