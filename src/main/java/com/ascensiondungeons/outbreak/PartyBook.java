package com.ascensiondungeons.outbreak;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who dives with whom. A player invites others; each accepts for themselves, so nobody is pulled into a dungeon they did not
 * choose. The inviter leads: the leader picks the dungeon and everyone in the party goes in together. Pure: no Minecraft.
 */
public final class PartyBook {
    public enum Result {
        OK,
        /** A player cannot invite themselves. */
        SELF,
        /** The party already has as many players as a run allows. */
        FULL,
        /** The guest is already in a party (their own or someone else's). */
        TAKEN,
        /** The inviter is a guest in someone else's party; only a leader invites. */
        NOT_LEADER,
        /** There is no invitation to accept, or it has run out. */
        NO_INVITE,
        /** The player is in no party. */
        NOT_IN_PARTY
    }

    private record Invite(UUID leader, long until) {}

    private final int maxSize;
    private final long inviteTicks;
    /** A leader's guests, in the order they joined. A leader with no guests has no entry. */
    private final Map<UUID, Set<UUID>> guests = new LinkedHashMap<>();
    private final Map<UUID, UUID> leaderOf = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();

    /** @param maxSize the most players in a party, leader included; @param inviteTicks how long an invitation stands */
    public PartyBook(int maxSize, long inviteTicks) {
        if (maxSize < 1 || inviteTicks < 1) throw new IllegalArgumentException("a party has at least one player and an invitation lasts at least a tick");
        this.maxSize = maxSize;
        this.inviteTicks = inviteTicks;
    }

    public int maxSize() { return maxSize; }

    /** The leader invites a guest. The newest invitation to a guest replaces an older one. */
    public Result invite(UUID leader, UUID guest, long now) {
        if (leader.equals(guest)) return Result.SELF;
        if (leaderOf.containsKey(leader)) return Result.NOT_LEADER;
        if (leaderOf.containsKey(guest) || guests.containsKey(guest)) return Result.TAKEN;
        if (party(leader).size() >= maxSize) return Result.FULL;
        invites.put(guest, new Invite(leader, now + inviteTicks));
        return Result.OK;
    }

    /** The guest accepts the invitation they hold. */
    public Result accept(UUID guest, long now) {
        Invite invite = invites.remove(guest);
        if (invite == null || now > invite.until()) return Result.NO_INVITE;
        if (leaderOf.containsKey(guest) || guests.containsKey(guest)) return Result.TAKEN;
        if (leaderOf.containsKey(invite.leader())) return Result.NO_INVITE;            // the inviter has since joined someone else
        if (party(invite.leader()).size() >= maxSize) return Result.FULL;
        guests.computeIfAbsent(invite.leader(), k -> new LinkedHashSet<>()).add(guest);
        leaderOf.put(guest, invite.leader());
        return Result.OK;
    }

    /** Who invited this player, if the invitation still stands; null otherwise. */
    public UUID invitedBy(UUID guest, long now) {
        Invite invite = invites.get(guest);
        return invite == null || now > invite.until() ? null : invite.leader();
    }

    /** A guest leaves their party; a leader who leaves ends it for everyone. */
    public Result leave(UUID player) {
        UUID leader = leaderOf.remove(player);
        if (leader != null) {
            Set<UUID> set = guests.get(leader);
            if (set != null) { set.remove(player); if (set.isEmpty()) guests.remove(leader); }
            return Result.OK;
        }
        Set<UUID> mine = guests.remove(player);
        if (mine == null) return Result.NOT_IN_PARTY;
        for (UUID g : mine) leaderOf.remove(g);
        return Result.OK;
    }

    /** The player has gone (logged off): they leave any party and any invitation to or from them is dropped. */
    public void forget(UUID player) {
        leave(player);
        invites.remove(player);
        invites.values().removeIf(i -> i.leader().equals(player));
    }

    /** The leader of the party this player is in: themselves when they lead or are alone. */
    public UUID leader(UUID player) { return leaderOf.getOrDefault(player, player); }

    public boolean leads(UUID player) { return !leaderOf.containsKey(player); }

    /** The party this player is in, leader first. A player on their own is a party of one. */
    public Set<UUID> party(UUID player) {
        UUID leader = leader(player);
        Set<UUID> out = new LinkedHashSet<>();
        out.add(leader);
        out.addAll(guests.getOrDefault(leader, Set.of()));
        return out;
    }
}
