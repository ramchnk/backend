package com.globalisor.backend.repository;

import com.globalisor.backend.model.ClientDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClientDocumentRepository extends MongoRepository<ClientDocument, String> {
    List<ClientDocument> findByClientId(String clientId);
}
