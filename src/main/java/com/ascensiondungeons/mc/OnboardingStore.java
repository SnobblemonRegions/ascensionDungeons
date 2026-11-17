package com.ascensiondungeons.mc;

import com.ascensiondungeons.outbreak.Onboarding;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * What each player has learned in the first-dive tutorial, kept with the world's saved data (the overworld's
 * {@code data/ascensiondungeons_onboarding.dat}). Read and written on the server thread only.
 */
public final class OnboardingStore extends SavedData {
    private static final String NAME = "ascensiondungeons_onboarding";
    // Vanilla's storage reads a saved file through a data fixer and needs a type for it. Ours has no fixes of its own;
    // the command-storage type leaves an unknown layout alone.
    private static final Factory<OnboardingStore> FACTORY = new Factory<>(OnboardingStore::new, OnboardingStore::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<UUID, Onboarding.Progress> players = new HashMap<>();

    public static OnboardingStore of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** This player's progress; a player never seen before starts from nothing. Call {@link #setDirty()} after changing it. */
    public Onboarding.Progress progress(UUID player) {
        return players.computeIfAbsent(player, k -> new Onboarding.Progress());
    }

    public int size() { return players.size(); }

    /** Reads a store back from what {@link #save} wrote. */
    public static OnboardingStore load(CompoundTag tag, HolderLookup.Provider registries) {
        OnboardingStore store = new OnboardingStore();
        CompoundTag all = tag.getCompound("players");
        for (String key : all.getAllKeys()) {
            UUID id;
            try { id = UUID.fromString(key); } catch (IllegalArgumentException e) { continue; }
            CompoundTag p = all.getCompound(key);
            store.players.put(id, new Onboarding.Progress(p.getLong("seen"), p.getInt("dives"), p.getInt("extractions"), p.getInt("knockOuts"), p.getBoolean("off"),
                    p.getBoolean("everExtracted"), p.contains("everKnockedOut") ? p.getBoolean("everKnockedOut") : p.getInt("knockOuts") > 0));
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<UUID, Onboarding.Progress> e : players.entrySet()) {
            Onboarding.Progress p = e.getValue();
            CompoundTag one = new CompoundTag();
            one.putLong("seen", p.seenBits());
            one.putInt("dives", p.dives());
            one.putInt("extractions", p.extractions());
            one.putInt("knockOuts", p.knockOuts());
            one.putBoolean("off", p.off());
            one.putBoolean("everExtracted", p.everExtracted());
            one.putBoolean("everKnockedOut", p.everKnockedOut());
            all.put(e.getKey().toString(), one);
        }
        tag.put("players", all);
        return tag;
    }
}
