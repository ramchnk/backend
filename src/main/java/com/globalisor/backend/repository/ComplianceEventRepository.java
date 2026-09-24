package com.globalisor.backend.repository;

import com.globalisor.backend.model.ComplianceEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplianceEventRepository extends MongoRepository<ComplianceEvent, String> {
    List<ComplianceEvent> findByClientId(String clientId);
    List<ComplianceEvent> findByClientIdAndPublishedTrue(String clientId);

    @Query("{ '$and': [ { 'published': true }, { '$or': [ { 'clientId': 'all' }, { 'clientId': null }, { 'clientId': ?0 } ] } ] }")
    List<ComplianceEvent> findPublishedForClient(String clientId);
}
