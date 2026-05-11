package com.globalisor.backend.controller;

import com.globalisor.backend.model.Requirement;
import com.globalisor.backend.model.User;
import com.globalisor.backend.payload.response.DashboardResponse;
import com.globalisor.backend.repository.RequirementRepository;
import com.globalisor.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    UserRepository userRepository;

    @Autowired
    RequirementRepository requirementRepository;

    @GetMapping
    public ResponseEntity<?> getDashboardData() {
        List<User> users = userRepository.findAll();
        List<Requirement> requirements = requirementRepository.findAll();

        List<DashboardResponse.ClientInfo> clientInfos = users.stream().map(user -> {
            List<Requirement> userReqs = requirements.stream()
                    .filter(r -> r.getUserId().equals(user.getId()))
                    .collect(Collectors.toList());

            String latestStatus = userReqs.isEmpty() ? "pending" : userReqs.get(0).getStatus();
            String latestActivity = userReqs.isEmpty() ? "Joined" : userReqs.get(0).getUpdatedAt().toString();
            
            List<String> companyNames = new ArrayList<>();
            for (Requirement r : userReqs) {
                Map<String, Object> data = r.getData();
                if (data != null && data.containsKey("names")) {
                    Object namesObj = data.get("names");
                    if (namesObj instanceof List) {
                        List<String> names = (List<String>) namesObj;
                        if (!names.isEmpty() && names.get(0) != null && !names.get(0).isEmpty()) {
                            companyNames.add(names.get(0));
                        }
                    }
                }
            }

            return new DashboardResponse.ClientInfo(
                    user.getId(),
                    user.getFirstName() + " " + user.getLastName(),
                    user.getEmail(),
                    userReqs.size(),
                    latestActivity,
                    latestStatus,
                    companyNames,
                    "", 
                    0 
            );
        }).collect(Collectors.toList());

        Map<String, Long> stats = new HashMap<>();
        stats.put("totalClients", (long) users.size());
        stats.put("totalServices", (long) requirements.size());
        stats.put("pending", requirements.stream().filter(r -> "pending".equals(r.getStatus())).count());
        stats.put("approved", requirements.stream().filter(r -> "approved".equals(r.getStatus())).count());
        stats.put("rejected", requirements.stream().filter(r -> "rejected".equals(r.getStatus())).count());

        return ResponseEntity.ok(new DashboardResponse(clientInfos, stats));
    }
}
