package com.globalisor.backend.repository;

import com.globalisor.backend.model.Compliance;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ComplianceRepository extends MongoRepository<Compliance, String> {
}
