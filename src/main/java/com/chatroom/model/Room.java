package com.chatroom.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Equivalent to roomSchema in models.js
 *
 * NOTE: plainPassword is kept here intentionally to match original project.
 *       For production, remove it and add a password-reset flow instead.
 *
 * Mongoose ref: { type: ObjectId, ref: "User" }
 * Java equivalent: just store the String ID — we load separately in service.
 */
@Data
@NoArgsConstructor
@Document(collection = "rooms")
public class Room {

    @Id
    private String id;

    @NotBlank
    @Indexed(unique = true)
    private String roomCode;       // 6-digit auto-generated code

    @NotBlank
    @Size(max = 40)
    private String name;

    @NotBlank
    private String passwordHash;   // BCrypt hash

    // ⚠ Improvement note: remove this in production; store only hash
    private String plainPassword;

    @NotBlank
    private String ownerId;        // replaces: owner: ObjectId ref "User"

    private List<String> memberIds = new ArrayList<>();   // replaces: members: [ObjectId]

    private String pinnedMessageId = null;  // replaces: pinnedMessage: ObjectId

    @CreatedDate
    private LocalDateTime createdAt;
}
