package com.ascensiondungeons.mc;

import com.ascensiondungeons.gen.cinderforge.CinderforgeLayout;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Global event hooks for Cinderforge run levels. They do nothing outside one: every handler first asks the director for
 * the session that owns the level.
 */
public final class CinderforgeEvents {
    private CinderforgeEvents() {}

    public static void register(Supplier<CinderforgeDirector> director) {
        // Nothing in the dungeon may be broken, or built on, except through the mechanics below.
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
            CinderforgeDirector d = director.get();
            return d == null || d.sessionFor(level) == null;
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> {
            CinderforgeDirector d = director.get();
            if (level.isClientSide || d == null) return InteractionResult.PASS;
            CinderforgeDirector.Session s = d.sessionFor(level);
            if (s == null) return InteractionResult.PASS;
            // Striking a glowing heat vent during the Forge Heart fight cools it; nothing else can be hit.
            if (player instanceof ServerPlayer sp) s.strikeBlock(sp, pos);
            return InteractionResult.FAIL;
        });
        // Diagnostic, off unless -Dascensiondungeons.debug=true: which damage source hurt a player inside a run, and where.
        if (Boolean.getBoolean("ascensiondungeons.debug"))
            net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
                CinderforgeDirector d = director.get();
                if (d != null && entity instanceof ServerPlayer p && d.sessionFor(p.level()) != null)
                    com.ascensiondungeons.AscensionDungeonsLog.LOG.info("[cf-debug] {} took {} from {} (attacker {}) at {}",
                            p.getName().getString(), amount, source.getMsgId(), source.getEntity() == null ? "-" : source.getEntity().getType().toShortString(),
                            p.blockPosition().toShortString());
                return true;
            });
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            CinderforgeDirector d = director.get();
            if (d != null) d.onDeath(entity);
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            CinderforgeDirector d = director.get();
            if (level.isClientSide || d == null || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            CinderforgeDirector.Session s = d.sessionFor(level);
            if (s == null) return InteractionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            int[] sc = CinderforgeChunkGenerator.toSchematic(pos);
            var layout = s.generator().layout();
            BlockState state = level.getBlockState(pos);

            // The cistern levers must be pulled in order.
            for (CinderforgeLayout.Lever lv : layout.data().levers) {
                if (lv.pos[0] != sc[0] || lv.pos[1] != sc[1] || lv.pos[2] != sc[2]) continue;
                if (lv.stage == s.stage() + 1) {
                    s.leverPulled(lv.stage);          // the vanilla toggle runs after we return PASS
                    return InteractionResult.PASS;
                }
                sp.sendSystemMessage(Component.literal(lv.stage <= s.stage() ? "This mechanism has already been thrown."
                        : "The mechanism is jammed. Another lever must be pulled first.").withStyle(ChatFormatting.GRAY));
                return InteractionResult.FAIL;
            }
            // The sealed door of the Barracks wants the Forge Key.
            if (layout.data().eastPlug.contains(sc[0], sc[1], sc[2]) && hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
                s.useEastPlug(sp, player.getItemInHand(hand));
                return InteractionResult.SUCCESS;
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
