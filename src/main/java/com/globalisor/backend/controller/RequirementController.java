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

    @GetMapping
    public ResponseEntity<?> getRequirement() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        
        Optional<Requirement> req = requirementRepository.findByUserId(userDetails.getId());
        if (req.isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", req.get().getStatus());
            response.put("data", req.get().getData());
            return ResponseEntity.ok(response);
        } else {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "none");
            response.put("data", new HashMap<>());
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping
    public ResponseEntity<?> saveRequirement(@RequestBody Map<String, Object> data) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        
        Optional<Requirement> reqOpt = requirementRepository.findByUserId(userDetails.getId());
        Requirement requirement;
        if (reqOpt.isPresent()) {
            requirement = reqOpt.get();
            requirement.setData(data);
            requirement.setUpdatedAt(new java.util.Date());
        } else {
            requirement = new Requirement(userDetails.getId(), data);
        }
        requirementRepository.save(requirement);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", requirement.getStatus());
        response.put("data", requirement.getData());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submitRequirement() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        
        Optional<Requirement> reqOpt = requirementRepository.findByUserId(userDetails.getId());
        if (reqOpt.isPresent()) {
            Requirement requirement = reqOpt.get();
            requirement.setStatus("under review");
            requirement.setUpdatedAt(new java.util.Date());
            requirementRepository.save(requirement);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", requirement.getStatus());
            response.put("data", requirement.getData());
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body("No requirement found to submit");
        }
    }
}
