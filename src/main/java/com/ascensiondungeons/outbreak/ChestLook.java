package com.ascensiondungeons.outbreak;

/**
 * Which resource-pack model a dungeon chest shows. The numbers are custom-model-data values on the display item
 * ({@code minecraft:popped_chorus_fruit}); {@code tools/outbreak/make_chest_pack.py} builds the pack that maps them, and
 * {@code ChestLookTest} fails if the two drift apart.
 */
public final class ChestLook {
    public static final String DISPLAY_ITEM = "minecraft:popped_chorus_fruit";
    public static final int TIMER_SEALED = 9105, TIMER_OPEN = 9106, STRONGBOX = 9107, OUTBREAK_BASE = 9111;

    private ChestLook() {}

    /**
     * @param kind   the feature
     * @param rarity the rarity it currently shows (an outbreak chest's grows over the run)
     * @param sealed a timer chest that has not unsealed yet
     * @return the model id, or 0 for a feature with no chest model (altars, the portal)
     */
    public static int model(FeaturePlanner.Kind kind, ChestRarity rarity, boolean sealed) {
        return switch (kind) {
            case CHEST -> rarity.modelId();
            case OUTBREAK_CHEST -> OUTBREAK_BASE + rarity.ordinal();
            case TIMER_CHEST -> sealed ? TIMER_SEALED : TIMER_OPEN;
            case STRONGBOX -> STRONGBOX;
            case ALTAR, PORTAL, DESCENT, RETURN -> 0;
        };
    }
}
