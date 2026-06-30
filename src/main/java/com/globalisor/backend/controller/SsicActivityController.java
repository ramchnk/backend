package com.globalisor.backend.controller;

import com.globalisor.backend.model.SsicActivity;
import com.globalisor.backend.repository.SsicActivityRepository;
import com.globalisor.backend.security.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    @PostMapping("/bulk")
    public ResponseEntity<?> bulkImport(@RequestBody List<SsicActivity> activities) {
        List<SsicActivity> toSave = new ArrayList<>();
        Date now = new Date();
        String updatedBy = getLoggedInAdminName();
        for (SsicActivity a : activities) {
            if (a.getId() == null || a.getId().isEmpty()) {
                a.setId("ssic-" + a.getCode());
            }
            a.setLastUpdatedBy(updatedBy);
            a.setLastUpdatedAt(now);
            if (a.getStatus() == null || a.getStatus().isEmpty()) {
                a.setStatus("PUBLISHED");
            }
            toSave.add(a);
        }
        List<SsicActivity> saved = ssicActivityRepository.saveAll(toSave);
        return ResponseEntity.ok(Map.of("imported", saved.size()));
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

    @PostMapping("/import")
    public ResponseEntity<?> importActivities(@RequestBody Map<String, List<SsicActivity>> payload) {
        if (payload == null || !payload.containsKey("activities")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid payload: activities array required"));
        }
        List<SsicActivity> imported = payload.get("activities");
        if (imported == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid payload: activities array required"));
        }

        int addedCount = 0;
        int updatedCount = 0;

        String updatedBy = getLoggedInAdminName();
        Date now = new Date();

        for (SsicActivity item : imported) {
            if (item.getCode() == null || item.getCode().isEmpty()) {
                continue;
            }

            Optional<SsicActivity> existingOpt = ssicActivityRepository.findByCode(item.getCode());
            if (existingOpt.isPresent()) {
                SsicActivity existing = existingOpt.get();
                boolean hasChanges = false;

                if (!Objects.equals(item.getName(), existing.getName())) hasChanges = true;
                if (!Objects.equals(item.getCategory(), existing.getCategory())) hasChanges = true;
                if (!Objects.equals(item.getDescription(), existing.getDescription())) hasChanges = true;

                if (hasChanges) {
                    existing.setName(item.getName());
                    existing.setCategory(item.getCategory());
                    existing.setDescription(item.getDescription());
                    existing.setStatus("DRAFT");
                    existing.setLastUpdatedBy(updatedBy);
                    existing.setLastUpdatedAt(now);
                    ssicActivityRepository.save(existing);
                    updatedCount++;
                }
            } else {
                SsicActivity newActivity = new SsicActivity();
                newActivity.setId("ssic-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000));
                newActivity.setCode(item.getCode());
                newActivity.setName(item.getName());
                newActivity.setCategory(item.getCategory());
                newActivity.setDescription(item.getDescription());
                newActivity.setStatus("DRAFT");
                newActivity.setLastUpdatedBy(updatedBy);
                newActivity.setLastUpdatedAt(now);
                ssicActivityRepository.save(newActivity);
                addedCount++;
            }
        }

        return ResponseEntity.ok(Map.of("success", true, "added", addedCount, "updated", updatedCount));
    }
}
