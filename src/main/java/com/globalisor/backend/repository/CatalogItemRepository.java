package com.globalisor.backend.repository;

import com.globalisor.backend.model.CatalogItem;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogItemRepository extends MongoRepository<CatalogItem, String> {
}
