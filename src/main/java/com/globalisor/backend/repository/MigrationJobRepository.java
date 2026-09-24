package com.globalisor.backend.repository;

import com.globalisor.backend.model.MigrationJob;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MigrationJobRepository extends MongoRepository<MigrationJob, String> {
    List<MigrationJob> findByCreatedAtGreaterThanEqual(long timestamp);
}
