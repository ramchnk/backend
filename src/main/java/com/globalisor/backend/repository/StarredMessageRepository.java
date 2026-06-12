package com.globalisor.backend.repository;

import com.globalisor.backend.model.StarredMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StarredMessageRepository extends MongoRepository<StarredMessage, String> {
    List<StarredMessage> findByUserId(String userId);
    Optional<StarredMessage> findByUserIdAndMessageId(String userId, String messageId);
}
