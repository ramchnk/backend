package com.globalisor.backend.repository;

import com.globalisor.backend.model.Requirement;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RequirementRepository extends MongoRepository<Requirement, String> {
    Optional<Requirement> findByUserId(String userId);
}
