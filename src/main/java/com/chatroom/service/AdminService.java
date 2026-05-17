package com.chatroom.service;

import com.chatroom.config.OnlineTracker;
import com.chatroom.model.*;
import com.chatroom.repository.*;
import com.chatroom.util.AvatarUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Equivalent to the /api/admin/* routes in server.js
 */
@Service
public class AdminService {

    @Autowired private UserRepository      userRepo;
    @Autowired private RoomRepository      roomRepo;
    @Autowired private MessageRepository   msgRepo;
    @Autowired private ActivityRepository  activityRepo;
    @Autowired private OnlineTracker       onlineTracker;
    @Autowired private AvatarUtil          avatarUtil;
    @Autowired private SimpMessagingTemplate ws;

    // ── GET /api/admin/stats ──────────────────────────────────────────────────
    public ResponseEntity<?> getStats() {
        long totalUsers    = userRepo.count();
        long totalRooms    = roomRepo.count();
        long totalMessages = msgRepo.count();           // approximation
        long bannedUsers   = userRepo.countByBannedTrue();
        int  onlineNow     = onlineTracker.getTotalOnline();

        return ResponseEntity.ok(Map.of(
            "totalUsers",    totalUsers,
            "totalRooms",    totalRooms,
            "totalMessages", totalMessages,
            "bannedUsers",   bannedUsers,
            "onlineNow",     onlineNow
        ));
    }

    // ── GET /api/admin/users ──────────────────────────────────────────────────
    public ResponseEntity<?> getUsers(String search) {
        List<User> users;
        if (search == null || search.isBlank()) {
            users = userRepo.findAll();
        } else {
            Pattern p = Pattern.compile(Pattern.quote(search), Pattern.CASE_INSENSITIVE);
            users = userRepo.findByNameRegexOrMobileRegex(p, p);
        }
        users.sort(Comparator.comparing(User::getCreatedAt,
            Comparator.nullsLast(Comparator.reverseOrder())));

        List<Map<String, Object>> result = users.stream().limit(100).map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("_id",         u.getId());
            m.put("name",        u.getName());
            m.put("mobile",      u.getMobile());
            m.put("age",         u.getAge());
            m.put("gender",      u.getGender());
            m.put("banned",      u.isBanned());
            m.put("banReason",   u.getBanReason());
            m.put("initials",    avatarUtil.getInitials(u.getName()));
            m.put("avatarColor", avatarUtil.getAvatarColor(u.getId()));
            m.put("createdAt",   u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("users", result));
    }

    // ── POST /api/admin/users/:userId/ban ─────────────────────────────────────
    public ResponseEntity<?> banUser(String userId, String reason) {
        return userRepo.findById(userId).map(user -> {
            user.setBanned(true);
            user.setBanReason(reason != null ? reason : "Admin action");
            userRepo.save(user);
            logActivity("BAN_USER", user.getId(), user.getName(), "Reason: " + user.getBanReason());
            return ResponseEntity.ok(Map.of("success", true));
        }).orElse(ResponseEntity.status(404).body(Map.of("error", "User not found")));
    }

    // ── POST /api/admin/users/:userId/unban ───────────────────────────────────
    public ResponseEntity<?> unbanUser(String userId) {
        return userRepo.findById(userId).map(user -> {
            user.setBanned(false);
            user.setBanReason("");
            userRepo.save(user);
            logActivity("UNBAN_USER", user.getId(), user.getName(), "User unbanned");
            return ResponseEntity.ok(Map.of("success", true));
        }).orElse(ResponseEntity.status(404).body(Map.of("error", "User not found")));
    }

    // ── DELETE /api/admin/users/:userId ───────────────────────────────────────
    public ResponseEntity<?> deleteUser(String userId) {
        return userRepo.findById(userId).map(user -> {
            userRepo.delete(user);
            logActivity("DELETE_USER", user.getId(), user.getName(), "User account deleted");
            return ResponseEntity.ok(Map.of("success", true));
        }).orElse(ResponseEntity.status(404).body(Map.of("error", "User not found")));
    }

    // ── GET /api/admin/rooms ──────────────────────────────────────────────────
    public ResponseEntity<?> getRooms(String search) {
        List<Room> rooms;
        if (search == null || search.isBlank()) {
            rooms = roomRepo.findAll();
        } else {
            Pattern p = Pattern.compile(Pattern.quote(search), Pattern.CASE_INSENSITIVE);
            rooms = roomRepo.findByNameRegexOrRoomCodeRegex(p, p);
        }
        rooms.sort(Comparator.comparing(Room::getCreatedAt,
            Comparator.nullsLast(Comparator.reverseOrder())));

        List<Map<String, Object>> result = rooms.stream().limit(100).map(r -> {
            long msgCount = msgRepo.countByRoomIdAndType(r.getRoomCode(), "user");
            int  online   = onlineTracker.getOnlineCount(r.getRoomCode());
            User owner    = userRepo.findById(r.getOwnerId()).orElse(null);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",            r.getId());
            m.put("roomCode",      r.getRoomCode());
            m.put("name",          r.getName());
            m.put("plainPassword", r.getPlainPassword());
            m.put("owner",         owner != null ? Map.of("name", owner.getName(), "mobile", owner.getMobile()) : null);
            m.put("memberCount",   r.getMemberIds().size());
            m.put("messageCount",  msgCount);
            m.put("onlineNow",     online);
            m.put("createdAt",     r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("rooms", result));
    }

    // ── DELETE /api/admin/rooms/:roomCode ─────────────────────────────────────
    public ResponseEntity<?> deleteRoom(String roomCode) {
        return roomRepo.findByRoomCode(roomCode).map(room -> {
            msgRepo.deleteAllByRoomId(roomCode);
            roomRepo.delete(room);
            ws.convertAndSend("/topic/room/" + roomCode + "/events",
                Map.of("type", "room_deleted", "roomCode", roomCode));
            logActivity("DELETE_ROOM", room.getId(), room.getName(), "Code: " + roomCode);
            return ResponseEntity.ok(Map.of("success", true));
        }).orElse(ResponseEntity.status(404).body(Map.of("error", "Room not found")));
    }

    // ── GET /api/admin/rooms/:roomCode/messages ───────────────────────────────
    public ResponseEntity<?> getRoomMessages(String roomCode) {
        var pageable = org.springframework.data.domain.PageRequest.of(0, 500);
        List<Message> msgs = msgRepo.findByRoomIdOrderByTimestampAsc(roomCode, pageable);

        // Strip mediaData from list (same as Node: msgLight.mediaData = undefined)
        List<Map<String, Object>> light = msgs.stream().map(m -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("_id",       m.getId());
            map.put("type",      m.getType());
            map.put("username",  m.getUsername());
            map.put("text",      m.getText());
            map.put("timestamp", m.getTimestamp() != null ? m.getTimestamp().toString() : null);
            map.put("hasMedia",  m.getMediaData() != null);
            map.put("mediaType", m.getMediaType());
            map.put("mediaMode", m.getMediaMode());
            map.put("viewCount", m.getViewCount());
            map.put("deleted",   m.isDeleted());
            // mediaData intentionally omitted — fetched separately via /media endpoint
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("messages", light));
    }

    // ── GET /api/admin/messages/:messageId/media ──────────────────────────────
    public ResponseEntity<?> getMessageMedia(String messageId) {
        return msgRepo.findById(messageId).map(msg -> {
            if (msg.getMediaData() == null)
                return ResponseEntity.status(404).body(Map.of("error", "No media"));
            return ResponseEntity.ok(Map.of(
                "mediaData", msg.getMediaData(),
                "mediaMime", msg.getMediaMime(),
                "mediaType", msg.getMediaType()
            ));
        }).orElse(ResponseEntity.status(404).body(Map.of("error", "Message not found")));
    }

    // ── GET /api/admin/activity ───────────────────────────────────────────────
    public ResponseEntity<?> getActivity() {
        return ResponseEntity.ok(Map.of("logs", activityRepo.findTop100ByOrderByTimestampDesc()));
    }

    // ── logActivity() equivalent ──────────────────────────────────────────────
    private void logActivity(String action, String targetId, String targetName, String detail) {
        try { activityRepo.save(new Activity(action, targetId, targetName, detail)); }
        catch (Exception e) { System.err.println("Activity log error: " + e.getMessage()); }
    }
}
