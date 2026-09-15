package com.globalisor.backend.repository;

import com.globalisor.backend.model.DocumentCategory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentCategoryRepository extends MongoRepository<DocumentCategory, String> {
    List<DocumentCategory> findAllByOrderBySortOrderAsc();
    Optional<DocumentCategory> findByKeyIgnoreCase(String key);
    Optional<DocumentCategory> findByKeyIgnoreCaseAndParentKeyIgnoreCase(String key, String parentKey);
    List<DocumentCategory> findByParentKeyIgnoreCase(String parentKey);
    List<DocumentCategory> findByParentId(String parentId);
    List<DocumentCategory> findByParentKeyIsNull();
    void deleteByKeyIgnoreCase(String key);
}
