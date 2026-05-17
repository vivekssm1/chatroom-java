package com.chatroom.util;

import org.springframework.stereotype.Component;

/**
 * Equivalent to these helpers in server.js:
 *
 *   const AVATAR_COLORS = ["#6366f1","#ec4899", ...]
 *   function getInitials(name) { return name.split(" ").map(w=>w[0]).join("").toUpperCase().slice(0,2) }
 *   function getAvatarColor(userId) { return AVATAR_COLORS[parseInt(userId.slice(-2), 16) % AVATAR_COLORS.length] }
 */
@Component
public class AvatarUtil {

    private static final String[] AVATAR_COLORS = {
        "#6366f1", "#ec4899", "#10b981", "#f59e0b",
        "#3b82f6", "#8b5cf6", "#ef4444", "#14b8a6",
        "#f97316", "#06b6d4"
    };

    public String getInitials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) sb.append(part.charAt(0));
        }
        return sb.toString().toUpperCase().substring(0, Math.min(2, sb.length()));
    }

    public String getAvatarColor(String userId) {
        if (userId == null || userId.length() < 2) return AVATAR_COLORS[0];
        try {
            // Same logic as Node: parseInt(userId.slice(-2), 16) % COLORS.length
            String lastTwo = userId.substring(userId.length() - 2);
            int index = Integer.parseInt(lastTwo, 16) % AVATAR_COLORS.length;
            return AVATAR_COLORS[index];
        } catch (NumberFormatException e) {
            return AVATAR_COLORS[0];
        }
    }
}
