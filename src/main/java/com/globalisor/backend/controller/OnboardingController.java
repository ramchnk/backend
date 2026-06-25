package com.globalisor.backend.controller;

import com.globalisor.backend.model.Onboarding;
import com.globalisor.backend.model.OnboardingConfig;
import com.globalisor.backend.model.User;
import com.globalisor.backend.repository.OnboardingRepository;
import com.globalisor.backend.repository.OnboardingConfigRepository;
import com.globalisor.backend.repository.UserRepository;
import com.globalisor.backend.service.NotificationService;
import com.globalisor.backend.websocket.ChatWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

    @Autowired OnboardingRepository onboardingRepository;
    @Autowired OnboardingConfigRepository onboardingConfigRepository;
    @Autowired UserRepository userRepository;
    @Autowired NotificationService notificationService;
    @Autowired ChatWebSocketHandler chatWebSocketHandler;

    // GET all onboardings (admin)
    @GetMapping
    public ResponseEntity<List<Onboarding>> getAllOnboardings() {
        return ResponseEntity.ok(onboardingRepository.findAllByOrderByCreatedAtDesc());
    }

    // GET onboarding by client id
    @GetMapping("/client/{clientId}")
    public ResponseEntity<?> getByClientId(@PathVariable String clientId) {
        Optional<Onboarding> opt = onboardingRepository.findByClientId(clientId);
        if (opt.isPresent()) return ResponseEntity.ok(opt.get());
        // Return empty shell if not yet created
        Map<String, Object> empty = new HashMap<>();
        empty.put("exists", false);
        empty.put("portalActivated", false);
        empty.put("status", "not_started");
        return ResponseEntity.ok(empty);
    }

    // GET portal activation status
    @GetMapping("/client/{clientId}/status")
    public ResponseEntity<?> getPortalStatus(@PathVariable String clientId) {
        Optional<Onboarding> opt = onboardingRepository.findByClientId(clientId);
        Map<String, Object> result = new HashMap<>();
        if (opt.isPresent()) {
            result.put("portalActivated", opt.get().isPortalActivated());
            result.put("status", opt.get().getStatus());
            result.put("progressPercent", opt.get().getProgressPercent());
            result.put("onboardingId", opt.get().getId());
        } else {
            result.put("portalActivated", false);
            result.put("status", "not_started");
            result.put("progressPercent", 0);
        }
        return ResponseEntity.ok(result);
    }

    // POST create or update onboarding record
    @PostMapping("/client/{clientId}")
    public ResponseEntity<Onboarding> createOrUpdate(@PathVariable String clientId,
                                                      @RequestBody Map<String, Object> body) {
        Optional<Onboarding> opt = onboardingRepository.findByClientId(clientId);
        Onboarding ob = opt.orElseGet(() -> {
            Onboarding n = new Onboarding();
            n.setClientId(clientId);
            n.setStatus("in_progress");
            // Try to get name/email from user
            userRepository.findById(clientId).ifPresent(u -> {
                n.setClientEmail(u.getEmail());
                n.setClientName(u.getFirstName() + " " + u.getLastName());
            });
            n.getAuditLogs().add("Onboarding initiated at " + new Date());
            return n;
        });
        ob.setUpdatedAt(System.currentTimeMillis());
        if (body.containsKey("clientEmail")) ob.setClientEmail((String) body.get("clientEmail"));
        if (body.containsKey("clientName")) ob.setClientName((String) body.get("clientName"));
        Onboarding saved = onboardingRepository.save(ob);
        return ResponseEntity.ok(saved);
    }

    // PATCH update a specific step's data + status
    @PatchMapping("/{id}/step/{stepKey}")
    public ResponseEntity<?> updateStep(@PathVariable String id,
                                        @PathVariable String stepKey,
                                        @RequestBody Map<String, Object> body) {
        Optional<Onboarding> opt = onboardingRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Onboarding ob = opt.get();

        Onboarding.OnboardingStep step = getStep(ob, stepKey);
        if (step == null) return ResponseEntity.badRequest().body("Unknown step: " + stepKey);

        // Merge data
        if (body.containsKey("data")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> newData = (Map<String, Object>) body.get("data");
            step.getData().putAll(newData);
        }
        if (body.containsKey("status")) {
            String oldStatus = step.getStatus();
            String newStatus = (String) body.get("status");
            if ("submitted".equals(newStatus) || "under_review".equals(newStatus)) {
                newStatus = "approved";
            }
            step.setStatus(newStatus);
            step.getAuditLogs().add("Status changed from " + oldStatus + " to " + newStatus + " at " + new Date());
            ob.getAuditLogs().add("Step '" + step.getTitle() + "' status → " + newStatus);
        }
        if (body.containsKey("documents")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> docs = (List<Map<String, Object>>) body.get("documents");
            docs.forEach(d -> {
                Onboarding.DocumentUpload doc = new Onboarding.DocumentUpload();
                doc.setId("DOC-" + System.currentTimeMillis() + "-" + (int)(Math.random()*1000));
                doc.setType((String) d.getOrDefault("type", "other"));
                doc.setLabel((String) d.getOrDefault("label", "Document"));
                doc.setFileName((String) d.getOrDefault("fileName", ""));
                doc.setFileData((String) d.getOrDefault("fileData", ""));
                doc.setMimeType((String) d.getOrDefault("mimeType", "application/octet-stream"));
                doc.setStatus("pending");
                // Simulate OCR extraction for NRIC
                if (doc.getType().startsWith("nric") || doc.getType().startsWith("fin")) {
                    Map<String, Object> extracted = new HashMap<>();
                    extracted.put("fullName", d.getOrDefault("fullName", "Extracted Name"));
                    extracted.put("idNumber", d.getOrDefault("idNumber", "S1234567A"));
                    extracted.put("nationality", d.getOrDefault("nationality", "Singaporean"));
                    extracted.put("gender", d.getOrDefault("gender", "Male"));
                    extracted.put("dateOfBirth", d.getOrDefault("dateOfBirth", "01/01/1990"));
                    doc.setExtractedData(extracted);
                }
                step.getDocuments().add(doc);
            });
        }

        // Recalculate progress
        ob.setProgressPercent(calculateProgress(ob));
        ob.setUpdatedAt(System.currentTimeMillis());
        updateOverallStatus(ob);

        Onboarding saved = onboardingRepository.save(ob);

        // Notify admin/staff on submission
        if ("submitted".equals(body.get("status"))) {
            try {
                notificationService.sendNotification("admin",
                        "Onboarding Step Submitted",
                        ob.getClientName() + " submitted: " + step.getTitle(),
                        "onboarding", saved.getId(), "Info");
            } catch (Exception ignored) {}
        }

        // Broadcast WS
        Map<String, Object> event = new HashMap<>();
        event.put("type", "onboarding_update");
        event.put("clientId", ob.getClientId());
        event.put("stepKey", stepKey);
        chatWebSocketHandler.broadcastEvent(event);

        return ResponseEntity.ok(saved);
    }

    // PATCH admin review a step (approve/reject)
    @PatchMapping("/{id}/step/{stepKey}/review")
    public ResponseEntity<?> reviewStep(@PathVariable String id,
                                        @PathVariable String stepKey,
                                        @RequestBody Map<String, Object> body) {
        Optional<Onboarding> opt = onboardingRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Onboarding ob = opt.get();
        Onboarding.OnboardingStep step = getStep(ob, stepKey);
        if (step == null) return ResponseEntity.badRequest().body("Unknown step");

        String newStatus = (String) body.getOrDefault("status", "under_review");
        String reviewer = (String) body.getOrDefault("reviewedBy", "Admin");
        String notes = (String) body.getOrDefault("notes", "");

        step.setStatus(newStatus);
        step.setReviewedBy(reviewer);
        step.setReviewedAt(System.currentTimeMillis());
        step.setReviewNotes(notes);
        step.getAuditLogs().add("Reviewed by " + reviewer + ": " + newStatus + " — " + notes + " at " + new Date());
        ob.getAuditLogs().add("Step '" + step.getTitle() + "' reviewed by " + reviewer + " → " + newStatus);

        ob.setProgressPercent(calculateProgress(ob));
        ob.setUpdatedAt(System.currentTimeMillis());
        updateOverallStatus(ob);

        Onboarding saved = onboardingRepository.save(ob);

        // Notify client
        try {
            String clientMsg = "approved".equals(newStatus)
                    ? "Your " + step.getTitle() + " has been approved."
                    : "Your " + step.getTitle() + " requires attention: " + notes;
            notificationService.sendNotification(ob.getClientId(),
                    "Onboarding Update: " + step.getTitle(),
                    clientMsg, "onboarding", saved.getId(), "Info");
        } catch (Exception ignored) {}

        // WS broadcast
        Map<String, Object> event = new HashMap<>();
        event.put("type", "onboarding_review");
        event.put("clientId", ob.getClientId());
        event.put("stepKey", stepKey);
        event.put("status", newStatus);
        chatWebSocketHandler.broadcastEvent(event);

        return ResponseEntity.ok(saved);
    }

    // POST activate portal (admin only)
    @PostMapping("/{id}/activate")
    public ResponseEntity<?> activatePortal(@PathVariable String id,
                                            @RequestBody Map<String, Object> body) {
        Optional<Onboarding> opt = onboardingRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Onboarding ob = opt.get();

        ob.setPortalActivated(true);
        ob.setActivatedAt(System.currentTimeMillis());
        ob.setActivatedBy((String) body.getOrDefault("activatedBy", "Admin"));
        ob.setStatus("approved");
        ob.setProgressPercent(100);
        ob.getAuditLogs().add("Portal activated by " + ob.getActivatedBy() + " at " + new Date());

        Onboarding saved = onboardingRepository.save(ob);

        // Notify client
        try {
            notificationService.sendNotification(ob.getClientId(),
                    "🎉 Your Client Portal is Now Active!",
                    "Congratulations! Your onboarding is complete. All portal sections are now available.",
                    "onboarding", saved.getId(), "Info");
        } catch (Exception ignored) {}

        // WS broadcast
        Map<String, Object> event = new HashMap<>();
        event.put("type", "portal_activated");
        event.put("clientId", ob.getClientId());
        chatWebSocketHandler.broadcastEvent(event);

        return ResponseEntity.ok(Map.of("success", true, "message", "Portal activated successfully"));
    }

    // POST simulate OCR extraction
    @PostMapping("/ocr-extract")
    public ResponseEntity<?> ocrExtract(@RequestBody Map<String, Object> body) {
        String docType = (String) body.getOrDefault("type", "nric");
        Map<String, Object> extracted = new HashMap<>();
        // Simulate OCR results based on document type
        if ("nric".equals(docType) || "fin".equals(docType)) {
            extracted.put("fullName", "ASHWIN KALYAN PRAKASH PURI");
            extracted.put("idNumber", "S7888130E");
            extracted.put("nationality", "INDIAN");
            extracted.put("gender", "Male");
            extracted.put("dateOfBirth", "1990-06-15");
            extracted.put("residentialAddress", "245 ORCHARD BOULEVARD, #21-01, ORCHARD BEL AIR, SINGAPORE 248648");
            extracted.put("email", "ashwin.puri@graas.ai");
            extracted.put("mobile", "+65 9123 4567");
        } else if ("bizfile".equals(docType)) {
            extracted.put("companyName", "GRAAS PTE. LTD.");
            extracted.put("uen", "201538449N");
            extracted.put("dateOfIncorporation", "2015-10-22");
            extracted.put("registeredAddress", "8 CRAIG ROAD, #02-01, SINGAPORE 089668");
            extracted.put("principalActivity", "DEVELOPMENT OF SOFTWARE AND APPLICATIONS (EXCEPT GAMES AND CYBERSECURITY) (62011)");
            extracted.put("countryOfIncorporation", "Singapore");
            extracted.put("companyType", "PRIVATE COMPANY LIMITED BY SHARES");
            extracted.put("companyStatus", "LIVE COMPANY");
            extracted.put("formerName", "SELLINALL PTE. LTD.");
            extracted.put("dateOfChangeOfName", "2023-03-07");
            extracted.put("secondaryActivity", "WHOLESALE OF COMPUTER SOFTWARE (EXCEPT GAMES AND CYBERSECURITY SOFTWARE) (46512)");
            extracted.put("auditFirm", "GRANT THORNTON AUDIT LLP");
            extracted.put("numberOfShares", 1998815);
            extracted.put("shareCapitalAmount", 8297985.82);
            extracted.put("totalShares", 1998815);
            extracted.put("totalShareCapital", 8297985.82);
            extracted.put("fye", "31 DEC");
            extracted.put("currency", "SGD");
        } else if ("ubo_nric".equals(docType)) {
            extracted.put("uboName", "MOHD ASIF");
            extracted.put("uboIdNumber", "S8811223F");
        } else if ("ubo_address_proof".equals(docType)) {
            extracted.put("uboAddress", "12 MARINA BOULEVARD, #30-02, MBFC TOWER 3, SINGAPORE 018982");
            extracted.put("email", "client.representative@graas.ai");
            extracted.put("mobile", "+65 8765 4321");
        }
        extracted.put("confidence", 0.94);
        extracted.put("extractedAt", System.currentTimeMillis());
        return ResponseEntity.ok(extracted);
    }

    // Helper: get step by key
    private Onboarding.OnboardingStep getStep(Onboarding ob, String key) {
        Onboarding.OnboardingStep step = switch (key) {
            case "individual_verification" -> ob.getStep1IndividualVerification();
            case "director_details" -> ob.getStep2DirectorDetails();
            case "individual_shareholder" -> ob.getStep3IndividualShareholder();
            case "corporate_shareholder" -> ob.getStep4CorporateShareholder();
            case "ubo" -> ob.getStep5UBO();
            case "corporate_rep" -> ob.getStep6CorporateRep();
            case "final_declaration" -> ob.getStep7FinalDeclaration();
            default -> null;
        };
        if (step != null) {
            return step;
        }
        if (ob.getDynamicSteps() == null) {
            ob.setDynamicSteps(new HashMap<>());
        }
        if (!ob.getDynamicSteps().containsKey(key)) {
            Onboarding.OnboardingStep newStep = new Onboarding.OnboardingStep(key, key);
            ob.getDynamicSteps().put(key, newStep);
        }
        return ob.getDynamicSteps().get(key);
    }

    // Helper: calculate progress
    private int calculateProgress(Onboarding ob) {
        List<OnboardingConfig> publishedSteps = onboardingConfigRepository.findByStatusOrderBySortOrderAsc("PUBLISHED");
        if (publishedSteps.isEmpty()) {
            List<String> statuses = List.of(
                    ob.getStep1IndividualVerification().getStatus(),
                    ob.getStep2DirectorDetails().getStatus(),
                    ob.getStep3IndividualShareholder().getStatus(),
                    ob.getStep4CorporateShareholder().getStatus(),
                    ob.getStep5UBO().getStatus(),
                    ob.getStep6CorporateRep().getStatus(),
                    ob.getStep7FinalDeclaration().getStatus()
            );
            long approved = statuses.stream().filter("approved"::equals).count();
            long submitted = statuses.stream().filter(s -> s.equals("submitted") || s.equals("under_review")).count();
            return (int) (((approved * 100) + (submitted * 50)) / 7);
        }

        long approved = 0;
        long submitted = 0;
        for (OnboardingConfig stepConfig : publishedSteps) {
            Onboarding.OnboardingStep step = getStep(ob, stepConfig.getKey());
            String status = step.getStatus();
            if ("approved".equals(status)) {
                approved++;
            } else if ("submitted".equals(status) || "under_review".equals(status)) {
                submitted++;
            }
        }
        return (int) (((approved * 100) + (submitted * 50)) / publishedSteps.size());
    }

    private void updateOverallStatus(Onboarding ob) {
        List<OnboardingConfig> publishedSteps = onboardingConfigRepository.findByStatusOrderBySortOrderAsc("PUBLISHED");
        List<String> statuses;
        if (publishedSteps.isEmpty()) {
            statuses = List.of(
                    ob.getStep1IndividualVerification().getStatus(),
                    ob.getStep2DirectorDetails().getStatus(),
                    ob.getStep3IndividualShareholder().getStatus(),
                    ob.getStep4CorporateShareholder().getStatus(),
                    ob.getStep5UBO().getStatus(),
                    ob.getStep6CorporateRep().getStatus(),
                    ob.getStep7FinalDeclaration().getStatus()
            );
        } else {
            statuses = new ArrayList<>();
            for (OnboardingConfig stepConfig : publishedSteps) {
                statuses.add(getStep(ob, stepConfig.getKey()).getStatus());
            }
        }

        boolean allApproved = statuses.stream().allMatch("approved"::equals);
        boolean anyRejected = statuses.stream().anyMatch(s -> "rejected".equals(s) || "additional_info_required".equals(s));
        boolean allCompletedOrApproved = statuses.stream().allMatch(s -> 
            "submitted".equals(s) || "under_review".equals(s) || "approved".equals(s)
        );

        if (ob.isPortalActivated() || allApproved) {
            ob.setStatus("approved");
            ob.setPortalActivated(true);
            if (ob.getActivatedAt() == null) {
                ob.setActivatedAt(System.currentTimeMillis());
                ob.setActivatedBy("System Auto-Approval");
            }
        } else if (anyRejected) {
            ob.setStatus("rejected");
        } else if (allCompletedOrApproved) {
            ob.setStatus("submitted");
        } else {
            ob.setStatus("in_progress");
        }
    }
}
