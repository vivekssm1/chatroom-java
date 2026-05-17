package com.chatroom.service;

import com.chatroom.dto.Dtos.*;
import com.chatroom.model.Message;
import com.chatroom.model.Room;
import com.chatroom.model.User;
import com.chatroom.repository.MessageRepository;
import com.chatroom.repository.RoomRepository;
import com.chatroom.repository.UserRepository;
import com.chatroom.util.AvatarUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Equivalent to room routes in server.js:
 *   POST   /api/rooms/create
 *   POST   /api/rooms/join
 *   GET    /api/rooms
 *   GET    /api/rooms/:roomCode
 *   DELETE /api/rooms/:roomCode
 *   POST   /api/rooms/:roomCode/pin
 */
@Service
public class RoomService {

    @Autowired private RoomRepository roomRepo;
    @Autowired private MessageRepository msgRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private PasswordEncoder encoder;
    @Autowired private AvatarUtil avatarUtil;
    @Autowired private SimpMessagingTemplate ws;

    // ── POST /api/rooms/create ────────────────────────────────────────────────
    public ResponseEntity<?> createRoom(String name, String password, String userId) {
        if (password.length() < 4)
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 4 characters"));

        String roomCode = generateUniqueCode();

        Room room = new Room();
        room.setRoomCode(roomCode);
        room.setName(name);
        room.setPasswordHash(encoder.encode(password));
        room.setPlainPassword(password);   // ⚠ See improvement note in Room.java
        room.setOwnerId(userId);
        room.setMemberIds(new ArrayList<>(List.of(userId)));

        Room saved = roomRepo.save(room);
        return ResponseEntity.ok(Map.of("success", true, "room", toResponse(saved, userId)));
    }

    // ── POST /api/rooms/join ──────────────────────────────────────────────────
    public ResponseEntity<?> joinRoom(String roomCode, String password, String userId) {
        Optional<Room> opt = roomRepo.findByRoomCode(roomCode);
        if (opt.isEmpty())
            return ResponseEntity.status(404).body(Map.of("error", "Room not found. Check the room code."));

        Room room = opt.get();
        if (!encoder.matches(password, room.getPasswordHash()))
            return ResponseEntity.status(401).body(Map.of("error", "Wrong room password"));

        if (!room.getMemberIds().contains(userId)) {
            room.getMemberIds().add(userId);
            roomRepo.save(room);
        }

        return ResponseEntity.ok(Map.of("success", true, "room", toResponse(room, userId)));
    }

    // ── GET /api/rooms ────────────────────────────────────────────────────────
    public ResponseEntity<?> getUserRooms(String userId) {
        List<Room> rooms = roomRepo.findByMemberIdsContaining(userId);
        rooms.sort(Comparator.comparing(Room::getCreatedAt).reversed());
        return ResponseEntity.ok(Map.of("rooms",
            rooms.stream().map(r -> toResponse(r, userId)).collect(Collectors.toList())));
    }

    // ── GET /api/rooms/:roomCode ──────────────────────────────────────────────
    public ResponseEntity<?> getRoomDetail(String roomCode, String userId) {
        return roomRepo.findByRoomCode(roomCode).map(room -> {
            long msgCount = msgRepo.countByRoomIdAndType(roomCode, "user");

            // Load member details
            List<Map<String, String>> members = room.getMemberIds().stream()
                .map(mid -> userRepo.findById(mid).orElse(null))
                .filter(Objects::nonNull)
                .map(u -> Map.of(
                    "id", u.getId(),
                    "name", u.getName(),
                    "initials", avatarUtil.getInitials(u.getName()),
                    "avatarColor", avatarUtil.getAvatarColor(u.getId())
                )).collect(Collectors.toList());

            Message pinned = null;
            if (room.getPinnedMessageId() != null) {
                pinned = msgRepo.findById(room.getPinnedMessageId())
                    .filter(m -> !m.isDeleted()).orElse(null);
            }

            return ResponseEntity.ok(Map.of(
                "room", toResponse(room, userId),
                "members", members,
                "messageCount", msgCount,
                "pinnedMessage", pinned != null ? pinned : Map.of()
            ));
        }).orElse(ResponseEntity.status(404).body(Map.of("error", "Room not found")));
    }

    // ── DELETE /api/rooms/:roomCode ───────────────────────────────────────────
    public ResponseEntity<?> deleteRoom(String roomCode, String userId) {
        return roomRepo.findByRoomCode(roomCode).map(room -> {
            if (!room.getOwnerId().equals(userId))
                return ResponseEntity.status(403).body(Map.of("error", "Only the owner can delete"));

            msgRepo.deleteAllByRoomId(roomCode);
            roomRepo.delete(room);

            // Notify all connected clients — like: io.to(roomCode).emit("room_deleted", ...)
            ws.convertAndSend("/topic/room/" + roomCode + "/events",
                Map.of("type", "room_deleted", "roomCode", roomCode));

            return ResponseEntity.ok(Map.of("success", true));
        }).orElse(ResponseEntity.status(404).body(Map.of("error", "Room not found")));
    }

    // ── POST /api/rooms/:roomCode/pin ─────────────────────────────────────────
    public ResponseEntity<?> pinMessage(String roomCode, String messageId, String userId) {
        return roomRepo.findByRoomCode(roomCode).map(room -> {
            if (!room.getOwnerId().equals(userId))
                return ResponseEntity.status(403).body(Map.of("error", "Only owner can pin"));

            // Toggle: if same message → unpin
            if (messageId != null && messageId.equals(room.getPinnedMessageId())) {
                msgRepo.findById(messageId).ifPresent(m -> { m.setPinned(false); msgRepo.save(m); });
                room.setPinnedMessageId(null);
            } else {
                // Unpin old
                if (room.getPinnedMessageId() != null) {
                    msgRepo.findById(room.getPinnedMessageId()).ifPresent(m -> { m.setPinned(false); msgRepo.save(m); });
                }
                // Pin new
                if (messageId != null) {
                    msgRepo.findById(messageId).ifPresent(m -> { m.setPinned(true); msgRepo.save(m); });
                    room.setPinnedMessageId(messageId);
                }
            }
            roomRepo.save(room);

            Message pinned = room.getPinnedMessageId() != null
                ? msgRepo.findById(room.getPinnedMessageId()).orElse(null) : null;

            // Broadcast pin update — like: io.to(roomCode).emit("pin_updated", ...)
            ws.convertAndSend("/topic/room/" + roomCode + "/events",
                Map.of("type", "pin_updated", "pinnedMessage", pinned != null ? pinned : Map.of()));

            return ResponseEntity.ok(Map.of("success", true));
        }).orElse(ResponseEntity.status(404).body(Map.of("error", "Room not found")));
    }

    // ── safeRoom() equivalent ─────────────────────────────────────────────────
    public RoomResponse toResponse(Room room, String userId) {
        RoomResponse r = new RoomResponse();
        r.setId(room.getId());
        r.setRoomCode(room.getRoomCode());
        r.setName(room.getName());
        r.setOwner(room.getOwnerId().equals(userId));
        r.setMemberCount(room.getMemberIds().size());
        r.setPinnedMessageId(room.getPinnedMessageId());
        r.setCreatedAt(room.getCreatedAt() != null ? room.getCreatedAt().toString() : null);
        // Only send plain password to owner
        if (room.getOwnerId().equals(userId)) r.setPlainPassword(room.getPlainPassword());
        return r;
    }

    // ── generateRoomCode() equivalent ─────────────────────────────────────────
    private String generateUniqueCode() {
        String code;
        do {
            code = String.valueOf(100000 + new Random().nextInt(900000));
        } while (roomRepo.existsByRoomCode(code));
        return code;
    }
}
