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
