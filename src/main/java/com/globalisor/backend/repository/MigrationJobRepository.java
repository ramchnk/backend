package com.globalisor.backend.repository;

import com.globalisor.backend.model.MigrationJob;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MigrationJobRepository extends MongoRepository<MigrationJob, String> {
}
