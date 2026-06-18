package com.globalisor.backend.controller;

import com.globalisor.backend.model.Requirement;
import com.globalisor.backend.repository.RequirementRepository;
import com.globalisor.backend.security.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.HashMap;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/requirements")
public class RequirementController {
 
    @Autowired
    RequirementRepository requirementRepository;
 
    @Autowired
    private com.globalisor.backend.service.NotificationService notificationService;

    @GetMapping
    public ResponseEntity<?> getRequirement() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl)) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        
        Optional<Requirement> req = requirementRepository.findByUserId(userDetails.getId());
        if (req.isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", req.get().getStatus());
            response.put("data", req.get().getData());
            response.put("sectionStatuses", req.get().getSectionStatuses());
            return ResponseEntity.ok(response);
        } else {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "none");
            response.put("data", new HashMap<>());
            response.put("sectionStatuses", new HashMap<>());
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping
    public ResponseEntity<?> saveRequirement(@RequestBody Map<String, Object> data) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl)) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        
        Optional<Requirement> reqOpt = requirementRepository.findByUserId(userDetails.getId());
        Requirement requirement;
        boolean isNew = !reqOpt.isPresent();
        if (reqOpt.isPresent()) {
            requirement = reqOpt.get();
            requirement.setData(data);
            requirement.setUpdatedAt(new java.util.Date());
        } else {
            requirement = new Requirement(userDetails.getId(), data);
        }
        requirementRepository.save(requirement);
        
        if (isNew) {
            try {
                notificationService.sendNotification(
                        "admin",
                        "New Application Created",
                        userDetails.getFirstName() + " " + userDetails.getLastName() + " created a new application.",
                        "application",
                        requirement.getId(),
                        "Info"
                );
            } catch (Exception e) {}
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", requirement.getStatus());
        response.put("data", requirement.getData());
        response.put("sectionStatuses", requirement.getSectionStatuses());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submitRequirement() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl)) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        
        Optional<Requirement> reqOpt = requirementRepository.findByUserId(userDetails.getId());
        if (reqOpt.isPresent()) {
            Requirement requirement = reqOpt.get();
            requirement.setStatus("under review");
            requirement.setUpdatedAt(new java.util.Date());
            requirementRepository.save(requirement);
            
            try {
                // Admin notification
                notificationService.sendNotification(
                        "admin",
                        "New Pre-Registration Submission",
                        userDetails.getFirstName() + " " + userDetails.getLastName() + " submitted pre-registration requirements.",
                        "pre-registration",
                        requirement.getId(),
                        "Info"
                );
                // Client notification
                notificationService.sendNotification(
                        userDetails.getId(),
                        "Application Submitted Successfully",
                        "Your pre-registration requirements have been submitted successfully.",
                        "pre-registration",
                        requirement.getId(),
                        "Info"
                );
            } catch (Exception e) {}

            Map<String, Object> response = new HashMap<>();
            response.put("status", requirement.getStatus());
            response.put("data", requirement.getData());
            response.put("sectionStatuses", requirement.getSectionStatuses());
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body("No requirement found to submit");
        }
    }
}
