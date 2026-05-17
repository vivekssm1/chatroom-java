package com.chatroom.repository;

import com.chatroom.model.Activity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityRepository extends MongoRepository<Activity, String> {

    // await Activity.find().sort({ timestamp: -1 }).limit(100)
    List<Activity> findTop100ByOrderByTimestampDesc();
}
