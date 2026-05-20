package com.globalisor.backend.repository;

import com.globalisor.backend.model.Kyc;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KycRepository extends MongoRepository<Kyc, String> {
    Optional<Kyc> findByClientId(String clientId);
    long countByStatus(String status);
}
