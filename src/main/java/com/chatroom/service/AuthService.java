package com.chatroom.service;

import com.chatroom.dto.Dtos.*;
import com.chatroom.model.User;
import com.chatroom.repository.UserRepository;
import com.chatroom.util.AvatarUtil;
import com.chatroom.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Equivalent to the auth routes in server.js:
 *   POST /api/register
 *   POST /api/login
 *   GET  /api/me
 *   PUT  /api/profile
 */
@Service
public class AuthService {

    @Autowired private UserRepository userRepo;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private AvatarUtil avatarUtil;

    // ── POST /api/register ────────────────────────────────────────────────────
    public ResponseEntity<?> register(RegisterRequest req, HttpServletResponse res) {
        if (userRepo.existsByMobile(req.getMobile())) {
            return ResponseEntity.status(409).body(Map.of("error", "Mobile number already registered"));
        }

        User user = new User();
        user.setName(req.getName());
        user.setMobile(req.getMobile());
        user.setPassword(encoder.encode(req.getPassword()));  // bcrypt.hash(password, 10)
        user.setAge(req.getAge());
        user.setGender(req.getGender());

        User saved = userRepo.save(user);
        setAuthCookie(res, jwtUtil.generateUserToken(saved.getId()), 30 * 24 * 60 * 60);
        return ResponseEntity.ok(Map.of("success", true, "user", toResponse(saved)));
    }

    // ── POST /api/login ───────────────────────────────────────────────────────
    public ResponseEntity<?> login(LoginRequest req, HttpServletResponse res) {
        Optional<User> opt = userRepo.findByMobile(req.getMobile());
        if (opt.isEmpty() || !encoder.matches(req.getPassword(), opt.get().getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid mobile or password"));
        }
        User user = opt.get();
        if (user.isBanned()) {
            return ResponseEntity.status(403).body(Map.of("error",
                "Account banned. Reason: " + (user.getBanReason().isBlank() ? "Violation of terms" : user.getBanReason())));
        }
        setAuthCookie(res, jwtUtil.generateUserToken(user.getId()), 30 * 24 * 60 * 60);
        return ResponseEntity.ok(Map.of("success", true, "user", toResponse(user)));
    }

    // ── GET /api/me ───────────────────────────────────────────────────────────
    public ResponseEntity<?> getMe(String userId, HttpServletResponse res) {
        return userRepo.findById(userId)
            .map(user -> {
                if (user.isBanned()) {
                    clearCookie(res);
                    return ResponseEntity.status(403).body(Map.of("error", "Account banned: " + user.getBanReason()));
                }
                return ResponseEntity.ok(Map.of("user", toResponse(user)));
            })
            .orElse(ResponseEntity.status(404).body(Map.of("error", "Not found")));
    }

    // ── PUT /api/profile ──────────────────────────────────────────────────────
    public ResponseEntity<?> updateProfile(String userId, String name, int age, String gender) {
        return userRepo.findById(userId).map(user -> {
            user.setName(name);
            user.setAge(age);
            user.setGender(gender);
            User saved = userRepo.save(user);
            return ResponseEntity.ok(Map.of("success", true, "user", toResponse(saved)));
        }).orElse(ResponseEntity.status(404).body(Map.of("error", "User not found")));
    }

    // ── safeUser() equivalent ─────────────────────────────────────────────────
    public UserResponse toResponse(User user) {
        UserResponse r = new UserResponse();
        r.setId(user.getId());
        r.setName(user.getName());
        r.setMobile(user.getMobile());
        r.setAge(user.getAge());
        r.setGender(user.getGender());
        r.setBanned(user.isBanned());
        r.setBanReason(user.getBanReason());
        r.setInitials(avatarUtil.getInitials(user.getName()));
        r.setAvatarColor(avatarUtil.getAvatarColor(user.getId()));
        return r;
    }

    // ── Cookie helpers (equivalent to res.cookie() in Node) ──────────────────
    public void setAuthCookie(HttpServletResponse res, String token, int maxAgeSeconds) {
        Cookie cookie = new Cookie("token", token);
        cookie.setHttpOnly(true);    // same as httpOnly: true in Node
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setPath("/");
        cookie.setAttribute("SameSite", "Lax");
        res.addCookie(cookie);
    }

    public void clearCookie(HttpServletResponse res) {
        Cookie cookie = new Cookie("token", "");
        cookie.setMaxAge(0);
        cookie.setPath("/");
        res.addCookie(cookie);
    }
}
