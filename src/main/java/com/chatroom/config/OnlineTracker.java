package com.chatroom.config;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Equivalent to this in server.js:
 *
 *   const onlineRooms = {}   // roomCode -> Set<socketId>
 *
 * Using ConcurrentHashMap for thread safety (multiple WebSocket threads).
 * In Node.js single-threaded event loop, a plain object is safe.
 * In Java multi-threaded environment, we need concurrent collections.
 */
@Component
public class OnlineTracker {

    // roomCode → Set of sessionIds (equivalent to Set<socketId>)
    private final Map<String, Set<String>> onlineRooms = new ConcurrentHashMap<>();

    // sessionId → user info (so we can build user lists)
    private final Map<String, UserInfo> sessionUsers = new ConcurrentHashMap<>();

    public void addUser(String roomCode, String sessionId, UserInfo user) {
        onlineRooms.computeIfAbsent(roomCode, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        sessionUsers.put(sessionId, user);
    }

    public void removeUser(String sessionId) {
        sessionUsers.remove(sessionId);
        onlineRooms.forEach((room, sessions) -> sessions.remove(sessionId));
        // Clean up empty room entries
        onlineRooms.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    public int getOnlineCount(String roomCode) {
        Set<String> sessions = onlineRooms.get(roomCode);
        return sessions == null ? 0 : sessions.size();
    }

    public List<UserInfo> getOnlineUsers(String roomCode) {
        Set<String> sessions = onlineRooms.getOrDefault(roomCode, Set.of());
        List<UserInfo> users = new ArrayList<>();
        for (String sid : sessions) {
            UserInfo u = sessionUsers.get(sid);
            if (u != null) users.add(u);
        }
        return users;
    }

    public int getTotalOnline() {
        return (int) sessionUsers.values().stream()
            .map(u -> u.userId)
            .distinct()
            .count();
    }

    // Equivalent to socket.user object stored on the socket
    public record UserInfo(String sessionId, String userId, String name,
                           String initials, String avatarColor, String roomCode) {}
}
