package com.globalisor.backend.repository;

import com.globalisor.backend.model.StaticContent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StaticContentRepository extends MongoRepository<StaticContent, String> {
    List<StaticContent> findByPortal(String portal);
    List<StaticContent> findByCategory(String category);
    List<StaticContent> findByPortalAndCategory(String portal, String category);
}
