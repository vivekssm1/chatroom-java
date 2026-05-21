package com.chatroom.service;

import com.chatroom.config.OnlineTracker;
import com.chatroom.model.*;
import com.chatroom.repository.*;
import com.chatroom.util.AvatarUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AdminService {

    @Autowired private UserRepository      userRepo;
    @Autowired private RoomRepository      roomRepo;
    @Autowired private MessageRepository   msgRepo;
    @Autowired private ActivityRepository  activityRepo;
    @Autowired private OnlineTracker       onlineTracker;
    @Autowired private AvatarUtil          avatarUtil;
    @Autowired private SimpMessagingTemplate ws;

    private Map<String, Object> ok()        { Map<String,Object> m=new LinkedHashMap<>(); m.put("success",true); return m; }
    private Map<String, Object> err(String msg) { Map<String,Object> m=new LinkedHashMap<>(); m.put("error",msg); return m; }

    public ResponseEntity<?> getStats() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("totalUsers",    userRepo.count());
        resp.put("totalRooms",    roomRepo.count());
        resp.put("totalMessages", msgRepo.count());
        resp.put("bannedUsers",   userRepo.countByBannedTrue());
        resp.put("onlineNow",     onlineTracker.getTotalOnline());
        return ResponseEntity.ok(resp);
    }

    public ResponseEntity<?> getUsers(String search) {
        List<User> users;
        if (search == null || search.isBlank()) {
            users = userRepo.findAll();
        } else {
            Pattern p = Pattern.compile(Pattern.quote(search), Pattern.CASE_INSENSITIVE);
            users = userRepo.findByNameRegexOrMobileRegex(p, p);
        }
        users.sort(Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
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
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("users", result);
        return ResponseEntity.ok(resp);
    }

    public ResponseEntity<?> banUser(String userId, String reason) {
        Optional<User> opt = userRepo.findById(userId);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(err("User not found"));
        User user = opt.get();
        user.setBanned(true);
        user.setBanReason(reason != null ? reason : "Admin action");
        userRepo.save(user);
        logActivity("BAN_USER", user.getId(), user.getName(), "Reason: " + user.getBanReason());
        return ResponseEntity.ok(ok());
    }

    public ResponseEntity<?> unbanUser(String userId) {
        Optional<User> opt = userRepo.findById(userId);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(err("User not found"));
        User user = opt.get();
        user.setBanned(false);
        user.setBanReason("");
        userRepo.save(user);
        logActivity("UNBAN_USER", user.getId(), user.getName(), "User unbanned");
        return ResponseEntity.ok(ok());
    }

    public ResponseEntity<?> deleteUser(String userId) {
        Optional<User> opt = userRepo.findById(userId);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(err("User not found"));
        User user = opt.get();
        userRepo.delete(user);
        logActivity("DELETE_USER", user.getId(), user.getName(), "User account deleted");
        return ResponseEntity.ok(ok());
    }

    public ResponseEntity<?> getRooms(String search) {
        List<Room> rooms;
        if (search == null || search.isBlank()) {
            rooms = roomRepo.findAll();
        } else {
            Pattern p = Pattern.compile(Pattern.quote(search), Pattern.CASE_INSENSITIVE);
            rooms = roomRepo.findByNameRegexOrRoomCodeRegex(p, p);
        }
        rooms.sort(Comparator.comparing(Room::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        List<Map<String, Object>> result = rooms.stream().limit(100).map(r -> {
            long msgCount = msgRepo.countByRoomIdAndType(r.getRoomCode(), "user");
            int  online   = onlineTracker.getOnlineCount(r.getRoomCode());
            User owner    = userRepo.findById(r.getOwnerId()).orElse(null);
            Map<String, Object> ownerMap = null;
            if (owner != null) {
                ownerMap = new LinkedHashMap<>();
                ownerMap.put("name",   owner.getName());
                ownerMap.put("mobile", owner.getMobile());
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",            r.getId());
            m.put("roomCode",      r.getRoomCode());
            m.put("name",          r.getName());
            m.put("plainPassword", r.getPlainPassword());
            m.put("owner",         ownerMap);
            m.put("memberCount",   r.getMemberIds().size());
            m.put("messageCount",  msgCount);
            m.put("onlineNow",     online);
            m.put("createdAt",     r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
            return m;
        }).collect(Collectors.toList());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("rooms", result);
        return ResponseEntity.ok(resp);
    }

    public ResponseEntity<?> deleteRoom(String roomCode) {
        Optional<Room> opt = roomRepo.findByRoomCode(roomCode);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(err("Room not found"));
        Room room = opt.get();
        msgRepo.deleteAllByRoomId(roomCode);
        roomRepo.delete(room);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "room_deleted");
        event.put("roomCode", roomCode);
        ws.convertAndSend("/topic/room/" + roomCode + "/events", event);
        logActivity("DELETE_ROOM", room.getId(), room.getName(), "Code: " + roomCode);
        return ResponseEntity.ok(ok());
    }

    public ResponseEntity<?> getRoomMessages(String roomCode) {
        List<Message> msgs = msgRepo.findByRoomIdOrderByTimestampAsc(roomCode, PageRequest.of(0, 500));
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
            return map;
        }).collect(Collectors.toList());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("messages", light);
        return ResponseEntity.ok(resp);
    }

    public ResponseEntity<?> getMessageMedia(String messageId) {
        Optional<Message> opt = msgRepo.findById(messageId);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(err("Message not found"));
        Message msg = opt.get();
        if (msg.getMediaData() == null) return ResponseEntity.status(404).body(err("No media"));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("mediaData", msg.getMediaData());
        resp.put("mediaMime", msg.getMediaMime());
        resp.put("mediaType", msg.getMediaType());
        return ResponseEntity.ok(resp);
    }

    public ResponseEntity<?> getActivity() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("logs", activityRepo.findTop100ByOrderByTimestampDesc());
        return ResponseEntity.ok(resp);
    }

    private void logActivity(String action, String targetId, String targetName, String detail) {
        try { activityRepo.save(new Activity(action, targetId, targetName, detail)); }
        catch (Exception e) { System.err.println("Activity log error: " + e.getMessage()); }
    }
}
