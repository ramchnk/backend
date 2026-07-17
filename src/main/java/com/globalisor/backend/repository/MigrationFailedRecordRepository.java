package com.globalisor.backend.repository;

import com.globalisor.backend.model.MigrationFailedRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MigrationFailedRecordRepository extends MongoRepository<MigrationFailedRecord, String> {
}
