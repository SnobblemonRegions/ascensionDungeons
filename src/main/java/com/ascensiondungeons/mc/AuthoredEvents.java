package com.ascensiondungeons.mc;

import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Global event hooks for authored run levels. They do nothing outside one: every handler first asks the director. */
public final class AuthoredEvents {
    private AuthoredEvents() {}

    /** True while a reduced hit is being re-applied, so the handler does not reduce it again. Server thread only. */
    private static boolean reducing;

    public static void register(Supplier<AuthoredDirector> director) {
        // Nothing in the dungeon may be broken, or built on, except through its mechanics.
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
            AuthoredDirector d = director.get();
            return d == null || d.sessionFor(level) == null;
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> {
            AuthoredDirector d = director.get();
            if (level.isClientSide || d == null) return InteractionResult.PASS;
            AuthoredDirector.Session s = d.sessionFor(level);
            if (s == null) return InteractionResult.PASS;
            if (player instanceof ServerPlayer sp) s.strike(sp, pos);
            return InteractionResult.FAIL;
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            AuthoredDirector d = director.get();
            if (d != null) d.onDeath(entity);
        });
        // Elites and gatekeepers that are Pokémon are tough by taking a share of each hit (their health cannot be raised).
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (reducing) return true;
            AuthoredDirector d = director.get();
            if (d == null) return true;
            try {
                AuthoredDirector.Session s = d.sessionFor(entity.level());
                if (s == null || s.dive() == null) return true;
                s.dive().hurt(entity, source, amount);           // who is hurting whom: enemies remember it
                double tough = s.dive().toughness(entity);
                if (tough <= 1.0) return true;
                reducing = true;
                try { entity.hurt(source, (float) (amount / tough)); }
                finally { reducing = false; }
                return false;
            } catch (RuntimeException e) {
                com.ascensiondungeons.AscensionDungeonsLog.LOG.error("Toughness handling failed; letting the full hit through", e);
                return true;
            }
        });
        // Nobody dies inside an Outbreak dive: a lethal hit knocks the player out of the run with what they banked.
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            AuthoredDirector d = director.get();
            if (d == null || !(entity instanceof ServerPlayer sp)) return true;
            try {
                AuthoredDirector.Session s = d.sessionFor(sp.level());
                return s == null || s.dive() == null || s.dive().knockedOut(sp, source);
            } catch (RuntimeException e) {
                com.ascensiondungeons.AscensionDungeonsLog.LOG.error("Knock-out handling failed; letting the death through", e);
                return true;
            }
        });
        // Loot from a dive that ended while the player was away (or died with a crash) was never banked: take it back at login.
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            try {
                AuthoredDirector d = director.get();
                ServerPlayer sp = handler.getPlayer();
                int removed = RunLoot.sweep(sp, run -> d != null && d.diveLiveFor(run, sp.getUUID()));
                if (removed > 0) {
                    sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("Unbanked loot from a dungeon run that ended while you were away was lost (" + removed + " stack(s)).")
                            .withStyle(net.minecraft.ChatFormatting.RED));
                    com.ascensiondungeons.AscensionDungeonsLog.LOG.info("[run-loot] swept {} stale stack(s) from {} at login", removed, sp.getGameProfile().getName());
                }
            } catch (RuntimeException e) {
                com.ascensiondungeons.AscensionDungeonsLog.LOG.error("Login loot sweep failed", e);
            }
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            AuthoredDirector d = director.get();
            if (level.isClientSide || d == null || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            AuthoredDirector.Session s = d.sessionFor(level);
            if (s == null) return InteractionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            int[] sc = s.generator().toSchematic(pos);
            BlockState state = level.getBlockState(pos);

            boolean[] allow = new boolean[1];
            if (s.leverClicked(sp, sc, allow)) return allow[0] ? InteractionResult.PASS : InteractionResult.FAIL;
            if (hand == InteractionHand.MAIN_HAND && s.lockClicked(sp, sc, player.getItemInHand(hand))) return InteractionResult.SUCCESS;
            if (s.dive() != null) {
                // Altars, sealed timer chests, growing outbreak chests and strongboxes.
                InteractionResult dived = s.dive().blockUsed(sp, pos, hand == InteractionHand.MAIN_HAND);
                if (dived != null) return dived;
            }
            if (state.is(Blocks.CHEST) || state.getBlock().getClass().getName().startsWith("noobanidus.mods.lootr")
                    || state.getBlock().getDescriptionId().contains("lootr")) return InteractionResult.PASS;
            // No beds (sleeping would move the respawn point), no building, no buckets.
            if (state.getBlock() instanceof BedBlock) return InteractionResult.FAIL;
            var held = player.getItemInHand(hand);
            if (held.getItem() instanceof BlockItem || held.getItem() instanceof BucketItem || held.is(Items.FLINT_AND_STEEL)
                    || held.is(Items.FIRE_CHARGE)) return InteractionResult.FAIL;
            return InteractionResult.PASS;
        });
    }
}
