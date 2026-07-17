package com.globalisor.backend.repository;

import com.globalisor.backend.model.MigrationSetting;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MigrationSettingRepository extends MongoRepository<MigrationSetting, String> {
}
