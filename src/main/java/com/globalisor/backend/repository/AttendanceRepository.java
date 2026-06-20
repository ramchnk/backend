package com.globalisor.backend.repository;

import com.globalisor.backend.model.Attendance;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends MongoRepository<Attendance, String> {
    Optional<Attendance> findByUserIdAndDate(String userId, String date);
    List<Attendance> findByUserIdOrderByDateDesc(String userId);
    List<Attendance> findByDate(String date);
}
