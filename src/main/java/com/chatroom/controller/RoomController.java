package com.chatroom.controller;

import com.chatroom.dto.Dtos.*;
import com.chatroom.model.Message;
import com.chatroom.repository.MessageRepository;
import com.chatroom.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Equivalent to room routes in server.js:
 *
 *   app.post  ("/api/rooms/create", authMiddleware, ...)
 *   app.post  ("/api/rooms/join", authMiddleware, ...)
 *   app.get   ("/api/rooms", authMiddleware, ...)
 *   app.get   ("/api/rooms/:roomCode", authMiddleware, ...)
 *   app.delete("/api/rooms/:roomCode", authMiddleware, ...)
 *   app.post  ("/api/rooms/:roomCode/pin", authMiddleware, ...)
 *   app.post  ("/api/messages/:messageId/view", authMiddleware, ...)
 */
@RestController
@RequestMapping("/api")
public class RoomController {

    @Autowired private RoomService roomService;
    @Autowired private MessageRepository msgRepo;
    @Autowired private SimpMessagingTemplate ws;

    // POST /api/rooms/create
    @PostMapping("/rooms/create")
    public ResponseEntity<?> createRoom(@Valid @RequestBody CreateRoomRequest req,
                                        Authentication auth) {
        return roomService.createRoom(req.getName(), req.getPassword(), auth.getName());
    }

    // POST /api/rooms/join
    @PostMapping("/rooms/join")
    public ResponseEntity<?> joinRoom(@Valid @RequestBody JoinRoomRequest req,
                                      Authentication auth) {
        return roomService.joinRoom(req.getRoomCode(), req.getPassword(), auth.getName());
    }

    // GET /api/rooms
    @GetMapping("/rooms")
    public ResponseEntity<?> getRooms(Authentication auth) {
        return roomService.getUserRooms(auth.getName());
    }

    // GET /api/rooms/:roomCode
    @GetMapping("/rooms/{roomCode}")
    public ResponseEntity<?> getRoomDetail(@PathVariable String roomCode,
                                           Authentication auth) {
        return roomService.getRoomDetail(roomCode, auth.getName());
    }

    // DELETE /api/rooms/:roomCode
    @DeleteMapping("/rooms/{roomCode}")
    public ResponseEntity<?> deleteRoom(@PathVariable String roomCode,
                                        Authentication auth) {
        return roomService.deleteRoom(roomCode, auth.getName());
    }

    // POST /api/rooms/:roomCode/pin
    @PostMapping("/rooms/{roomCode}/pin")
    public ResponseEntity<?> pinMessage(@PathVariable String roomCode,
                                        @RequestBody PinRequest req,
                                        Authentication auth) {
        return roomService.pinMessage(roomCode, req.getMessageId(), auth.getName());
    }

    /**
     * POST /api/messages/:messageId/view
     * Equivalent to the 2-view media logic in server.js
     */
    @PostMapping("/messages/{messageId}/view")
    public ResponseEntity<?> viewMedia(@PathVariable String messageId,
                                       Authentication auth) {
        String userId = auth.getName();

        return msgRepo.findById(messageId).map(msg -> {
            if (msg.getMediaData() == null)
                return ResponseEntity.status(404).body(Map.of("error", "Not found"));

            if ("2view".equals(msg.getMediaMode())) {
                if (!msg.getViewedBy().contains(userId)) {
                    msg.getViewedBy().add(userId);
                    msg.setViewCount(msg.getViewCount() + 1);
                    msgRepo.save(msg);

                    // Once 2 unique users have viewed — delete media (same logic as Node)
                    if (msg.getViewCount() >= 2) {
                        msg.setMediaData(null);
                        msg.setDeleted(true);
                        msgRepo.save(msg);

                        // Notify room: io.to(msg.roomId).emit("message_deleted", { messageId })
                        ws.convertAndSend("/topic/room/" + msg.getRoomId() + "/events",
                            Map.of("type", "message_deleted", "messageId", msg.getId()));

                        return ResponseEntity.ok(Map.of("success", true, "expired", true));
                    }
                }
            }

            return ResponseEntity.ok(Map.of(
                "success",   true,
                "mediaData", msg.getMediaData(),
                "mediaMime", msg.getMediaMime(),
                "mediaType", msg.getMediaType(),
                "expired",   false
            ));
        }).orElse(ResponseEntity.status(404).body(Map.of("error", "Message not found")));
    }
}
