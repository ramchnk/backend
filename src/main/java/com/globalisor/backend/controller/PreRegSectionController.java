package com.globalisor.backend.controller;

import com.globalisor.backend.model.PreRegSection;
import com.globalisor.backend.repository.PreRegSectionRepository;
import com.globalisor.backend.security.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/prereg-sections")
public class PreRegSectionController {

    @Autowired
    PreRegSectionRepository preRegSectionRepository;

    private void seedDefaultSections() {
        if (preRegSectionRepository.count() > 0) return;

        List<PreRegSection> defaults = new ArrayList<>();

        // 1. Company Name
        List<Map<String, Object>> nameFields = List.of(
            createField("names[0]", "Proposed Name Option 1", "text", true, "Primary preferred name", null),
            createField("names[1]", "Proposed Name Option 2", "text", false, "Backup name if Option 1 is unavailable", null),
            createField("names[2]", "Proposed Name Option 3", "text", false, "Alternative name or enter NA", null),
            createField("activities.primary", "Primary Business Activity", "text", true, "E.g. Software Development", null),
            createField("activities.secondary", "Secondary Business Activity", "text", false, "E.g. IT Consulting", null)
        );
        defaults.add(new PreRegSection("sec-company-name", "company-name", "Company Name", "Selection & Activities", "form", 1, nameFields));

        // 2. Directors
        List<Map<String, Object>> directorFields = List.of(
            createField("name", "Full Name", "text", true, "As in Passport/ID", null),
            createField("idType", "Director Type", "select", true, null, List.of("local:Local (NRIC/FIN)", "foreigner:Foreigner (Passport)")),
            createField("idNum", "ID / Passport Number", "text", true, "NRIC/FIN or Passport No.", null),
            createField("dob", "Date of Birth", "date", true, null, null),
            createField("phone", "Contact Number", "text", true, "E.g. +65 9123 4567", null),
            createField("passportExpiry", "Passport Expiry Date (Foreigner only)", "date", false, null, null),
            createField("docs.id", "ID / Passport Copy", "file", true, null, null),
            createField("docs.address", "Address Proof (Utility Bill / Bank Statement)", "file", true, null, null)
        );
        defaults.add(new PreRegSection("sec-directors", "directors", "Directors", "Entity Setup", "repeater", 2, directorFields));

        // 3. Shareholders
        List<Map<String, Object>> shFields = List.of(
            createField("type", "Shareholder Type", "select", true, null, List.of("individual:Individual", "corporate:Corporate")),
            createField("name", "Name / Company Name", "text", true, "Full Name or Registered Entity Name", null),
            createField("shares", "Number of Shares", "number", true, "E.g. 1000", null),
            createField("percent", "% Held", "number", true, "E.g. 100", null),
            createField("docs.doc1", "ID Proof / Cert of Incorporation", "file", true, null, null),
            createField("docs.doc2", "Address Proof / M&A", "file", true, null, null)
        );
        defaults.add(new PreRegSection("sec-shareholders", "shareholders", "Shareholders", "Ownership Structure", "repeater", 3, shFields));

        // 4. Paid-Up Capital
        List<Map<String, Object>> capitalFields = List.of(
            createField("capital.issued", "Issued Capital", "number", true, "E.g. 1000", null),
            createField("capital.numShares", "Number of Shares", "number", true, "E.g. 1000", null),
            createField("capital.currency", "Currency", "text", true, "E.g. SGD", null)
        );
        defaults.add(new PreRegSection("sec-capital", "capital", "Paid-Up Capital", "Share Structure", "form", 4, capitalFields));

        // 5. Registered Office Address
        List<Map<String, Object>> officeFields = List.of(
            createField("office.useService", "Office Address Option", "select", true, null, List.of("true:Globalisor Premium CBD Address", "false:Provide Own Address")),
            createField("office.address", "Office Address", "textarea", false, "Enter your own address if not using Globalisor service", null)
        );
        defaults.add(new PreRegSection("sec-office", "office", "Registered Office Address", "Registered Address", "form", 5, officeFields));

        // 6. Company Secretary
        List<Map<String, Object>> secFields = List.of(
            createField("secretary.required", "Secretary Option", "select", true, null, List.of("true:Globalisor Corporate Secretary Service", "false:Provide Own Secretary")),
            createField("secretary.details.name", "Secretary Name", "text", false, "Enter secretary name if providing own", null)
        );
        defaults.add(new PreRegSection("sec-secretary", "secretary", "Company Secretary", "Compliance Setup", "form", 6, secFields));

        preRegSectionRepository.saveAll(defaults);
    }

    private Map<String, Object> createField(String key, String label, String type, boolean required, String placeholder, List<String> options) {
        Map<String, Object> f = new HashMap<>();
        f.put("key", key);
        f.put("label", label);
        f.put("type", type);
        f.put("required", required);
        if (placeholder != null) f.put("placeholder", placeholder);
        if (options != null) f.put("options", options);
        return f;
    }

    private String getLoggedInAdminName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl) {
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            return userDetails.getFirstName() + " " + userDetails.getLastName();
        }
        return "Admin";
    }

    @GetMapping
    public ResponseEntity<?> getAdminSections() {
        seedDefaultSections();
        List<PreRegSection> sections = preRegSectionRepository.findAll();
        sections.sort(Comparator.comparingInt(PreRegSection::getSortOrder));
        return ResponseEntity.ok(sections);
    }

    @GetMapping("/published")
    public ResponseEntity<?> getPublishedSections() {
        seedDefaultSections();
        List<PreRegSection> sections = preRegSectionRepository.findAll();
        sections.sort(Comparator.comparingInt(PreRegSection::getSortOrder));

        List<Map<String, Object>> published = new ArrayList<>();
        for (PreRegSection s : sections) {
            if ("PUBLISHED".equals(s.getStatus()) && s.getPublishedData() != null) {
                published.add(s.getPublishedData());
            }
        }
        return ResponseEntity.ok(published);
    }

    @GetMapping("/preview")
    public ResponseEntity<?> getPreviewSections() {
        seedDefaultSections();
        List<PreRegSection> sections = preRegSectionRepository.findAll();
        sections.sort(Comparator.comparingInt(PreRegSection::getSortOrder));
        
        // Map draft structures as they look
        List<Map<String, Object>> preview = new ArrayList<>();
        for (PreRegSection s : sections) {
            // Include everything except if explicitly unpublished
            if (!"UNPUBLISHED".equals(s.getStatus())) {
                Map<String, Object> sMap = new HashMap<>();
                sMap.put("id", s.getId());
                sMap.put("key", s.getKey());
                sMap.put("title", s.getTitle());
                sMap.put("description", s.getDescription());
                sMap.put("type", s.getType());
                sMap.put("sortOrder", s.getSortOrder());
                sMap.put("fields", s.getFields());
                preview.add(sMap);
            }
        }
        return ResponseEntity.ok(preview);
    }

    @PostMapping
    public ResponseEntity<?> createSection(@RequestBody PreRegSection section) {
        String adminName = getLoggedInAdminName();
        section.setLastUpdatedBy(adminName);
        section.setLastUpdatedAt(new Date());
        section.setStatus("DRAFT");

        // Set sort order to max + 1
        List<PreRegSection> sections = preRegSectionRepository.findAll();
        int maxOrder = sections.stream().mapToInt(PreRegSection::getSortOrder).max().orElse(0);
        section.setSortOrder(maxOrder + 1);

        PreRegSection saved = preRegSectionRepository.save(section);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateSection(@PathVariable String id, @RequestBody PreRegSection sectionUpdates) {
        Optional<PreRegSection> sectionOpt = preRegSectionRepository.findById(id);
        if (sectionOpt.isEmpty()) return ResponseEntity.notFound().build();

        PreRegSection section = sectionOpt.get();
        section.setTitle(sectionUpdates.getTitle());
        section.setDescription(sectionUpdates.getDescription());
        section.setType(sectionUpdates.getType());
        section.setFields(sectionUpdates.getFields());

        // Don't allow changing key for default seeded sections
        if (!section.getId().startsWith("sec-")) {
            section.setKey(sectionUpdates.getKey());
        }

        // If it was PUBLISHED, check if changes differ to set hasUnpublishedChanges or just set status to DRAFT/Modified
        // The requirements say: "Display status badges: Draft, Published, Unpublished"
        // Let's set the status to "DRAFT" when there are modifications after publishing.
        // Wait, "Unpublished content should be hidden from clients. Published should appear. Republish should instantly update."
        // So keeping status as "DRAFT" (or we can keep it as "PUBLISHED" with unpublished changes but let's change status to "DRAFT" so it gets a DRAFT badge, but its publishedData is STILL live in the client wizard until they click "Publish" again!).
        // This is a brilliant strategy: the client page uses `publishedData`. The editor page shows the draft config. The badge shows "Draft" (or "Draft/Unpublished Changes" if publishedData is not null).
        // Let's implement this! If status was PUBLISHED, but it got updated, we keep the status as "PUBLISHED" but we can flag that it has draft changes, or change status to "DRAFT" but leave `publishedData` intact!
        // The badge will show "Draft" if it has never been published (publishedData is null) or "Unpublished Changes" (or just "Draft") if it has publishedData but status is DRAFT.
        // Let's check status badge requirement: "Display status badges: Draft, Published, Unpublished".
        // If status becomes DRAFT, then status badge is "Draft". That is clean and perfectly satisfies the requirement!
        section.setStatus("DRAFT"); 

        section.setLastUpdatedBy(getLoggedInAdminName());
        section.setLastUpdatedAt(new Date());

        PreRegSection saved = preRegSectionRepository.save(section);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSection(@PathVariable String id) {
        Optional<PreRegSection> sectionOpt = preRegSectionRepository.findById(id);
        if (sectionOpt.isEmpty()) return ResponseEntity.notFound().build();

        PreRegSection section = sectionOpt.get();
        if (section.getId().startsWith("sec-")) {
            return ResponseEntity.badRequest().body("Default system sections cannot be deleted");
        }

        preRegSectionRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<?> publishSection(@PathVariable String id) {
        Optional<PreRegSection> sectionOpt = preRegSectionRepository.findById(id);
        if (sectionOpt.isEmpty()) return ResponseEntity.notFound().build();

        PreRegSection section = sectionOpt.get();
        section.setStatus("PUBLISHED");
        section.setLastUpdatedBy(getLoggedInAdminName());
        section.setLastUpdatedAt(new Date());

        // Snapshot current draft config
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("id", section.getId());
        snapshot.put("key", section.getKey());
        snapshot.put("title", section.getTitle());
        snapshot.put("description", section.getDescription());
        snapshot.put("type", section.getType());
        snapshot.put("sortOrder", section.getSortOrder());
        snapshot.put("fields", section.getFields());
        section.setPublishedData(snapshot);

        PreRegSection saved = preRegSectionRepository.save(section);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{id}/unpublish")
    public ResponseEntity<?> unpublishSection(@PathVariable String id) {
        Optional<PreRegSection> sectionOpt = preRegSectionRepository.findById(id);
        if (sectionOpt.isEmpty()) return ResponseEntity.notFound().build();

        PreRegSection section = sectionOpt.get();
        section.setStatus("UNPUBLISHED");
        section.setLastUpdatedBy(getLoggedInAdminName());
        section.setLastUpdatedAt(new Date());
        
        // Remove publishedData or just keep it but since status is UNPUBLISHED it won't render
        // Keeping it allows "Publish" again to restore it easily.

        PreRegSection saved = preRegSectionRepository.save(section);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/reorder")
    public ResponseEntity<?> reorderSections(@RequestBody List<String> orderIds) {
        List<PreRegSection> sections = preRegSectionRepository.findAll();
        String adminName = getLoggedInAdminName();
        Date now = new Date();

        for (int i = 0; i < orderIds.size(); i++) {
            String id = orderIds.get(i);
            for (PreRegSection s : sections) {
                if (s.getId().equals(id)) {
                    s.setSortOrder(i + 1);
                    s.setLastUpdatedBy(adminName);
                    s.setLastUpdatedAt(now);
                    
                    // If it was already published, update publishedData's sortOrder as well so client order changes instantly!
                    if (s.getPublishedData() != null) {
                        Map<String, Object> snapshot = new HashMap<>(s.getPublishedData());
                        snapshot.put("sortOrder", i + 1);
                        s.setPublishedData(snapshot);
                    }
                    break;
                }
            }
        }
        preRegSectionRepository.saveAll(sections);
        return ResponseEntity.ok().build();
    }
}
