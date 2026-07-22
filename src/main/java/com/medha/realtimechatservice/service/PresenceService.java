package com.medha.realtimechatservice.service;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which usernames are currently present in which chat rooms.
 *
 * <p>This is deliberately an in-memory, non-persisted store: presence is a transient,
 * connection-scoped concept (who is online <em>right now</em>), unlike chat history which is
 * durable and belongs in MySQL. Because a single WebSocket session serves one user in one room
 * at a time in this demo, a session id is also tracked so {@code WebSocketEventListener} can
 * resolve "which user/room disconnected" purely from the session attributes it stashed at
 * JOIN time -- no shared mutable state needs to leak into the STOMP controller.</p>
 *
 * <p>Backed by {@link ConcurrentHashMap} and {@link ConcurrentHashMap#newKeySet()} so it is safe
 * under concurrent WebSocket sessions without explicit synchronization.</p>
 */
@Service
public class PresenceService {

    private final Map<String, Set<String>> roomToUsernames = new ConcurrentHashMap<>();

    /**
     * Registers {@code username} as present in {@code roomCode}.
     *
     * @return true if the user was not already tracked as present in the room
     */
    public boolean join(String roomCode, String username) {
        Set<String> users = roomToUsernames.computeIfAbsent(roomCode, k -> ConcurrentHashMap.newKeySet());
        return users.add(username);
    }

    /**
     * Removes {@code username} from {@code roomCode}'s presence set.
     *
     * @return true if the user had been tracked as present
     */
    public boolean leave(String roomCode, String username) {
        Set<String> users = roomToUsernames.get(roomCode);
        if (users == null) {
            return false;
        }
        boolean removed = users.remove(username);
        if (users.isEmpty()) {
            roomToUsernames.remove(roomCode, users);
        }
        return removed;
    }

    public Set<String> getOnlineUsers(String roomCode) {
        Set<String> users = roomToUsernames.get(roomCode);
        return users == null ? Collections.emptySet() : Set.copyOf(users);
    }

    public int getOnlineCount(String roomCode) {
        Set<String> users = roomToUsernames.get(roomCode);
        return users == null ? 0 : users.size();
    }
}
