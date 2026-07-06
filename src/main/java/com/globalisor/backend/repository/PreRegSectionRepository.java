package com.globalisor.backend.repository;

import com.globalisor.backend.model.PreRegSection;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PreRegSectionRepository extends MongoRepository<PreRegSection, String> {
    Optional<PreRegSection> findByKey(String key);
    java.util.List<PreRegSection> findByJourneyType(String journeyType);
    Optional<PreRegSection> findByKeyAndJourneyType(String key, String journeyType);
}
