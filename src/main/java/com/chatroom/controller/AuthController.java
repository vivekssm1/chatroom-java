package com.chatroom.controller;

import com.chatroom.dto.Dtos.*;
import com.chatroom.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Equivalent to the auth routes block in server.js:
 *
 *   app.post("/api/register", ...)
 *   app.post("/api/login", ...)
 *   app.post("/api/logout", ...)
 *   app.get ("/api/me", authMiddleware, ...)
 *   app.put ("/api/profile", authMiddleware, ...)
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    @Autowired
    private AuthService authService;

    // POST /api/register
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req,
                                      HttpServletResponse res) {
        return authService.register(req, res);
    }

    // POST /api/login
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req,
                                   HttpServletResponse res) {
        return authService.login(req, res);
    }

    // POST /api/logout — clears the cookie
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse res) {
        authService.clearCookie(res);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // GET /api/me — requires valid JWT (handled by SecurityConfig filter)
    @GetMapping("/me")
    public ResponseEntity<?> getMe(Authentication auth, HttpServletResponse res) {
        // auth.getName() = userId set by the JWT filter
        return authService.getMe(auth.getName(), res);
    }

    // PUT /api/profile
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, Object> body,
                                           Authentication auth) {
        String name   = (String) body.get("name");
        int    age    = Integer.parseInt(body.get("age").toString());
        String gender = (String) body.get("gender");
        return authService.updateProfile(auth.getName(), name, age, gender);
    }
}
