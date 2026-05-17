package com.chatroom.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Equivalent to activitySchema in models.js
 * Used for admin activity log (ban, delete, etc.)
 */
@Data
@NoArgsConstructor
@Document(collection = "activities")
public class Activity {

    @Id
    private String id;

    private String action;      // e.g. "BAN_USER", "DELETE_ROOM"
    private String targetId;
    private String targetName;
    private String detail;
    private LocalDateTime timestamp = LocalDateTime.now();

    public Activity(String action, String targetId, String targetName, String detail) {
        this.action     = action;
        this.targetId   = targetId;
        this.targetName = targetName;
        this.detail     = detail;
    }
}
