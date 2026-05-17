package com.chatroom.repository;

import com.chatroom.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Equivalent to User.findOne(), User.find(), User.create() in Node.js
 *
 * MongoRepository<User, String> gives you for free:
 *   save(user)           → await User.create(data) / user.save()
 *   findById(id)         → await User.findById(id)
 *   findAll()            → await User.find()
 *   deleteById(id)       → await User.deleteOne({_id: id})
 *   count()              → await User.countDocuments()
 */
@Repository
public interface UserRepository extends MongoRepository<User, String> {

    // await User.findOne({ mobile }) in Node
    Optional<User> findByMobile(String mobile);

    // await User.exists({ mobile }) in Node
    boolean existsByMobile(String mobile);

    // await User.countDocuments({ banned: true })
    long countByBannedTrue();

    // Search by name or mobile — used in admin panel
    List<User> findByNameRegexOrMobileRegex(Pattern name, Pattern mobile);
}
