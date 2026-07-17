package com.globalisor.backend.repository;

import com.globalisor.backend.model.MigrationTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MigrationTemplateRepository extends MongoRepository<MigrationTemplate, String> {
}
