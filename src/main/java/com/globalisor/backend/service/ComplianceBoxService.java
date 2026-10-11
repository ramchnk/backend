package com.globalisor.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globalisor.backend.model.Requirement;
import com.globalisor.backend.model.User;
import com.globalisor.backend.repository.RequirementRepository;
import com.globalisor.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.*;

@Service
public class ComplianceBoxService {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceBoxService.class);

    @Autowired
    private RequirementRepository requirementRepository;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> syncApplication(String applicationId) {
        Map<String, Object> response = new HashMap<>();
        try {
            Requirement req = findRequirementFlexible(applicationId);
            if (req == null) {
                response.put("success", false);
                response.put("error", "Application not found: " + applicationId);
                return response;
            }

            Map<String, Object> data = req.getData() != null ? req.getData() : new HashMap<>();
            
            // Check linked user for fallback details
            User linkedUser = null;
            if (req.getUserId() != null) {
                linkedUser = userRepository.findById(req.getUserId()).orElse(null);
            }

            // Resolve company/client name
            String name = null;
            if (data.containsKey("companyName") && data.get("companyName") != null && !data.get("companyName").toString().trim().isEmpty()) {
                name = data.get("companyName").toString().trim();
            } else if (data.containsKey("proposedName1") && data.get("proposedName1") != null && !data.get("proposedName1").toString().trim().isEmpty()) {
                name = data.get("proposedName1").toString().trim();
            } else if (data.containsKey("proposedName") && data.get("proposedName") != null && !data.get("proposedName").toString().trim().isEmpty()) {
                name = data.get("proposedName").toString().trim();
            } else if (data.containsKey("clientName") && data.get("clientName") != null && !data.get("clientName").toString().trim().isEmpty()) {
                name = data.get("clientName").toString().trim();
            } else if (linkedUser != null && linkedUser.getCompanyName() != null && !linkedUser.getCompanyName().trim().isEmpty()) {
                name = linkedUser.getCompanyName().trim();
            } else if (linkedUser != null) {
                String fullName = ((linkedUser.getFirstName() != null ? linkedUser.getFirstName() : "") + " " + 
                                   (linkedUser.getLastName() != null ? linkedUser.getLastName() : "")).trim();
                name = !fullName.isEmpty() ? fullName : "Client " + req.getId();
            } else {
                name = "Client Application " + req.getId();
            }

            // Resolve contact person
            String contactPerson = null;
            if (data.containsKey("contactPerson") && data.get("contactPerson") != null && !data.get("contactPerson").toString().trim().isEmpty()) {
                contactPerson = data.get("contactPerson").toString().trim();
            } else if (data.containsKey("applicantName") && data.get("applicantName") != null && !data.get("applicantName").toString().trim().isEmpty()) {
                contactPerson = data.get("applicantName").toString().trim();
            } else if (data.containsKey("fullName") && data.get("fullName") != null && !data.get("fullName").toString().trim().isEmpty()) {
                contactPerson = data.get("fullName").toString().trim();
            } else if (linkedUser != null) {
                String fullName = ((linkedUser.getFirstName() != null ? linkedUser.getFirstName() : "") + " " + 
                                   (linkedUser.getLastName() != null ? linkedUser.getLastName() : "")).trim();
                if (!fullName.isEmpty()) contactPerson = fullName;
            }

            // Resolve email
            String email = null;
            if (data.containsKey("email") && data.get("email") != null && !data.get("email").toString().trim().isEmpty()) {
                email = data.get("email").toString().trim();
            } else if (data.containsKey("applicantEmail") && data.get("applicantEmail") != null && !data.get("applicantEmail").toString().trim().isEmpty()) {
                email = data.get("applicantEmail").toString().trim();
            } else if (linkedUser != null && linkedUser.getEmail() != null) {
                email = linkedUser.getEmail();
            }

            // Resolve phone
            String phone = null;
            if (data.containsKey("phone") && data.get("phone") != null && !data.get("phone").toString().trim().isEmpty()) {
                phone = data.get("phone").toString().trim();
            } else if (data.containsKey("applicantPhone") && data.get("applicantPhone") != null && !data.get("applicantPhone").toString().trim().isEmpty()) {
                phone = data.get("applicantPhone").toString().trim();
            } else if (linkedUser != null && linkedUser.getPhone() != null) {
                phone = linkedUser.getPhone();
            }

            // Resolve country
            String country = "Singapore";
            if (data.containsKey("country") && data.get("country") != null && !data.get("country").toString().trim().isEmpty()) {
                country = data.get("country").toString().trim();
            } else if (data.containsKey("incorporationCountry") && data.get("incorporationCountry") != null && !data.get("incorporationCountry").toString().trim().isEmpty()) {
                country = data.get("incorporationCountry").toString().trim();
            } else if (data.containsKey("nationality") && data.get("nationality") != null && !data.get("nationality").toString().trim().isEmpty()) {
                country = data.get("nationality").toString().trim();
            }

            if (contactPerson == null || contactPerson.isEmpty()) contactPerson = name;
            if (email == null) email = "";
            if (phone == null) phone = "";

            Map<String, Object> payload = new HashMap<>();
            payload.put("name", name);
            payload.put("contactPerson", contactPerson);
            payload.put("email", email);
            payload.put("phone", phone);
            payload.put("country", country);

            // Execute sync runner
            Map<String, Object> syncResult = executeSyncRunner(payload);

            if (Boolean.TRUE.equals(syncResult.get("success"))) {
                // Update requirement with sync status
                data.put("complianceBoxSynced", true);
                data.put("complianceBoxSyncedAt", new Date().toString());
                data.put("complianceBoxClientName", name);
                data.put("complianceBoxContactPerson", contactPerson);
                req.setData(data);
                req.setUpdatedAt(new Date());
                requirementRepository.save(req);

                response.put("success", true);
                response.put("message", "Client successfully synced to ComplianceBox");
                response.put("clientName", name);
                response.put("contactPerson", contactPerson);
                response.put("email", email);
                response.put("phone", phone);
                response.put("country", country);
                response.put("syncedAt", new Date().toString());
            } else {
                response.put("success", false);
                response.put("error", syncResult.getOrDefault("error", "ComplianceBox sync failed"));
            }

        } catch (Exception e) {
            logger.error("Error syncing application to ComplianceBox: ", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return response;
    }

    public Map<String, Object> syncCustom(Map<String, Object> clientPayload) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (clientPayload == null || !clientPayload.containsKey("name") || clientPayload.get("name") == null) {
                response.put("success", false);
                response.put("error", "Client name is required");
                return response;
            }

            Map<String, Object> syncResult = executeSyncRunner(clientPayload);
            return syncResult;
        } catch (Exception e) {
            logger.error("Error in custom ComplianceBox sync: ", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }

    private Map<String, Object> executeSyncRunner(Map<String, Object> payload) throws Exception {
        String jsonPayload = objectMapper.writeValueAsString(payload);
        
        // Find path to script
        String userDir = System.getProperty("user.dir");
        File scriptFile = new File(userDir, "scripts/compliancebox_sync.js");
        if (!scriptFile.exists()) {
            File parentScript = new File(userDir, "../scripts/compliancebox_sync.js");
            if (parentScript.exists()) {
                scriptFile = parentScript;
            } else {
                scriptFile = new File("/Users/ramachandran/git/globalisor/scripts/compliancebox_sync.js");
            }
        }

        logger.info("Running ComplianceBox sync script at: {} with payload: {}", scriptFile.getAbsolutePath(), jsonPayload);

        ProcessBuilder pb = new ProcessBuilder("node", scriptFile.getAbsolutePath(), jsonPayload);
        pb.directory(scriptFile.getParentFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }

        int exitCode = process.waitFor();
        String resultStr = output.toString().trim();
        logger.info("ComplianceBox script output (exit {}): {}", exitCode, resultStr);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = objectMapper.readValue(resultStr, Map.class);
            return resultMap;
        } catch (Exception parseErr) {
            Map<String, Object> errMap = new HashMap<>();
            errMap.put("success", false);
            errMap.put("error", "Invalid script output: " + resultStr);
            return errMap;
        }
    }

    private Requirement findRequirementFlexible(String id) {
        if (id == null) return null;
        Optional<Requirement> r = requirementRepository.findById(id);
        if (r.isPresent()) return r.get();
        
        String cleanId = id.replace("APP-", "").trim();
        r = requirementRepository.findById(cleanId);
        if (r.isPresent()) return r.get();

        for (Requirement req : requirementRepository.findAll()) {
            if (req.getId() != null && (req.getId().equalsIgnoreCase(id) || req.getId().equalsIgnoreCase(cleanId))) {
                return req;
            }
            if (req.getData() != null) {
                Object appNum = req.getData().get("applicationNumber");
                if (appNum != null && (appNum.toString().equalsIgnoreCase(id) || appNum.toString().equalsIgnoreCase(cleanId))) {
                    return req;
                }
            }
        }
        return null;
    }
}
