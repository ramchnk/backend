package com.globalisor.backend.repository;

import com.globalisor.backend.model.AcraEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AcraEntityRepository extends MongoRepository<AcraEntity, String> {

    Optional<AcraEntity> findByUen(String uen);

    List<AcraEntity> findByNormalizedName(String normalizedName);

    List<AcraEntity> findByBaseName(String baseName);

    List<AcraEntity> findByBaseNameAndIsLive(String baseName, boolean isLive);

    long countByIsLive(boolean isLive);

    long countByEntityType(String entityType);

    @Query("{ $or: [ { 'normalizedName': { $regex: ?0, $options: 'i' } }, { 'baseName': { $regex: ?0, $options: 'i' } } ] }")
    List<AcraEntity> findByNameCandidate(String candidate, Pageable pageable);

    @Query("{ 'isLive': true, $or: [ { 'baseName': { $regex: ?0, $options: 'i' } }, { 'baseName': { $regex: ?1, $options: 'i' } } ] }")
    List<AcraEntity> findSimilarCandidates(String vowelRegex, String prefixRegex, Pageable pageable);

    @Query("{ $or: [ { 'uen': { $regex: ?0, $options: 'i' } }, { 'entityName': { $regex: ?0, $options: 'i' } } ] }")
    Page<AcraEntity> searchEntities(String query, Pageable pageable);
}
