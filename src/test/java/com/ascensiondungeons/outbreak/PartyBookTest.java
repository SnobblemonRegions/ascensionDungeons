package com.ascensiondungeons.outbreak;

import static org.junit.jupiter.api.Assertions.*;

import com.ascensiondungeons.outbreak.PartyBook.Result;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PartyBookTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3), D = new UUID(0, 4), E = new UUID(0, 5);

    @Test
    void aPlayerAloneIsAPartyOfOneAndLeadsIt() {
        PartyBook book = new PartyBook(4, 1200);
        assertEquals(Set.of(A), book.party(A));
        assertEquals(A, book.leader(A));
        assertTrue(book.leads(A));
        assertEquals(Result.NOT_IN_PARTY, book.leave(A));
    }

    @Test
    void nobodyJoinsWithoutAccepting() {
        PartyBook book = new PartyBook(4, 1200);
        assertEquals(Result.OK, book.invite(A, B, 0));
        assertEquals(Set.of(A), book.party(A), "an invitation alone changes nothing");
        assertEquals(A, book.invitedBy(B, 10));
        assertEquals(Result.OK, book.accept(B, 10));
        assertEquals(List.of(A, B), List.copyOf(book.party(A)), "leader first");
        assertEquals(book.party(A), book.party(B));
        assertEquals(A, book.leader(B));
        assertFalse(book.leads(B));
        assertEquals(Result.NO_INVITE, book.accept(B, 11), "an invitation is used once");
        assertEquals(Result.NO_INVITE, book.accept(C, 11));
    }

    @Test
    void anInvitationRunsOut() {
        PartyBook book = new PartyBook(4, 1200);
        book.invite(A, B, 0);
        assertNull(book.invitedBy(B, 1201));
        assertEquals(Result.NO_INVITE, book.accept(B, 1201));
        book.invite(A, B, 2000);
        assertEquals(Result.OK, book.accept(B, 3200), "on the last tick it still stands");
    }

    @Test
    void onlyALeaderInvitesAndNobodyIsInTwoParties() {
        PartyBook book = new PartyBook(4, 1200);
        assertEquals(Result.SELF, book.invite(A, A, 0));
        book.invite(A, B, 0);
        book.accept(B, 1);
        assertEquals(Result.NOT_LEADER, book.invite(B, C, 2), "a guest does not invite");
        assertEquals(Result.TAKEN, book.invite(C, B, 2), "B is already in A's party");
        assertEquals(Result.TAKEN, book.invite(C, A, 2), "A leads a party of their own");
        // an invitation accepted too late, after the guest joined someone else, does nothing
        book.invite(A, C, 3);
        book.invite(D, C, 4);
        assertEquals(D, book.invitedBy(C, 5), "the newest invitation is the one held");
        assertEquals(Result.OK, book.accept(C, 5));
        assertEquals(Set.of(D, C), book.party(C));
        assertEquals(Set.of(A, B), book.party(A));
    }

    @Test
    void aPartyHasAsManyPlayersAsARunAllows() {
        PartyBook book = new PartyBook(3, 1200);
        book.invite(A, B, 0);
        book.invite(A, C, 0);
        book.invite(A, D, 0);
        assertEquals(Result.OK, book.accept(B, 1));
        assertEquals(Result.OK, book.accept(C, 1));
        assertEquals(Result.FULL, book.accept(D, 1), "the third guest finds it full");
        assertEquals(Result.FULL, book.invite(A, E, 2));
        assertEquals(3, book.party(A).size());
        assertEquals(3, book.maxSize());
        assertThrows(IllegalArgumentException.class, () -> new PartyBook(0, 10));
    }

    @Test
    void aGuestLeavesAloneAndALeaderLeavingEndsTheParty() {
        PartyBook book = new PartyBook(4, 1200);
        book.invite(A, B, 0);
        book.invite(A, C, 0);
        book.accept(B, 1);
        book.accept(C, 1);
        assertEquals(Result.OK, book.leave(B));
        assertEquals(Set.of(A, C), book.party(A));
        assertEquals(Set.of(B), book.party(B));
        assertTrue(book.leads(B));
        assertEquals(Result.OK, book.leave(A));
        assertEquals(Set.of(C), book.party(C), "the leader left: everyone is on their own again");
        assertTrue(book.leads(C));
        assertEquals(Result.NOT_IN_PARTY, book.leave(C));
    }

    @Test
    void aPlayerWhoLogsOffIsForgotten() {
        PartyBook book = new PartyBook(4, 1200);
        book.invite(A, B, 0);
        book.accept(B, 1);
        book.invite(A, C, 2);
        book.invite(D, E, 2);
        book.forget(A);
        assertEquals(Set.of(B), book.party(B));
        assertEquals(Result.NO_INVITE, book.accept(C, 3), "an invitation from someone who has gone is void");
        book.forget(E);
        assertEquals(Result.NO_INVITE, book.accept(E, 3));
        // the inviter joined another party before the guest answered: the invitation is void
        book.invite(B, C, 10);
        book.invite(D, B, 11);
        assertEquals(Result.OK, book.accept(B, 12));
        assertEquals(Result.NO_INVITE, book.accept(C, 13));
    }
}
