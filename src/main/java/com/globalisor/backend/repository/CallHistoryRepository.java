package com.globalisor.backend.repository;

import com.globalisor.backend.model.CallHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CallHistoryRepository extends MongoRepository<CallHistory, String> {
    List<CallHistory> findByCallerIdOrReceiverIdOrderByTimestampDesc(String callerId, String receiverId);
}
