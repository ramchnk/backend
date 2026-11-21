package com.globalisor.backend.repository;

import com.globalisor.backend.model.AmlWatchlistRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AmlWatchlistRepository extends MongoRepository<AmlWatchlistRecord, String> {

    List<AmlWatchlistRecord> findByNormalizedName(String normalizedName);

    @Query("{ 'normalizedAliases': ?0 }")
    List<AmlWatchlistRecord> findByNormalizedAlias(String normalizedAlias);

    List<AmlWatchlistRecord> findBySource(String source);

    long countBySource(String source);

    @Query("{ 'passportsOrIds': { $regex: ?0, $options: 'i' } }")
    List<AmlWatchlistRecord> findByPassportOrId(String idNumber);

    @Query("{ $or: [ " +
           "{ 'normalizedName': { $regex: ?0, $options: 'i' } }, " +
           "{ 'normalizedAliases': { $regex: ?0, $options: 'i' } } " +
           "] }")
    List<AmlWatchlistRecord> findByNameCandidate(String namePart, Pageable pageable);

    @Query("{ $or: [ " +
           "{ 'normalizedName': { $regex: ?0, $options: 'i' } }, " +
           "{ 'normalizedAliases': { $regex: ?0, $options: 'i' } } " +
           "] }")
    List<AmlWatchlistRecord> findByNameCandidate(String namePart);

    @Query("{ $or: [ " +
           "{ 'primaryName': { $regex: ?0, $options: 'i' } }, " +
           "{ 'normalizedName': { $regex: ?0, $options: 'i' } }, " +
           "{ 'aliases': { $regex: ?0, $options: 'i' } }, " +
           "{ 'passportsOrIds': { $regex: ?0, $options: 'i' } } " +
           "] }")
    Page<AmlWatchlistRecord> searchRecords(String query, Pageable pageable);
}
