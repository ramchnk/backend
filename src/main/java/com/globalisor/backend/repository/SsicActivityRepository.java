package com.globalisor.backend.repository;

import com.globalisor.backend.model.SsicActivity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SsicActivityRepository extends MongoRepository<SsicActivity, String> {
    List<SsicActivity> findByStatus(String status);
    Optional<SsicActivity> findByCode(String code);
}
