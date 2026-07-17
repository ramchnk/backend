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

import com.globalisor.backend.websocket.ChatWebSocketHandler;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    UserRepository userRepository;

    @Autowired
    RequirementRepository requirementRepository;

    @Autowired
    ChatWebSocketHandler chatWebSocketHandler;

    @GetMapping
    public ResponseEntity<?> getDashboardData() {
        List<User> users = userRepository.findAll().stream()
                .filter(u -> {
                    String role = u.getRole();
                    if (role == null) return true;
                    String trimmedRole = role.trim();
                    return !trimmedRole.equalsIgnoreCase("ADMIN") && !trimmedRole.equalsIgnoreCase("STAFF");
                })
                .collect(Collectors.toList());
        List<Requirement> requirements = requirementRepository.findAll();

        List<DashboardResponse.ClientInfo> clientInfos = users.stream().map(user -> {
            List<Requirement> userReqs = requirements.stream()
                    .filter(r -> r.getUserId().equals(user.getId()))
                    .collect(Collectors.toList());

            String latestStatus = userReqs.isEmpty() ? "pending" : userReqs.get(0).getStatus();
            String latestActivity = userReqs.isEmpty() ? "Joined" : userReqs.get(0).getUpdatedAt().toString();
            
            List<String> companyNames = new ArrayList<>();
            List<String> serviceTypes = new ArrayList<>();
            List<String> nomineeDirectors = new ArrayList<>();
            int pendingCount = 0, approvedCount = 0, rejectedCount = 0;
            String latestStaff = "Unassigned";

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
                
                // Parse nominee directors from excelData if present
                if (data != null && data.containsKey("excelData")) {
                    Object excelObj = data.get("excelData");
                    if (excelObj instanceof Map) {
                        Map<String, Object> excel = (Map<String, Object>) excelObj;
                        if (excel.containsKey("directors")) {
                            Object dirObj = excel.get("directors");
                            if (dirObj instanceof List) {
                                List<?> dirList = (List<?>) dirObj;
                                for (Object dObj : dirList) {
                                    if (dObj instanceof Map) {
                                        Map<String, Object> dir = (Map<String, Object>) dObj;
                                        Object typeObj = dir.get("type");
                                        Object nameObj = dir.get("name");
                                        if (typeObj != null && "Nominee Director".equalsIgnoreCase(typeObj.toString().trim()) && nameObj != null) {
                                            nomineeDirectors.add(nameObj.toString());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Service type
                Object sType = (data != null) ? data.get("serviceType") : null;
                serviceTypes.add(sType != null ? sType.toString() : "Company Incorporation");
                // Status counts
                String s = r.getStatus() != null ? r.getStatus().toLowerCase() : "pending";
                if (s.equals("approved") || s.equals("verified") || s.equals("completed")) approvedCount++;
                else if (s.equals("rejected")) rejectedCount++;
                else pendingCount++;
                // Staff
                if (r.getStaff() != null && !r.getStaff().isEmpty() && !r.getStaff().equals("Unassigned")) {
                    latestStaff = r.getStaff();
                }
            }

            boolean isOnline = chatWebSocketHandler.isUserOnline(user.getId());
            Long lastSeen = user.getLastSeenTime();

            return new DashboardResponse.ClientInfo(
                    user.getId(),
                    user.getFirstName() + " " + user.getLastName(),
                    user.getEmail(),
                    userReqs.size(),
                    latestActivity,
                    latestStatus,
                    companyNames,
                    "", // phone not in User model yet
                    0L,
                    pendingCount,
                    approvedCount,
                    rejectedCount,
                    serviceTypes,
                    latestStaff,
                    isOnline,
                    lastSeen,
                    nomineeDirectors
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
