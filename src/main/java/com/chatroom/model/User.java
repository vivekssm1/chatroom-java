package com.chatroom.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

/**
 * Equivalent to userSchema in models.js
 *
 * Mongoose:                         Java / Spring Data MongoDB:
 * ─────────────────────────────     ────────────────────────────────────
 * new mongoose.Schema({...})    →   @Document(collection = "users")
 * required: true                →   @NotBlank / @NotNull
 * unique: true                  →   @Indexed(unique = true)
 * maxlength: 40                 →   @Size(max = 40)
 * default: false                →   = false  (field initializer)
 * pre("save", hashPassword)     →   done in AuthService before save
 */
@Data                      // Lombok: generates getters, setters, toString, equals
@NoArgsConstructor         // Lombok: generates no-arg constructor
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @NotBlank
    @Size(max = 40)
    private String name;

    @NotBlank
    @Indexed(unique = true)   // same as unique:true in Mongoose
    private String mobile;

    @NotBlank
    private String password;  // always stored as BCrypt hash

    @Min(10) @Max(120)
    private int age;

    @NotBlank
    private String gender;    // "male" | "female" | "other"

    private boolean banned    = false;
    private String  banReason = "";

    @CreatedDate
    private LocalDateTime createdAt;
}
