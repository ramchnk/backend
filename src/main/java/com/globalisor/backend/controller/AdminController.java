package com.globalisor.backend.controller;

import com.globalisor.backend.model.Requirement;
import com.globalisor.backend.model.User;
import com.globalisor.backend.repository.RequirementRepository;
import com.globalisor.backend.repository.UserRepository;
import com.globalisor.backend.security.EncryptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

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
    private PasswordEncoder encoder;

    @Autowired
    private EncryptionUtils encryptionUtils;

    @GetMapping("/admin/staff")
    public ResponseEntity<?> getStaffList() {
        List<User> staffList = userRepository.findByRole("STAFF");
        return ResponseEntity.ok(staffList);
    }

    @PostMapping("/admin/staff")
    public ResponseEntity<?> createStaff(@RequestBody Map<String, String> body) {
        String firstName = body.get("firstName");
        String lastName = body.get("lastName");

        if (firstName == null || lastName == null || firstName.isEmpty() || lastName.isEmpty()) {
            return ResponseEntity.badRequest().body("First name and last name are required");
        }

        // Generate unique email
        String baseEmail = (firstName + "." + lastName).toLowerCase().replaceAll("[^a-z0-9]", "");
        String email = baseEmail + "@globalisor.com";
        int suffix = 1;
        while (userRepository.existsByEmail(encryptionUtils.encryptQueryable(email))) {
            email = baseEmail + suffix + "@globalisor.com";
            suffix++;
        }

        // Generate random password
        String password = "Glob-" + (1000 + new Random().nextInt(9000));

        // Create new user
        User newStaff = new User(firstName, lastName, email, encoder.encode(password));
        newStaff.setRole("STAFF");
        newStaff.setId("staff-" + System.currentTimeMillis());

        userRepository.save(newStaff);

        // Return credentials
        Map<String, Object> response = new HashMap<>();
        response.put("id", newStaff.getId());
        response.put("firstName", newStaff.getFirstName());
        response.put("lastName", newStaff.getLastName());
        response.put("email", newStaff.getEmail());
        response.put("password", password);
        response.put("role", newStaff.getRole());

        return ResponseEntity.ok(response);
    }

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
                service.put("totalPrice", "SGD 1,500");
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
    public ResponseEntity<?> updateServiceStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        Optional<Requirement> reqOpt = requirementRepository.findById(id);
        if (reqOpt.isEmpty()) return ResponseEntity.notFound().build();
        
        Requirement req = reqOpt.get();
        if (body.containsKey("status")) {
            req.setStatus(body.get("status"));
        }
        if (body.containsKey("staff")) {
            req.setStaff(body.get("staff"));
        }
        req.setUpdatedAt(new Date());
        requirementRepository.save(req);
        
        return ResponseEntity.ok(req);
    }
}

