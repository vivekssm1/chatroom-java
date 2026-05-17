package com.chatroom.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Equivalent to messageSchema in models.js
 *
 * Nested class Reaction replaces the inline subdocument array:
 *   reactions: [{ emoji: String, userId: String, username: String }]
 */
@Data
@NoArgsConstructor
@Document(collection = "messages")
public class Message {

    @Id
    private String id;

    @Indexed                        // same as index: true in Mongoose
    private String roomId;

    private String type = "user";   // "user" | "system"

    private String senderId;
    private String username;
    private String initials;
    private String avatarColor;

    private String text;
    private boolean deleted  = false;
    private boolean pinned   = false;

    // ── Media fields (same as models.js) ─────────────────────────────────────
    private String mediaType;       // "image" | "video" | null
    private String mediaData;       // base64 string
    private String mediaMime;       // e.g. "image/jpeg"
    private String mediaMode;       // "permanent" | "2view" | null
    private int    viewCount = 0;
    private List<String> viewedBy = new ArrayList<>();

    // ── Reactions ─────────────────────────────────────────────────────────────
    private List<Reaction> reactions = new ArrayList<>();

    private LocalDateTime timestamp = LocalDateTime.now();

    // ── Nested class (replaces Mongoose subdocument) ──────────────────────────
    @Data
    @NoArgsConstructor
    public static class Reaction {
        private String emoji;
        private String userId;
        private String username;

        public Reaction(String emoji, String userId, String username) {
            this.emoji    = emoji;
            this.userId   = userId;
            this.username = username;
        }
    }
}
