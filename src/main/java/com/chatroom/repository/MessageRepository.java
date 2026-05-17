package com.chatroom.repository;

import com.chatroom.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Equivalent to Message.find(), Message.create(), Message.deleteMany() */
@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

    // await Message.find({ roomId }).sort({ timestamp: 1 }).limit(200)
    List<Message> findByRoomIdOrderByTimestampAsc(String roomId, Pageable pageable);

    // await Message.countDocuments({ roomId, type: "user" })
    long countByRoomIdAndType(String roomId, String type);

    // await Message.deleteMany({ roomId })
    void deleteAllByRoomId(String roomId);
}
