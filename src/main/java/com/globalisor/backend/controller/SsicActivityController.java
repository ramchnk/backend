package com.globalisor.backend.controller;

import com.globalisor.backend.model.SsicActivity;
import com.globalisor.backend.repository.SsicActivityRepository;
import com.globalisor.backend.security.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/ssic-activities")
public class SsicActivityController {

    @Autowired
    SsicActivityRepository ssicActivityRepository;

    private String getLoggedInAdminName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl) {
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            return userDetails.getFirstName() + " " + userDetails.getLastName();
        }
        return "Admin";
    }

    @GetMapping
    public ResponseEntity<List<SsicActivity>> getAllActivities() {
        return ResponseEntity.ok(ssicActivityRepository.findAll());
    }

    @GetMapping("/published")
    public ResponseEntity<List<SsicActivity>> getPublishedActivities() {
        return ResponseEntity.ok(ssicActivityRepository.findByStatus("PUBLISHED"));
    }

    @PostMapping
    public ResponseEntity<?> createActivity(@RequestBody SsicActivity activity) {
        if (activity.getId() == null || activity.getId().isEmpty()) {
            activity.setId("ssic-" + System.currentTimeMillis());
        }
        activity.setLastUpdatedBy(getLoggedInAdminName());
        activity.setLastUpdatedAt(new Date());
        if (activity.getStatus() == null) {
            activity.setStatus("DRAFT");
        }
        SsicActivity saved = ssicActivityRepository.save(activity);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateActivity(@PathVariable String id, @RequestBody SsicActivity activityUpdates) {
        Optional<SsicActivity> activityOpt = ssicActivityRepository.findById(id);
        if (activityOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        SsicActivity activity = activityOpt.get();
        activity.setCode(activityUpdates.getCode());
        activity.setName(activityUpdates.getName());
        activity.setCategory(activityUpdates.getCategory());
        activity.setDescription(activityUpdates.getDescription());
        activity.setStatus(activityUpdates.getStatus());
        activity.setLastUpdatedBy(getLoggedInAdminName());
        activity.setLastUpdatedAt(new Date());

        SsicActivity saved = ssicActivityRepository.save(activity);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteActivity(@PathVariable String id) {
        if (!ssicActivityRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        ssicActivityRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<?> publishActivity(@PathVariable String id) {
        Optional<SsicActivity> activityOpt = ssicActivityRepository.findById(id);
        if (activityOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        SsicActivity activity = activityOpt.get();
        activity.setStatus("PUBLISHED");
        activity.setLastUpdatedBy(getLoggedInAdminName());
        activity.setLastUpdatedAt(new Date());
        SsicActivity saved = ssicActivityRepository.save(activity);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{id}/unpublish")
    public ResponseEntity<?> unpublishActivity(@PathVariable String id) {
        Optional<SsicActivity> activityOpt = ssicActivityRepository.findById(id);
        if (activityOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        SsicActivity activity = activityOpt.get();
        activity.setStatus("UNPUBLISHED");
        activity.setLastUpdatedBy(getLoggedInAdminName());
        activity.setLastUpdatedAt(new Date());
        SsicActivity saved = ssicActivityRepository.save(activity);
        return ResponseEntity.ok(saved);
    }
}
