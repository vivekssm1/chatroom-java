package com.chatroom.repository;

import com.chatroom.model.Room;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Equivalent to Room.findOne(), Room.find() in Node */
@Repository
public interface RoomRepository extends MongoRepository<Room, String> {

    Optional<Room> findByRoomCode(String roomCode);
    boolean existsByRoomCode(String roomCode);

    // await Room.find({ members: userId })  — find rooms user belongs to
    List<Room> findByMemberIdsContaining(String userId);

    // Admin search
    List<Room> findByNameRegexOrRoomCodeRegex(Pattern name, Pattern code);
}
