package com.medha.realtimechatservice.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PresenceServiceTest {

    private final PresenceService presenceService = new PresenceService();

    @Test
    void joinAddsUserToRoomAndReturnsTrueOnFirstJoin() {
        boolean result = presenceService.join("ROOM01", "alice");

        assertThat(result).isTrue();
        assertThat(presenceService.getOnlineUsers("ROOM01")).containsExactly("alice");
        assertThat(presenceService.getOnlineCount("ROOM01")).isEqualTo(1);
    }

    @Test
    void joinIsIdempotentForTheSameUser() {
        presenceService.join("ROOM01", "alice");
        boolean secondJoin = presenceService.join("ROOM01", "alice");

        assertThat(secondJoin).isFalse();
        assertThat(presenceService.getOnlineCount("ROOM01")).isEqualTo(1);
    }

    @Test
    void multipleUsersCanShareARoom() {
        presenceService.join("ROOM01", "alice");
        presenceService.join("ROOM01", "bob");

        assertThat(presenceService.getOnlineUsers("ROOM01")).containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void leaveRemovesUserAndReturnsTrueWhenPresent() {
        presenceService.join("ROOM01", "alice");

        boolean removed = presenceService.leave("ROOM01", "alice");

        assertThat(removed).isTrue();
        assertThat(presenceService.getOnlineUsers("ROOM01")).isEmpty();
        assertThat(presenceService.getOnlineCount("ROOM01")).isZero();
    }

    @Test
    void leaveReturnsFalseWhenUserWasNeverPresent() {
        boolean removed = presenceService.leave("UNKNOWN", "ghost");

        assertThat(removed).isFalse();
    }

    @Test
    void getOnlineUsersForUnknownRoomReturnsEmptySet() {
        assertThat(presenceService.getOnlineUsers("NOPE")).isEqualTo(Set.of());
    }

    @Test
    void roomsAreIndependent() {
        presenceService.join("ROOM01", "alice");
        presenceService.join("ROOM02", "bob");

        assertThat(presenceService.getOnlineUsers("ROOM01")).containsExactly("alice");
        assertThat(presenceService.getOnlineUsers("ROOM02")).containsExactly("bob");
    }
}
