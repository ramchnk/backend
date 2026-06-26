package com.globalisor.backend.controller;

import com.globalisor.backend.model.OnboardingConfig;
import com.globalisor.backend.model.OnboardingConfig.FieldConfig;
import com.globalisor.backend.model.OnboardingConfig.DocumentConfig;
import com.globalisor.backend.model.OnboardingConfig.VersionLog;
import com.globalisor.backend.repository.OnboardingConfigRepository;
import com.globalisor.backend.security.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/onboarding-config")
public class OnboardingConfigController {

    @Autowired
    OnboardingConfigRepository repo;

    @jakarta.annotation.PostConstruct
    public void init() {
        try {
            repo.deleteAll();
            List<OnboardingConfig> defaults = buildDefaultSteps();
            for (OnboardingConfig step : defaults) {
                step.setCreatedBy("System Auto-Seed");
                step.setCreatedAt(new Date());
                step.setLastUpdatedBy("System Auto-Seed");
                step.setLastUpdatedAt(new Date());
                step.setHistory(new ArrayList<>());
                repo.save(step);
            }
        } catch (Exception e) {
            System.err.println("Failed to auto-seed onboarding configuration: " + e.getMessage());
        }
    }

    private String getAdminName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl u) {
            return u.getFirstName() + " " + u.getLastName();
        }
        return "Admin";
    }

    /** GET all steps (admin view — all statuses, ordered) */
    @GetMapping
    public ResponseEntity<List<OnboardingConfig>> getAll(
            @RequestParam(required = false, defaultValue = "false") boolean includeArchived) {
        List<OnboardingConfig> all = repo.findAllByOrderBySortOrderAsc();
        if (!includeArchived) {
            all.removeIf(s -> "ARCHIVED".equals(s.getStatus()));
        }
        return ResponseEntity.ok(all);
    }

    /** GET published steps only (client portal) */
    @GetMapping("/published")
    public ResponseEntity<List<OnboardingConfig>> getPublished() {
        return ResponseEntity.ok(repo.findByStatusOrderBySortOrderAsc("PUBLISHED"));
    }

    /** GET single step by ID */
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        return repo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** POST create new step */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody OnboardingConfig config) {
        config.setId("ob-" + System.currentTimeMillis());
        config.setCreatedBy(getAdminName());
        config.setCreatedAt(new Date());
        config.setLastUpdatedBy(getAdminName());
        config.setLastUpdatedAt(new Date());
        config.setVersion(1);
        if (config.getHistory() == null) config.setHistory(new ArrayList<>());
        if (config.getStatus() == null) config.setStatus("DRAFT");

        // Auto-assign sort order
        List<OnboardingConfig> all = repo.findAllByOrderBySortOrderAsc();
        config.setSortOrder(all.size());

        OnboardingConfig saved = repo.save(config);
        return ResponseEntity.ok(saved);
    }

    /** PUT update step */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody OnboardingConfig updates) {
        Optional<OnboardingConfig> opt = repo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        OnboardingConfig existing = opt.get();

        // Log version
        VersionLog log = new VersionLog();
        log.setVersion(existing.getVersion());
        log.setUpdatedBy(getAdminName());
        log.setUpdatedAt(existing.getLastUpdatedAt());
        log.setChangeNote(updates.getTitle() != null && !updates.getTitle().equals(existing.getTitle())
                ? "Title changed" : "Fields updated");
        if (existing.getHistory() == null) existing.setHistory(new ArrayList<>());
        existing.getHistory().add(0, log);
        // Keep last 20 versions
        if (existing.getHistory().size() > 20) {
            existing.setHistory(existing.getHistory().subList(0, 20));
        }

        // Apply updates
        if (updates.getTitle() != null) existing.setTitle(updates.getTitle());
        if (updates.getKey() != null) existing.setKey(updates.getKey());
        if (updates.getField() != null) existing.setField(updates.getField());
        if (updates.getIcon() != null) existing.setIcon(updates.getIcon());
        if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
        if (updates.getDeclaration() != null) existing.setDeclaration(updates.getDeclaration());
        if (updates.getHelpText() != null) existing.setHelpText(updates.getHelpText());
        if (updates.getBannerText() != null) existing.setBannerText(updates.getBannerText());
        if (updates.getManualFields() != null) existing.setManualFields(updates.getManualFields());
        if (updates.getRequiredDocs() != null) existing.setRequiredDocs(updates.getRequiredDocs());
        if (updates.getExtractedFields() != null) existing.setExtractedFields(updates.getExtractedFields());
        if (updates.getAutoChecks() != null) existing.setAutoChecks(updates.getAutoChecks());
        existing.setMandatory(updates.isMandatory());
        existing.setDynamicSection(updates.isDynamicSection());
        if (updates.getDynamicCountKey() != null) existing.setDynamicCountKey(updates.getDynamicCountKey());

        existing.setVersion(existing.getVersion() + 1);
        existing.setLastUpdatedBy(getAdminName());
        existing.setLastUpdatedAt(new Date());

        return ResponseEntity.ok(repo.save(existing));
    }

    /** POST publish */
    @PostMapping("/{id}/publish")
    public ResponseEntity<?> publish(@PathVariable String id) {
        Optional<OnboardingConfig> opt = repo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        OnboardingConfig s = opt.get();
        s.setStatus("PUBLISHED");
        s.setLastUpdatedBy(getAdminName());
        s.setLastUpdatedAt(new Date());
        return ResponseEntity.ok(repo.save(s));
    }

    /** POST unpublish */
    @PostMapping("/{id}/unpublish")
    public ResponseEntity<?> unpublish(@PathVariable String id) {
        Optional<OnboardingConfig> opt = repo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        OnboardingConfig s = opt.get();
        s.setStatus("UNPUBLISHED");
        s.setLastUpdatedBy(getAdminName());
        s.setLastUpdatedAt(new Date());
        return ResponseEntity.ok(repo.save(s));
    }

    /** POST archive (soft delete) */
    @PostMapping("/{id}/archive")
    public ResponseEntity<?> archive(@PathVariable String id) {
        Optional<OnboardingConfig> opt = repo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        OnboardingConfig s = opt.get();
        s.setStatus("ARCHIVED");
        s.setLastUpdatedBy(getAdminName());
        s.setLastUpdatedAt(new Date());
        return ResponseEntity.ok(repo.save(s));
    }

    /** POST restore from archive */
    @PostMapping("/{id}/restore")
    public ResponseEntity<?> restore(@PathVariable String id) {
        Optional<OnboardingConfig> opt = repo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        OnboardingConfig s = opt.get();
        s.setStatus("DRAFT");
        s.setLastUpdatedBy(getAdminName());
        s.setLastUpdatedAt(new Date());
        return ResponseEntity.ok(repo.save(s));
    }

    /** DELETE hard delete */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }

    /** POST duplicate a step */
    @PostMapping("/{id}/duplicate")
    public ResponseEntity<?> duplicate(@PathVariable String id) {
        Optional<OnboardingConfig> opt = repo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        OnboardingConfig original = opt.get();

        OnboardingConfig copy = new OnboardingConfig();
        copy.setId("ob-" + System.currentTimeMillis());
        copy.setKey(original.getKey() + "_copy");
        copy.setField(original.getField() + "Copy");
        copy.setTitle(original.getTitle() + " (Copy)");
        copy.setIcon(original.getIcon());
        copy.setDescription(original.getDescription());
        copy.setDeclaration(original.getDeclaration());
        copy.setHelpText(original.getHelpText());
        copy.setBannerText(original.getBannerText());
        copy.setManualFields(original.getManualFields());
        copy.setRequiredDocs(original.getRequiredDocs());
        copy.setExtractedFields(original.getExtractedFields());
        copy.setAutoChecks(original.getAutoChecks());
        copy.setDynamicSection(original.isDynamicSection());
        copy.setDynamicCountKey(original.getDynamicCountKey());
        copy.setStatus("DRAFT");
        copy.setVersion(1);
        copy.setHistory(new ArrayList<>());
        copy.setSortOrder(repo.findAllByOrderBySortOrderAsc().size());
        copy.setCreatedBy(getAdminName());
        copy.setCreatedAt(new Date());
        copy.setLastUpdatedBy(getAdminName());
        copy.setLastUpdatedAt(new Date());

        return ResponseEntity.ok(repo.save(copy));
    }

    /** POST reorder — accepts list of IDs in new order */
    @PostMapping("/reorder")
    public ResponseEntity<?> reorder(@RequestBody List<String> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            final int idx = i;
            repo.findById(orderedIds.get(i)).ifPresent(s -> {
                s.setSortOrder(idx);
                s.setLastUpdatedBy(getAdminName());
                s.setLastUpdatedAt(new Date());
                repo.save(s);
            });
        }
        return ResponseEntity.ok(Map.of("reordered", orderedIds.size()));
    }

    /** POST seed the 7 default onboarding steps (idempotent) */
    @PostMapping("/import-defaults")
    public ResponseEntity<?> importDefaults() {
        List<OnboardingConfig> defaults = buildDefaultSteps();
        int added = 0;
        for (OnboardingConfig step : defaults) {
            if (!repo.existsById(step.getId())) {
                step.setCreatedBy("System");
                step.setCreatedAt(new Date());
                step.setLastUpdatedBy("System");
                step.setLastUpdatedAt(new Date());
                step.setHistory(new ArrayList<>());
                repo.save(step);
                added++;
            }
        }
        return ResponseEntity.ok(Map.of("added", added, "skipped", defaults.size() - added));
    }

    // ─── Build the 7 canonical default steps ───────────────────────────────────
    private List<OnboardingConfig> buildDefaultSteps() {
        List<OnboardingConfig> steps = new ArrayList<>();

        // Step 0 — Document Checklist
        OnboardingConfig sChecklist = new OnboardingConfig();
        sChecklist.setId("ob-default-0");
        sChecklist.setKey("document_checklist");
        sChecklist.setField("stepDocumentChecklist");
        sChecklist.setTitle("Document Checklist");
        sChecklist.setIcon("clipboard-list");
        sChecklist.setDescription("Please review the document checklist based on pre-registration selections before starting onboarding.");
        sChecklist.setSortOrder(0);
        sChecklist.setStatus("PUBLISHED");
        sChecklist.setVersion(1);
        sChecklist.setManualFields(new ArrayList<>());
        sChecklist.setRequiredDocs(new ArrayList<>());
        steps.add(sChecklist);

        // Step 1 — Director Details
        OnboardingConfig s2 = new OnboardingConfig();
        s2.setId("ob-default-2");
        s2.setKey("director_details");
        s2.setField("step2DirectorDetails");
        s2.setTitle("Director Details");
        s2.setIcon("briefcase");
        s2.setDescription("Please upload NRIC/FIN and Address Proof, verify and confirm details.");
        s2.setDeclaration("");
        s2.setSortOrder(1);
        s2.setStatus("PUBLISHED");
        s2.setVersion(1);
        s2.setDynamicSection(true);
        s2.setDynamicCountKey("directorCount");
        s2.setManualFields(List.of(
                field("fullName","Full Legal Name","text",true,false,null,null,0),
                field("idNumber","NRIC / FIN","text",true,false,null,null,1),
                field("nationality","Nationality","nationality",true,false,null,null,2),
                field("gender","Gender","select",true,false,null,List.of("Select","Male","Female","Other"),3),
                field("dateOfBirth","Date of Birth","date",true,false,null,null,4),
                field("residentialAddress","Residential Address","text",true,false,null,null,5),
                field("email","Email","email",true,false,null,null,6),
                field("mobile","Mobile Number","phone",true,false,null,null,7),
                field("disqualificationAcknowledge","I confirm that I am not disqualified from acting as a director under the laws of Singapore.","checkbox",true,false,null,null,8)
        ));
        s2.setRequiredDocs(List.of(
                doc("nric","NRIC / FIN",true,true,List.of("fullName","idNumber","nationality","gender","dateOfBirth")),
                doc("address_proof","Utility Bill / Bank Statement / Mobile Bill",true,true,List.of("residentialAddress"))
        ));
        steps.add(s2);

        // Step 2 — Share Capital Details
        OnboardingConfig sShare = new OnboardingConfig();
        sShare.setId("ob-default-share-capital");
        sShare.setKey("share_capital");
        sShare.setField("stepShareCapital");
        sShare.setTitle("Share Capital Details");
        sShare.setIcon("coins");
        sShare.setDescription("Configure corporate share capital structure and allocate shares to shareholders.");
        sShare.setSortOrder(2);
        sShare.setStatus("PUBLISHED");
        sShare.setVersion(1);
        sShare.setManualFields(new ArrayList<>());
        sShare.setRequiredDocs(new ArrayList<>());
        steps.add(sShare);

        // Step 3 — Individual Shareholder
        OnboardingConfig s3 = new OnboardingConfig();
        s3.setId("ob-default-3");
        s3.setKey("individual_shareholder");
        s3.setField("step3IndividualShareholder");
        s3.setTitle("Individual Shareholder Details");
        s3.setIcon("users");
        s3.setDescription("Capture individual shareholder information. Ownership ≥ 25% will automatically trigger UBO and AML/KYC screening.");
        s3.setSortOrder(3);
        s3.setStatus("PUBLISHED");
        s3.setVersion(1);
        s3.setDynamicSection(true);
        s3.setDynamicCountKey("individualShareholderCount");
        s3.setManualFields(List.of(
                field("sameAsDirector","Is individual shareholder same as director?","checkbox",false,false,null,null,0),
                field("fullName","Full Name","text",true,false,null,null,1),
                field("idNumber","NRIC / FIN","text",true,false,null,null,2),
                field("nationality","Nationality","nationality",true,false,null,null,3),
                field("dateOfBirth","Date of Birth","date",true,false,null,null,4),
                field("residentialAddress","Residential Address","text",true,false,null,null,5),
                field("email","Email","email",true,false,null,null,6),
                field("mobile","Mobile Number","phone",true,false,null,null,7),
                field("totalShares","Total Number of Shares of the Company","number",true,false,null,null,8),
                field("totalShareCapital","Total Share Capital Amount of the Company","number",true,false,null,null,9),
                field("currency","Currency","select",true,false,null,List.of("Select","SGD","USD"),10),
                field("shareClass","Share Class","select",true,false,null,List.of("Select","Ordinary","Preference"),11),
                field("numberOfShares","Number of Shares","number",true,false,null,null,12),
                field("shareCapitalAmount","Share Capital Amount","number",true,false,null,null,13),
                field("ownershipPercentage","Ownership % (auto-calculated)","number",false,true,null,null,14),
                field("uboDeclaration","Is the Shareholder the Ultimate Beneficial Owner?","select",true,false,null,List.of("Select","No","Yes"),15)
        ));
        s3.setRequiredDocs(List.of(
                doc("nric","NRIC / FIN",true,true,List.of("fullName","idNumber")),
                doc("address_proof","Address Proof (Utility Bill / Bank Statement / Mobile Bill)",true,true,List.of("residentialAddress"))
        ));
        steps.add(s3);

        // Step 4 — Corporate Shareholder
        OnboardingConfig s4 = new OnboardingConfig();
        s4.setId("ob-default-4");
        s4.setKey("corporate_shareholder");
        s4.setField("step4CorporateShareholder");
        s4.setTitle("Corporate Shareholder Details");
        s4.setIcon("building-2");
        s4.setDescription("Upload Bizfile and constitution for OCR extraction of company details.");
        s4.setSortOrder(4);
        s4.setStatus("PUBLISHED");
        s4.setVersion(1);
        s4.setDynamicSection(true);
        s4.setDynamicCountKey("corporateShareholderCount");
        s4.setExtractedFields(List.of("companyName","uen","dateOfIncorporation","registeredAddress"));
        s4.setManualFields(List.of(
                field("companyName","Company Name","text",true,false,null,null,0),
                field("uen","UEN / Reg Number","text",true,false,null,null,1),
                field("dateOfIncorporation","Date of Incorporation","date",true,false,null,null,2),
                field("registeredAddress","Registered Address","text",true,false,null,null,3),
                field("totalShares","Total Number of Shares of the Company","number",true,false,null,null,4),
                field("totalShareCapital","Total Share Capital Amount of the Company","number",true,false,null,null,5),
                field("currency","Currency","select",true,false,null,List.of("SGD","USD"),6),
                field("shareClass","Share Class","select",true,false,null,List.of("Select","Ordinary","Preference"),7),
                field("numberOfShares","Number of Shares","number",true,false,null,null,8),
                field("shareCapitalAmount","Share Capital Amount","number",true,false,null,null,9),
                field("ownershipPercentage","Ownership % (auto-calculated)","number",false,true,null,null,10),
                field("uboDeclaration","Is the Shareholder the Ultimate Beneficial Owner?","select",true,false,null,List.of("No","Yes"),11)
        ));
        s4.setRequiredDocs(List.of(
                doc("bizfile","Bizfile (ACRA)",true,true,List.of("companyName","uen","dateOfIncorporation","registeredAddress")),
                doc("constitution","Constitution / M&AA",true,false,null),
                doc("cert_incorporation","Certificate of Incorporation (non-SG entities)",false,false,null),
                doc("supporting_docs","Supporting Corporate Documents",false,false,null)
        ));
        steps.add(s4);

        // Step 5 — Corporate Representative
        OnboardingConfig s6 = new OnboardingConfig();
        s6.setId("ob-default-6");
        s6.setKey("corporate_rep");
        s6.setField("step6CorporateRep");
        s6.setTitle("Corporate Representative");
        s6.setIcon("user-cog");
        s6.setDescription("Upload NRIC/FIN and address proof for OCR extraction. Confirm contact details.");
        s6.setSortOrder(5);
        s6.setStatus("PUBLISHED");
        s6.setVersion(1);
        s6.setExtractedFields(List.of("fullName","idNumber","nationality","dateOfBirth"));
        s6.setManualFields(List.of(
                field("fullName","Full Legal Name","text",true,false,null,null,0),
                field("idNumber","NRIC / FIN","text",true,false,null,null,1),
                field("nationality","Nationality","nationality",true,false,null,null,2),
                field("dateOfBirth","Date of Birth","date",true,false,null,null,3),
                field("residentialAddress","Residential Address","text",true,false,null,null,4),
                field("email","Email Address","email",true,false,null,null,5),
                field("mobile","Mobile Number","phone",true,false,null,null,6)
        ));
        s6.setRequiredDocs(List.of(
                doc("nric","NRIC / FIN",true,true,List.of("fullName","idNumber","nationality","dateOfBirth")),
                doc("address_proof","Address Proof",true,false,null),
                doc("auth_document","Proof of Authorization (Board Resolution / Letter of Authorization)",false,false,null)
        ));
        steps.add(s6);

        // Step 6 — Final Declaration
        OnboardingConfig s7 = new OnboardingConfig();
        s7.setId("ob-default-7");
        s7.setKey("final_declaration");
        s7.setField("step7FinalDeclaration");
        s7.setTitle("Final Declaration & Consent");
        s7.setIcon("file-signature");
        s7.setDescription("Please review all details and declare final consent before submitting your application.");
        s7.setDeclaration("");
        s7.setSortOrder(6);
        s7.setStatus("PUBLISHED");
        s7.setVersion(1);
        s7.setManualFields(List.of(
                field("declarationAgreed","I confirm that all the details provided are true and accurate to the best of my knowledge.","checkbox",true,false,null,null,0),
                field("consentAgreed","I consent to Globalisor conducting compliance, AML/KYC screening, and verification checks.","checkbox",true,false,null,null,1),
                field("fye","Financial Year End (FYE)","select",true,false,null,List.of("Select","31 Dec","31 Mar","30 Jun","30 Sep"),2)
        ));
        s7.setRequiredDocs(new ArrayList<>());
        steps.add(s7);

        return steps;
    }

    private FieldConfig field(String key, String label, String type, boolean mandatory, boolean readonly,
                              String placeholder, List<String> options, int sortOrder) {
        return field(key, label, type, mandatory, readonly, placeholder, options, sortOrder, null, null);
    }

    private FieldConfig field(String key, String label, String type, boolean mandatory, boolean readonly,
                              String placeholder, List<String> options, int sortOrder, String conditionalOn, String conditionalValue) {
        FieldConfig f = new FieldConfig();
        f.setKey(key);
        f.setLabel(label);
        f.setType(type);
        f.setMandatory(mandatory);
        f.setReadonly(readonly);
        f.setPlaceholder(placeholder);
        f.setOptions(options);
        f.setSortOrder(sortOrder);
        f.setConditionalOn(conditionalOn);
        f.setConditionalValue(conditionalValue);
        return f;
    }

    private DocumentConfig doc(String type, String label, boolean required, boolean ocrEnabled,
                                List<String> ocrMappedFields) {
        return doc(type, label, required, ocrEnabled, ocrMappedFields, null, null);
    }

    private DocumentConfig doc(String type, String label, boolean required, boolean ocrEnabled,
                                List<String> ocrMappedFields, String conditionalOn, String conditionalValue) {
        DocumentConfig d = new DocumentConfig();
        d.setType(type);
        d.setLabel(label);
        d.setRequired(required);
        d.setOcrEnabled(ocrEnabled);
        d.setOcrMappedFields(ocrMappedFields);
        d.setConditionalOn(conditionalOn);
        d.setConditionalValue(conditionalValue);
        return d;
    }
}
