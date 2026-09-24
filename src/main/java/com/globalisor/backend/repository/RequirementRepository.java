package com.globalisor.backend.repository;

import com.globalisor.backend.model.Requirement;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RequirementRepository extends MongoRepository<Requirement, String> {
    Optional<Requirement> findByUserId(String userId);
    List<Requirement> findByUserIdIn(List<String> userIds);
    long countByStatusIgnoreCase(String status);
    long countByStatusIn(List<String> statuses);
}
