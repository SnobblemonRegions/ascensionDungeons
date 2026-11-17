package com.ascensiondungeons.outbreak;

import static org.junit.jupiter.api.Assertions.*;

import com.ascensiondungeons.outbreak.Readiness.Card;
import com.ascensiondungeons.outbreak.Readiness.Concern;
import com.ascensiondungeons.outbreak.Readiness.Team;
import com.ascensiondungeons.outbreak.Readiness.Verdict;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReadinessTest {
    private static final Card NORMAL = new Card("normal", "The Hearthwarden Village", 1, 12, 30, 50, 45);
    private static final Card WATER = new Card("water", "The Drowned Reservoir", 3, 12, 30, 55, 45);
    private static final Card DRAGON = new Card("dragon", "The Elder Wyrm Roost", 4, 12, 30, 62, 45);

    private static Team team(int... levels) { return new Team(true, levels, 0, 30); }

    @Test
    void aReadyPlayerHasNoConcerns() {
        Verdict v = Readiness.assess(NORMAL, team(25, 18, 14), true, NORMAL);
        assertTrue(v.ready());
        assertTrue(v.lines().isEmpty());
        assertTrue(Readiness.assess(DRAGON, team(60), false, NORMAL).ready(), "a graduate may go where they like");
    }

    @Test
    void noTeamAFaintedTeamAndAWeakTeamAreEachNamed() {
        Verdict none = Readiness.assess(NORMAL, new Team(true, new int[0], 0, 30), false, NORMAL);
        assertEquals(List.of(Concern.NO_POKEMON), none.concerns());
        assertTrue(none.lines().get(0).contains("no Pokémon"));
        Verdict fainted = Readiness.assess(NORMAL, new Team(true, new int[0], 3, 30), false, NORMAL);
        assertEquals(List.of(Concern.ALL_FAINTED), fainted.concerns());
        Verdict weak = Readiness.assess(NORMAL, team(5, 9), false, NORMAL);
        assertEquals(List.of(Concern.UNDER_LEVELLED), weak.concerns());
        assertEquals("Your strongest Pokémon is level 9. Enemies here start at level 12 and reach 30.", weak.lines().get(0));
        assertTrue(Readiness.assess(NORMAL, team(12), false, NORMAL).ready(), "level with the weakest enemies is enough to try");
        // one strong Pokémon among fainted ones is still a team
        assertTrue(Readiness.assess(NORMAL, new Team(true, new int[] {40}, 5, 30), false, NORMAL).ready());
    }

    @Test
    void aFullInventoryIsNamedWhateverElseIsTrue() {
        Verdict v = Readiness.assess(NORMAL, new Team(true, new int[] {30}, 0, 2), false, NORMAL);
        assertEquals(List.of(Concern.INVENTORY_FULL), v.concerns());
        assertTrue(v.lines().get(0).startsWith("You have 2 free inventory slots."));
        assertTrue(Readiness.assess(NORMAL, new Team(true, new int[] {30}, 0, 1), false, NORMAL).lines().get(0).startsWith("You have 1 free inventory slot."));
        assertTrue(Readiness.assess(NORMAL, new Team(true, new int[] {30}, 0, Readiness.ROOM_WANTED), false, NORMAL).ready());
        // without a Pokémon mod nothing is said about a team, but pockets still count
        assertTrue(Readiness.assess(NORMAL, new Team(false, new int[0], 0, 30), false, NORMAL).ready());
        assertEquals(List.of(Concern.INVENTORY_FULL), Readiness.assess(NORMAL, new Team(false, new int[0], 0, 0), false, NORMAL).concerns());
    }

    @Test
    void aFirstDiveIsSteeredToTheGentlestDungeonButNotLockedOut() {
        Verdict v = Readiness.assess(WATER, team(40), true, NORMAL);
        assertEquals(List.of(Concern.HIGH_TIER_FIRST_DIVE), v.concerns());
        assertEquals("This is a tier 3 dungeon. For a first dive, The Hearthwarden Village (tier 1) is the one to start with.", v.lines().get(0));
        assertTrue(Readiness.assess(NORMAL, team(40), true, NORMAL).ready());
        assertTrue(Readiness.assess(WATER, team(40), false, NORMAL).ready(), "only a first dive is steered");
        assertTrue(Readiness.assess(WATER, team(40), true, null).ready(), "nothing to recommend, nothing said");
        // several concerns come in a fixed order, each with its line
        Verdict many = Readiness.assess(DRAGON, new Team(true, new int[] {3}, 0, 0), true, NORMAL);
        assertEquals(List.of(Concern.UNDER_LEVELLED, Concern.INVENTORY_FULL, Concern.HIGH_TIER_FIRST_DIVE), many.concerns());
        assertEquals(3, many.lines().size());
    }

    @Test
    void dungeonsAreOfferedGentlestFirst() {
        Card bug = new Card("bug", "The Silkspire Hollow", 2, 12, 30, 52, 45);
        Card fairy = new Card("fairy", "The Moonpetal Court", 2, 12, 30, 52, 45);
        List<Card> cards = new ArrayList<>(List.of(DRAGON, fairy, WATER, NORMAL, bug));
        cards.sort(Readiness::order);
        assertEquals(List.of(NORMAL, fairy, bug, WATER, DRAGON), cards, "by tier, then gatekeeper, then name");
        assertEquals(NORMAL, Readiness.gentlest(List.of(DRAGON, WATER, NORMAL)));
        assertNull(Readiness.gentlest(List.of()));
        assertEquals("Water", WATER.typeName());
    }

    @Test
    void aFirstDiveOnHardOrNightmareIsWorthAWordButNormalIsNot() {
        Verdict hard = Readiness.assess(NORMAL, team(25), true, NORMAL, Tactics.Difficulty.HARD);
        assertEquals(List.of(Concern.HARD_FIRST_DIVE), hard.concerns());
        assertTrue(hard.lines().get(0).contains("on Hard"));
        assertTrue(Readiness.assess(NORMAL, team(25), true, NORMAL, Tactics.Difficulty.NORMAL).ready());
        assertTrue(Readiness.assess(NORMAL, team(25), true, NORMAL, Tactics.Difficulty.CASUAL).ready());
        assertTrue(Readiness.assess(NORMAL, team(25), false, NORMAL, Tactics.Difficulty.NIGHTMARE).ready(), "a graduate picks freely");
        assertEquals(List.of(Concern.HIGH_TIER_FIRST_DIVE, Concern.HARD_FIRST_DIVE), Readiness.assess(WATER, team(40), true, NORMAL, Tactics.Difficulty.NIGHTMARE).concerns());
    }
}
