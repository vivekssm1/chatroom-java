package com.chatroom.controller;

import com.chatroom.service.AdminService;
import com.chatroom.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Equivalent to admin routes in server.js:
 *
 *   app.post("/api/admin/login", ...)
 *   app.post("/api/admin/logout", ...)
 *   app.get ("/api/admin/verify", adminMiddleware, ...)
 *   app.get ("/api/admin/stats", adminMiddleware, ...)
 *   app.get ("/api/admin/users", adminMiddleware, ...)
 *   app.post("/api/admin/users/:id/ban", adminMiddleware, ...)
 *   app.post("/api/admin/users/:id/unban", adminMiddleware, ...)
 *   app.delete("/api/admin/users/:id", adminMiddleware, ...)
 *   app.get ("/api/admin/rooms", adminMiddleware, ...)
 *   app.delete("/api/admin/rooms/:roomCode", adminMiddleware, ...)
 *   app.get ("/api/admin/rooms/:roomCode/messages", adminMiddleware, ...)
 *   app.get ("/api/admin/messages/:id/media", adminMiddleware, ...)
 *   app.get ("/api/admin/activity", adminMiddleware, ...)
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired private AdminService adminService;
    @Autowired private JwtUtil jwtUtil;

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password}")
    private String adminPassword;

    // ── Auth ──────────────────────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body,
                                   HttpServletResponse res) {
        String username = body.get("username");
        String password = body.get("password");

        if (!adminUsername.equals(username) || !adminPassword.equals(password))
            return ResponseEntity.status(401).body(Map.of("error", "Invalid admin credentials"));

        String token = jwtUtil.generateAdminToken();
        Cookie cookie = new Cookie("adminToken", token);
        cookie.setHttpOnly(true);
        cookie.setMaxAge(8 * 60 * 60);   // 8 hours
        cookie.setPath("/");
        res.addCookie(cookie);

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse res) {
        Cookie cookie = new Cookie("adminToken", "");
        cookie.setMaxAge(0);
        cookie.setPath("/");
        res.addCookie(cookie);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verify(HttpServletRequest req) {
        if (!isAdminRequest(req))
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    public ResponseEntity<?> getStats(HttpServletRequest req) {
        if (!isAdminRequest(req)) return forbidden();
        return adminService.getStats();
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<?> getUsers(@RequestParam(required = false) String search,
                                      HttpServletRequest req) {
        if (!isAdminRequest(req)) return forbidden();
        return adminService.getUsers(search);
    }

    @PostMapping("/users/{userId}/ban")
    public ResponseEntity<?> banUser(@PathVariable String userId,
                                     @RequestBody(required = false) Map<String, String> body,
                                     HttpServletRequest req) {
        if (!isAdminRequest(req)) return forbidden();
        String reason = body != null ? body.get("reason") : null;
        return adminService.banUser(userId, reason);
    }

    @PostMapping("/users/{userId}/unban")
    public ResponseEntity<?> unbanUser(@PathVariable String userId,
                                       HttpServletRequest req) {
        if (!isAdminRequest(req)) return forbidden();
        return adminService.unbanUser(userId);
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable String userId,
                                        HttpServletRequest req) {
        if (!isAdminRequest(req)) return forbidden();
        return adminService.deleteUser(userId);
    }

    // ── Rooms ─────────────────────────────────────────────────────────────────

    @GetMapping("/rooms")
    public ResponseEntity<?> getRooms(@RequestParam(required = false) String search,
                                      HttpServletRequest req) {
        if (!isAdminRequest(req)) return forbidden();
        return adminService.getRooms(search);
    }

    @DeleteMapping("/rooms/{roomCode}")
    public ResponseEntity<?> deleteRoom(@PathVariable String roomCode,
                                        HttpServletRequest req) {
        if (!isAdminRequest(req)) return forbidden();
        return adminService.deleteRoom(roomCode);
    }

    @GetMapping("/rooms/{roomCode}/messages")
    public ResponseEntity<?> getRoomMessages(@PathVariable String roomCode,
                                             HttpServletRequest req) {
        if (!isAdminRequest(req)) return forbidden();
        return adminService.getRoomMessages(roomCode);
    }

    @GetMapping("/messages/{messageId}/media")
    public ResponseEntity<?> getMedia(@PathVariable String messageId,
                                      HttpServletRequest req) {
        if (!isAdminRequest(req)) return forbidden();
        return adminService.getMessageMedia(messageId);
    }

    // ── Activity Log ──────────────────────────────────────────────────────────

    @GetMapping("/activity")
    public ResponseEntity<?> getActivity(HttpServletRequest req) {
        if (!isAdminRequest(req)) return forbidden();
        return adminService.getActivity();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Equivalent to adminMiddleware in server.js:
     *   const token = req.cookies?.adminToken ...
     *   const payload = verifyToken(token)
     *   if (!payload || payload.role !== "admin") return 403
     */
    private boolean isAdminRequest(HttpServletRequest req) {
        if (req.getCookies() != null) {
            for (Cookie c : req.getCookies()) {
                if ("adminToken".equals(c.getName())) {
                    return jwtUtil.isAdmin(c.getValue());
                }
            }
        }
        return false;
    }

    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
    }
}
