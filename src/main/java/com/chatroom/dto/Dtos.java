package com.chatroom.dto;

import lombok.Data;
import jakarta.validation.constraints.*;

/**
 * DTOs (Data Transfer Objects) — these separate what the API accepts/returns
 * from the internal DB model. Node.js doesn't need these explicitly because
 * it's loosely typed, but in Java they make code safer and cleaner.
 */
public class Dtos {

    // ── Auth DTOs ─────────────────────────────────────────────────────────────

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "Name is required")
        @Size(max = 40)
        private String name;

        @NotBlank(message = "Mobile is required")
        @Pattern(regexp = "\\d{10}", message = "Mobile must be 10 digits")
        private String mobile;

        @NotBlank
        @Size(min = 6, message = "Password must be at least 6 characters")
        private String password;

        @Min(10) @Max(120)
        private int age;

        @NotBlank
        @Pattern(regexp = "male|female|other", message = "Gender must be male, female or other")
        private String gender;
    }

    @Data
    public static class LoginRequest {
        @NotBlank private String mobile;
        @NotBlank private String password;
    }

    @Data
    public static class AdminLoginRequest {
        @NotBlank private String username;
        @NotBlank private String password;
    }

    // ── User response (equivalent to safeUser() in server.js) ─────────────────
    @Data
    public static class UserResponse {
        private String id;
        private String name;
        private String mobile;
        private int    age;
        private String gender;
        private boolean banned;
        private String banReason;
        private String initials;
        private String avatarColor;
    }

    // ── Room DTOs ─────────────────────────────────────────────────────────────

    @Data
    public static class CreateRoomRequest {
        @NotBlank private String name;
        @NotBlank @Size(min = 4, message = "Password must be at least 4 characters") private String password;
    }

    @Data
    public static class JoinRoomRequest {
        @NotBlank private String roomCode;
        @NotBlank private String password;
    }

    @Data
    public static class RoomResponse {
        private String  id;
        private String  roomCode;
        private String  name;
        private boolean isOwner;
        private int     memberCount;
        private String  pinnedMessageId;
        private String  createdAt;
        private String  plainPassword;   // only sent to owner
    }

    // ── Message DTOs ──────────────────────────────────────────────────────────

    @Data
    public static class SendMessageRequest {
        @NotBlank private String text;
    }

    @Data
    public static class PinRequest {
        private String messageId;  // null = unpin
    }

    @Data
    public static class BanRequest {
        private String reason;
    }

    // ── WebSocket payloads (received from frontend via STOMP) ─────────────────

    @Data
    public static class WsJoinRoom {
        private String roomCode;
    }

    @Data
    public static class WsSendMessage {
        private String roomCode;
        private String text;
    }

    @Data
    public static class WsSendMedia {
        private String roomCode;
        private String mediaData;   // base64
        private String mediaMime;
        private String mediaType;   // "image" | "video"
        private String mediaMode;   // "permanent" | "2view"
        private String caption;
    }

    @Data
    public static class WsReaction {
        private String messageId;
        private String roomCode;
        private String emoji;
    }

    @Data
    public static class WsTyping {
        private String roomCode;
    }

    @Data
    public static class WsDeleteMessage {
        private String messageId;
        private String roomCode;
    }

    // ── WebSocket broadcasts (sent to frontend) ───────────────────────────────

    @Data
    public static class RoomUpdateEvent {
        private int userCount;
        private java.util.List<UserResponse> users;
    }

    @Data
    public static class TypingEvent {
        private String username;
        private boolean typing;  // true = start, false = stop
    }

    @Data
    public static class DeletedEvent {
        private String messageId;
    }

    @Data
    public static class ReactionUpdatedEvent {
        private String messageId;
        private java.util.List<Object> reactions;
    }
}
