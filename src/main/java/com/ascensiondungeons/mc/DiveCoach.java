package com.ascensiondungeons.mc;

import com.ascensiondungeons.outbreak.Onboarding;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.phys.Vec3;

/**
 * The first-dive tutorial inside one dive: tells each player their lessons (chat, a title for the big moments, a chime) and
 * shows a new player the Guide bar with what to do next and which way it is. Everything is sent to one player at a time, so
 * a veteran in the same party is not lectured. The rules are {@link Onboarding}'s; this class only delivers.
 */
final class DiveCoach {
    /** Where a goal is, for one player, in the world. Null when there is nowhere to point. */
    interface Targets { Vec3 target(ServerPlayer player, Onboarding.Goal goal); }

    private final ServerLevel level;
    private final OnboardingStore store;
    private final boolean hasDepths;
    private final int tollPercent;
    private final Targets targets;
    private final Map<UUID, Onboarding.Coach> coaches = new HashMap<>();
    private final Map<UUID, ServerBossEvent> bars = new HashMap<>();
    private final Map<UUID, String> shown = new HashMap<>();

    DiveCoach(ServerLevel level, boolean hasDepths, int tollPercent, Targets targets) {
        this.level = level;
        this.store = OnboardingStore.of(level.getServer());
        this.hasDepths = hasDepths;
        this.tollPercent = tollPercent;
        this.targets = targets;
    }

    private Onboarding.Coach coach(UUID player) {
        return coaches.computeIfAbsent(player, id -> new Onboarding.Coach(store.progress(id), Onboarding.Pace.DEFAULT, hasDepths));
    }

    /** Whether this player gets the lesson-by-lesson opening instead of the ordinary briefing. Decided before their coach starts. */
    boolean coached(ServerPlayer p) { return store.progress(p.getUUID()).coached(); }

    void notice(ServerPlayer p, Onboarding.Lesson lesson, long now) { coach(p.getUUID()).notice(lesson, now); }

    void lootedChest(ServerPlayer p, boolean inDepths) { coach(p.getUUID()).lootedChest(inDepths); }

    void enteredDepths(ServerPlayer p) { coach(p.getUUID()).enteredDepths(); }

    /** The player is leaving through the portal: they graduate, and are told so at once. */
    void extracted(ServerPlayer p, boolean paidToll, long now) {
        Onboarding.Coach c = coach(p.getUUID());
        c.extracted();
        tell(p, c.tellNow(paidToll ? Onboarding.Lesson.TOLL_PAID : Onboarding.Lesson.EXTRACTED, now));
        leave(p);
    }

    void knockedOut(ServerPlayer p, long now) {
        Onboarding.Coach c = coach(p.getUUID());
        c.knockedOut();
        tell(p, c.tellNow(Onboarding.Lesson.KNOCKED_OUT, now));
        leave(p);
    }

    /** The player is out of the dive: their Guide bar goes and their progress is saved. */
    void leave(ServerPlayer p) {
        ServerBossEvent bar = bars.remove(p.getUUID());
        if (bar != null) bar.removeAllPlayers();
        shown.remove(p.getUUID());
        Onboarding.Coach c = coaches.get(p.getUUID());
        if (c != null && c.pollChanged()) store.setDirty();
    }

    void tick(long now, List<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            Onboarding.Coach c = coach(p.getUUID());
            tell(p, c.next(now));
            if (now % 10 == 0) guide(p, c);
            if (c.pollChanged()) store.setDirty();
        }
        // A bar whose player has gone elsewhere is taken down.
        if (now % 20 == 0) bars.entrySet().removeIf(e -> {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (p != null && p.level() == level && players.contains(p)) return false;
            e.getValue().removeAllPlayers();
            shown.remove(e.getKey());
            return true;
        });
    }

    private void guide(ServerPlayer p, Onboarding.Coach c) {
        Onboarding.Goal goal = c.goal();
        ServerBossEvent bar = bars.get(p.getUUID());
        if (goal == null) {
            if (bar != null) { bar.removeAllPlayers(); bars.remove(p.getUUID()); shown.remove(p.getUUID()); }
            return;
        }
        Vec3 to = targets.target(p, goal);
        String text = to == null ? "Guide: " + goal.text() : Onboarding.guideLine(goal, to.x - p.getX(), to.y - p.getY(), to.z - p.getZ(), p.getYRot());
        if (bar == null) {
            bar = new ServerBossEvent(Component.literal(text), BossEvent.BossBarColor.PINK, BossEvent.BossBarOverlay.PROGRESS);
            bar.addPlayer(p);
            bars.put(p.getUUID(), bar);
        } else if (!text.equals(shown.get(p.getUUID()))) {
            bar.setName(Component.literal(text));
        }
        shown.put(p.getUUID(), text);
        bar.setProgress((goal.ordinal() + 1) / (float) Onboarding.Goal.values().length);
    }

    private void tell(ServerPlayer p, Onboarding.Lesson lesson) {
        if (lesson == null) return;
        p.sendSystemMessage(Component.literal("Guide: ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal(lesson.title()).withStyle(st -> st.withColor(ChatFormatting.YELLOW).withBold(false))));
        for (String line : lesson.lines(tollPercent)) p.sendSystemMessage(Component.literal("  " + line).withStyle(ChatFormatting.GRAY));
        if (lesson.weight() == Onboarding.Weight.HEADLINE) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(lesson.subtitle()).withStyle(ChatFormatting.GRAY)));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(lesson.title()).withStyle(ChatFormatting.GOLD)));
        }
        p.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.MASTER, 0.7f, lesson.urgent() ? 0.8f : 1.3f);
        store.setDirty();
        com.ascensiondungeons.AscensionDungeonsLog.LOG.info("[guide] {} was told {}", p.getGameProfile().getName(), lesson);
    }

    void close() {
        for (ServerBossEvent bar : bars.values()) bar.removeAllPlayers();
        bars.clear();
        shown.clear();
        store.setDirty();
    }

    // ---- test aids -----------------------------------------------------------------------------------------------

    /** The lessons this player was told in this dive, in order. */
    List<String> told(UUID player) {
        Onboarding.Coach c = coaches.get(player);
        List<String> out = new ArrayList<>();
        if (c != null) for (Onboarding.Lesson l : c.told()) out.add(l.name());
        return out;
    }

    /** The Guide bar's text for this player, or null when they have none. */
    String guideText(UUID player) { return bars.containsKey(player) ? shown.get(player) : null; }

    /** The goal the Guide bar is on, or null. */
    String goal(UUID player) {
        Onboarding.Coach c = coaches.get(player);
        return c == null || c.goal() == null ? null : c.goal().name();
    }
}
