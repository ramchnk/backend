package com.globalisor.backend.repository;

import com.globalisor.backend.model.Task;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends MongoRepository<Task, String> {

    Optional<Task> findByTicketNumber(String ticketNumber);

    List<Task> findByClientIdOrderByCreatedAtDesc(String clientId);

    List<Task> findByCompanyIdOrderByCreatedAtDesc(String companyId);

    @Query("{'assignedTo.id': ?0}")
    List<Task> findByAssignedToId(String staffId);

    List<Task> findByStatusOrderByCreatedAtDesc(String status);

    List<Task> findByCompanyNameContainingIgnoreCase(String companyName);

    long countByStatus(String status);

    long countByStatusIn(List<String> statuses);

    long countByStatusIgnoreCase(String status);

    long countByStatusInIgnoreCase(List<String> statuses);

    long countByAssignedToIsNull();
}
