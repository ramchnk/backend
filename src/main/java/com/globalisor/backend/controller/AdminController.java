package com.globalisor.backend.controller;

import com.globalisor.backend.model.Requirement;
import com.globalisor.backend.model.User;
import com.globalisor.backend.model.Notification;
import com.globalisor.backend.repository.RequirementRepository;
import com.globalisor.backend.repository.UserRepository;
import com.globalisor.backend.repository.NotificationRepository;
import com.globalisor.backend.websocket.ChatWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.crypto.password.PasswordEncoder;
import com.globalisor.backend.security.EncryptionUtils;

import java.util.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api")
public class AdminController {

    @Autowired
    UserRepository userRepository;

    @Autowired
    RequirementRepository requirementRepository;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    ChatWebSocketHandler chatWebSocketHandler;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    EncryptionUtils encryptionUtils;

    @GetMapping("/clients/{id}/services")
    public ResponseEntity<?> getClientServices(@PathVariable String id) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();
        
        User user = userOpt.get();
        List<Requirement> requirements = requirementRepository.findAll();
        List<Map<String, Object>> userServices = new ArrayList<>();
        
        requirements.stream()
            .filter(r -> r.getUserId().equals(id))
            .forEach(r -> {
                Map<String, Object> service = new HashMap<>();
                service.put("serviceId", r.getId());
                service.put("status", r.getStatus());
                service.put("date", r.getUpdatedAt().toString());
                
                Map<String, Object> data = r.getData();
                String companyName = "New Incorporation";
                if (data != null && data.containsKey("names")) {
                    Object namesObj = data.get("names");
                    if (namesObj instanceof List) {
                        List<String> names = (List<String>) namesObj;
                        if (!names.isEmpty()) companyName = names.get(0);
                    }
                }
                
                service.put("companyName", companyName);
                service.put("serviceType", "Company Incorporation");
                service.put("details", data);
                service.put("sectionStatuses", r.getSectionStatuses());
                service.put("totalPrice", "SGD 1,500");
                service.put("staff", r.getStaff() != null ? r.getStaff() : "Unassigned");
                userServices.add(service);
            });

        Map<String, Object> response = new HashMap<>();
        Map<String, Object> clientMap = new HashMap<>();
        clientMap.put("clientId", user.getId());
        clientMap.put("name", user.getFirstName() + " " + user.getLastName());
        clientMap.put("email", user.getEmail());
        clientMap.put("createdAt", new Date()); 
        
        response.put("client", clientMap);
        response.put("services", userServices);
        
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/services/{id}")
    public ResponseEntity<?> updateServiceStatus(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Optional<Requirement> reqOpt = requirementRepository.findById(id);
        if (reqOpt.isEmpty()) return ResponseEntity.notFound().build();
        
        Requirement req = reqOpt.get();
        boolean statusChanged = false;
        String newStatus = null;
        if (body.containsKey("status")) {
            newStatus = (String) body.get("status");
            if (!Objects.equals(newStatus, req.getStatus())) {
                req.setStatus(newStatus);
                statusChanged = true;
            }
        }
        
        boolean staffChanged = false;
        String newStaff = null;
        if (body.containsKey("staff")) {
            newStaff = (String) body.get("staff");
            if (!Objects.equals(newStaff, req.getStaff())) {
                req.setStaff(newStaff);
                staffChanged = true;
            }
        }
        
        if (body.containsKey("sectionStatuses")) {
            req.setSectionStatuses((Map<String, Map<String, Object>>) body.get("sectionStatuses"));
        }
        req.setUpdatedAt(new Date());
        Requirement saved = requirementRepository.save(req);

        if (statusChanged) {
            Notification notif = new Notification();
            notif.setId("notif-" + System.currentTimeMillis());
            notif.setClientId(req.getUserId());
            notif.setTitle("Application Status Update");
            String companyName = "Your incorporation application";
            Map<String, Object> data = req.getData();
            if (data != null && data.containsKey("names")) {
                Object namesObj = data.get("names");
                if (namesObj instanceof List) {
                    List<String> names = (List<String>) namesObj;
                    if (!names.isEmpty()) companyName = names.get(0);
                }
            }
            notif.setMessage("The status of " + companyName + " has been updated to '" + newStatus + "'.");
            notif.setType("status_update");
            notif.setRelatedId(req.getId());
            notif.setTimestamp(System.currentTimeMillis());
            notif.setReadBy(new ArrayList<>());
            
            notificationRepository.save(notif);
            chatWebSocketHandler.broadcastNotification(notif);
        }

        if (staffChanged && newStaff != null && !newStaff.equalsIgnoreCase("Unassigned")) {
            String staffId = null;
            List<User> users = userRepository.findAll();
            for (User u : users) {
                if ("STAFF".equalsIgnoreCase(u.getRole()) || "ADMIN".equalsIgnoreCase(u.getRole())) {
                    String fullName = (u.getFirstName() + " " + u.getLastName()).trim();
                    if (fullName.equalsIgnoreCase(newStaff.trim())) {
                        staffId = u.getId();
                        break;
                    }
                }
            }
            if (staffId != null) {
                Notification notif = new Notification();
                notif.setId("notif-" + System.currentTimeMillis());
                notif.setClientId(staffId);
                notif.setTitle("New Task Assigned");
                String companyName = "incorporation application";
                Map<String, Object> data = req.getData();
                if (data != null && data.containsKey("names")) {
                    Object namesObj = data.get("names");
                    if (namesObj instanceof List) {
                        List<String> names = (List<String>) namesObj;
                        if (!names.isEmpty()) companyName = names.get(0);
                    }
                }
                notif.setMessage("You have been assigned to " + companyName + ".");
                notif.setType("assignment");
                notif.setRelatedId(req.getId());
                notif.setTimestamp(System.currentTimeMillis());
                notif.setReadBy(new ArrayList<>());
                
                notificationRepository.save(notif);
                chatWebSocketHandler.broadcastNotification(notif);
            }
        }
        
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/documents/request")
    public ResponseEntity<?> requestDocument(@RequestBody Map<String, String> body) {
        String clientId = body.get("clientId");
        String documentType = body.get("documentType");
        
        if (clientId == null || documentType == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "clientId and documentType are required"));
        }
        
        Notification notif = new Notification();
        notif.setId("notif-" + System.currentTimeMillis());
        notif.setClientId(clientId);
        notif.setTitle("Document Upload Request");
        notif.setMessage("Please upload your " + documentType + " as requested.");
        notif.setType("document_request");
        notif.setRelatedId(documentType);
        notif.setTimestamp(System.currentTimeMillis());
        notif.setReadBy(new ArrayList<>());
        
        notificationRepository.save(notif);
        chatWebSocketHandler.broadcastNotification(notif);
        
        return ResponseEntity.ok(notif);
    }



    @GetMapping("/services/debug/{id}")
    public ResponseEntity<?> debugService(@PathVariable String id) {
        Optional<Requirement> reqOpt = requirementRepository.findById(id);
        if (reqOpt.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(reqOpt.get());
    }

    @PostMapping("/admin/staff")
    public ResponseEntity<?> createStaff(@RequestBody Map<String, String> body) {
        String firstName = body.get("firstName");
        String lastName = body.get("lastName");
        
        // Generate email: firstname.lastname@globalisor.com
        String baseEmail = (firstName + "." + lastName).toLowerCase().replaceAll("[^a-z0-9]", "");
        String email = baseEmail + "@globalisor.com";
        
        // Check uniqueness and append suffix if exists
        String encryptedEmail = encryptionUtils.encryptQueryable(email);
        int suffix = 1;
        while (userRepository.existsByEmail(encryptedEmail)) {
            email = baseEmail + suffix + "@globalisor.com";
            encryptedEmail = encryptionUtils.encryptQueryable(email);
            suffix++;
        }
        
        // Generate password: Glob-[4-digit-number]
        int randomNum = (int) (Math.random() * 9000) + 1000;
        String rawPassword = "Glob-" + randomNum;
        String encodedPassword = encoder.encode(rawPassword);
        
        User staff = new User(firstName, lastName, email, encodedPassword);
        staff.setRole("STAFF");
        staff.setPlainPassword(rawPassword);
        userRepository.save(staff);
        
        Map<String, String> response = new HashMap<>();
        response.put("email", email);
        response.put("password", rawPassword);
        response.put("id", staff.getId());
        response.put("firstName", firstName);
        response.put("lastName", lastName);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/staff")
    public ResponseEntity<?> getStaffList() {
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> staffList = new ArrayList<>();
        for (User u : users) {
            if ("STAFF".equalsIgnoreCase(u.getRole())) {
                Map<String, Object> staff = new HashMap<>();
                staff.put("id", u.getId());
                staff.put("firstName", u.getFirstName());
                staff.put("lastName", u.getLastName());
                staff.put("email", u.getEmail());
                staff.put("password", u.getPlainPassword() != null ? u.getPlainPassword() : "password123");
                staffList.add(staff);
            }
        }
        return ResponseEntity.ok(staffList);
    }
}
