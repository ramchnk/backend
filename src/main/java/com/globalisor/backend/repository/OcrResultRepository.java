package com.globalisor.backend.repository;

import com.globalisor.backend.model.OcrResult;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OcrResultRepository extends MongoRepository<OcrResult, String> {
    List<OcrResult> findByUserId(String userId);
    List<OcrResult> findByRequirementId(String requirementId);
    Optional<OcrResult> findByUserIdAndFieldPath(String userId, String fieldPath);
}
