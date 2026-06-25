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
            if (repo.count() == 0) {
                List<OnboardingConfig> defaults = buildDefaultSteps();
                for (OnboardingConfig step : defaults) {
                    step.setCreatedBy("System Auto-Seed");
                    step.setCreatedAt(new Date());
                    step.setLastUpdatedBy("System Auto-Seed");
                    step.setLastUpdatedAt(new Date());
                    step.setHistory(new ArrayList<>());
                    repo.save(step);
                }
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

        // Step 1 — Verification Details
        OnboardingConfig s1 = new OnboardingConfig();
        s1.setId("ob-default-1");
        s1.setKey("individual_verification");
        s1.setField("step1IndividualVerification");
        s1.setTitle("Verification Details");
        s1.setIcon("user-check");
        s1.setDescription("Select your shareholder type to start. Upload required documents and verify details.");
        s1.setDeclaration("I confirm that I am not disqualified from acting as a director under the laws of Singapore.");
        s1.setSortOrder(0);
        s1.setStatus("PUBLISHED");
        s1.setVersion(1);
        s1.setExtractedFields(List.of("fullName","idNumber","nationality","gender","dateOfBirth",
                "companyName","uen","dateOfIncorporation","registeredAddress","uboName","uboIdNumber","uboAddress"));
        s1.setManualFields(List.of(
                field("shareholderType","Shareholder Type","select",true,false,null,
                        List.of("Select","Individual Shareholder","Corporate Shareholder"),0),
                field("fullName","Full Legal Name","text",true,false,null,null,1,"shareholderType","Individual Shareholder"),
                field("idNumber","NRIC / FIN","text",true,false,null,null,2,"shareholderType","Individual Shareholder"),
                field("nationality","Nationality","nationality",true,false,null,null,3,"shareholderType","Individual Shareholder"),
                field("gender","Gender","select",true,false,null,List.of("Select","Male","Female","Other"),4,"shareholderType","Individual Shareholder"),
                field("dateOfBirth","Date of Birth","date",true,false,null,null,5,"shareholderType","Individual Shareholder"),
                field("residentialAddress","Residential Address","text",true,false,null,null,6,"shareholderType","Individual Shareholder"),
                field("email","Email Address","email",true,false,null,null,7,"shareholderType","Individual Shareholder"),
                field("mobile","Mobile Number","phone",true,false,null,null,8,"shareholderType","Individual Shareholder"),
                
                field("companyName","Company Name","text",true,false,null,null,9,"shareholderType","Corporate Shareholder"),
                field("uen","UEN / Reg Number","text",true,false,null,null,10,"shareholderType","Corporate Shareholder"),
                field("dateOfIncorporation","Date of Incorporation","date",true,false,null,null,11,"shareholderType","Corporate Shareholder"),
                field("registeredAddress","Registered Address","text",true,false,null,null,12,"shareholderType","Corporate Shareholder"),
                field("email","Email Address","email",true,false,null,null,13,"shareholderType","Corporate Shareholder"),
                field("mobile","Mobile Number","phone",true,false,null,null,14,"shareholderType","Corporate Shareholder"),
                field("uboName","UBO - Full Name","text",true,false,null,null,15,"shareholderType","Corporate Shareholder"),
                field("uboIdNumber","UBO - NRIC / FIN","text",true,false,null,null,16,"shareholderType","Corporate Shareholder"),
                field("uboAddress","UBO - Address","text",true,false,null,null,17,"shareholderType","Corporate Shareholder")
        ));
        s1.setRequiredDocs(List.of(
                doc("nric","NRIC / FIN (Front & Back)",true,true,
                        List.of("fullName","idNumber","nationality","gender","dateOfBirth"),"shareholderType","Individual Shareholder"),
                doc("address_proof","Address Proof (Utility/Mobile/Bank Bill — within 3 months)",true,false,null,"shareholderType","Individual Shareholder"),
                doc("bizfile","Bizfile of the Company",true,true,
                        List.of("companyName","uen","dateOfIncorporation","registeredAddress"),"shareholderType","Corporate Shareholder"),
                doc("constitution","Constitution / M&AA",true,false,null,"shareholderType","Corporate Shareholder"),
                doc("ubo_nric","UBO - NRIC / FIN",true,true,
                        List.of("uboName","uboIdNumber"),"shareholderType","Corporate Shareholder"),
                doc("ubo_address_proof","UBO - Address Proof",true,true,
                        List.of("uboAddress","email","mobile"),"shareholderType","Corporate Shareholder")
        ));
        steps.add(s1);

        // Step 2 — Director Details
        OnboardingConfig s2 = new OnboardingConfig();
        s2.setId("ob-default-2");
        s2.setKey("director_details");
        s2.setField("step2DirectorDetails");
        s2.setTitle("Director Details");
        s2.setIcon("briefcase");
        s2.setDescription("Auto-populated from your NRIC/FIN. Please verify all information is correct.");
        s2.setDeclaration("I confirm that I am not disqualified from acting as a director under the laws of Singapore.");
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
                field("directorConsent","I consent to act as a director of the company.","checkbox",true,false,null,null,8)
        ));
        s2.setRequiredDocs(new ArrayList<>());
        steps.add(s2);

        // Step 3 — Individual Shareholder
        OnboardingConfig s3 = new OnboardingConfig();
        s3.setId("ob-default-3");
        s3.setKey("individual_shareholder");
        s3.setField("step3IndividualShareholder");
        s3.setTitle("Individual Shareholder Details");
        s3.setIcon("users");
        s3.setDescription("Capture individual shareholder information. Ownership ≥ 25% will automatically trigger UBO and AML/KYC screening.");
        s3.setSortOrder(2);
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
        s3.setRequiredDocs(List.of(doc("nric","NRIC / FIN",true,true,List.of("fullName","idNumber"))));
        steps.add(s3);

        // Step 4 — Corporate Shareholder
        OnboardingConfig s4 = new OnboardingConfig();
        s4.setId("ob-default-4");
        s4.setKey("corporate_shareholder");
        s4.setField("step4CorporateShareholder");
        s4.setTitle("Corporate Shareholder Details");
        s4.setIcon("building-2");
        s4.setDescription("Upload Bizfile and supporting documents for OCR extraction of company details.");
        s4.setSortOrder(3);
        s4.setStatus("PUBLISHED");
        s4.setVersion(1);
        s4.setDynamicSection(true);
        s4.setDynamicCountKey("corporateShareholderCount");
        s4.setExtractedFields(List.of("companyName","uen","dateOfIncorporation","registeredAddress",
                "principalActivity","countryOfIncorporation","companyType","companyStatus",
                "formerName","dateOfChangeOfName","auditFirm"));
        s4.setManualFields(List.of(
                field("companyName","Company Name","text",true,false,null,null,0),
                field("uen","UEN / Reg Number","text",true,false,null,null,1),
                field("dateOfIncorporation","Date of Incorporation","date",true,false,null,null,2),
                field("registeredAddress","Registered Address","text",true,false,null,null,3),
                field("principalActivity","Principal Activity","text",false,false,null,null,4),
                field("countryOfIncorporation","Country of Incorporation","text",true,false,null,null,5),
                field("companyType","Company Type","select",true,false,null,
                        List.of("Select","PRIVATE COMPANY LIMITED BY SHARES","PUBLIC COMPANY LIMITED BY SHARES",
                                "SOLE PROPRIETORSHIP","PARTNERSHIP","OTHER"),6),
                field("companyStatus","Status of Company","text",false,false,null,null,7),
                field("formerName","Former Name if any","text",false,false,null,null,8),
                field("dateOfChangeOfName","Date of Change of Name","date",false,false,null,null,9),
                field("auditFirm","Audit Firm","text",false,false,null,null,10),
                field("fye","Financial Year End (FYE)","text",false,false,null,null,11),
                field("totalShares","Total Number of Shares of the Company","number",true,false,null,null,12),
                field("totalShareCapital","Total Share Capital Amount of the Company","number",true,false,null,null,13),
                field("currency","Currency","select",true,false,null,List.of("SGD","USD"),14),
                field("shareClass","Share Class","select",true,false,null,List.of("Select","Ordinary","Preference"),15),
                field("numberOfShares","Number of Shares","number",true,false,null,null,16),
                field("shareCapitalAmount","Share Capital Amount","number",true,false,null,null,17),
                field("ownershipPercentage","Ownership % (auto-calculated)","number",false,true,null,null,18),
                field("uboDeclaration","Is the Shareholder the Ultimate Beneficial Owner?","select",true,false,null,List.of("No","Yes"),19)
        ));
        s4.setRequiredDocs(List.of(
                doc("bizfile","Bizfile (ACRA)",true,true,List.of("companyName","uen","dateOfIncorporation","registeredAddress")),
                doc("constitution","Constitution / M&AA",true,false,null),
                doc("cert_incorporation","Certificate of Incorporation (non-SG entities)",false,false,null),
                doc("supporting_docs","Supporting Corporate Documents",false,false,null)
        ));
        steps.add(s4);

        // Step 5 — UBO
        OnboardingConfig s5 = new OnboardingConfig();
        s5.setId("ob-default-5");
        s5.setKey("ubo");
        s5.setField("step5UBO");
        s5.setTitle("Ultimate Beneficial Owner (UBO)");
        s5.setIcon("shield-check");
        s5.setDescription("UBO verification triggers AML Screening, KYC Verification, and Sanctions Check automatically.");
        s5.setSortOrder(4);
        s5.setStatus("PUBLISHED");
        s5.setVersion(1);
        s5.setAutoChecks(List.of("AML Screening","KYC Verification","Sanctions Check"));
        s5.setManualFields(List.of(
                field("fullName","Full Name","text",true,false,null,null,0),
                field("idNumber","NRIC / FIN","text",true,false,null,null,1),
                field("nationality","Nationality","nationality",true,false,null,null,2),
                field("dateOfBirth","Date of Birth","date",true,false,null,null,3),
                field("residentialAddress","Residential Address","text",true,false,null,null,4),
                field("email","Email Address","email",true,false,null,null,5),
                field("mobile","Mobile Number","phone",true,false,null,null,6),
                field("ownershipPercentage","Ownership Percentage (%)","number",true,false,null,null,7)
        ));
        s5.setRequiredDocs(List.of(
                doc("nric","NRIC / FIN",true,true,List.of("fullName","idNumber","nationality","dateOfBirth")),
                doc("address_proof","Address Proof",true,false,null)
        ));
        steps.add(s5);

        // Step 6 — Corporate Representative
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
                field("designation","Designation / Job Title","text",false,false,null,null,4),
                field("authorizationType","Authorization Type","select",true,false,null,
                        List.of("Board Resolution","Power of Attorney","Letter of Authorization","Other"),5),
                field("residentialAddress","Residential Address","text",true,false,null,null,6),
                field("email","Email Address","email",true,false,null,null,7),
                field("mobile","Mobile Number","phone",true,false,null,null,8)
        ));
        s6.setRequiredDocs(List.of(
                doc("nric","NRIC / FIN",true,true,List.of("fullName","idNumber","nationality","dateOfBirth")),
                doc("address_proof","Address Proof",true,false,null),
                doc("auth_document","Proof of Authorization (Board Resolution / Letter of Authorization)",true,false,null)
        ));
        steps.add(s6);

        // Step 7 — Final Declaration
        OnboardingConfig s7 = new OnboardingConfig();
        s7.setId("ob-default-7");
        s7.setKey("final_declaration");
        s7.setField("step7FinalDeclaration");
        s7.setTitle("Final Declaration & Consent");
        s7.setIcon("file-signature");
        s7.setDescription("Please review all details and declare final consent before submitting your application.");
        s7.setDeclaration("I understand that providing false or misleading information may lead to the rejection of this application.");
        s7.setSortOrder(6);
        s7.setStatus("PUBLISHED");
        s7.setVersion(1);
        s7.setManualFields(List.of(
                field("declarationAgreed","I confirm that all the details provided are true and accurate to the best of my knowledge.","checkbox",true,false,null,null,0),
                field("consentAgreed","I consent to Globalisor conducting compliance, AML/KYC screening, and verification checks.","checkbox",true,false,null,null,1)
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
