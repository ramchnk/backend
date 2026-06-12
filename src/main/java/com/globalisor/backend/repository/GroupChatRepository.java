package com.globalisor.backend.repository;

import com.globalisor.backend.model.GroupChat;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupChatRepository extends MongoRepository<GroupChat, String> {
    List<GroupChat> findByMemberIdsContaining(String memberId);
}
