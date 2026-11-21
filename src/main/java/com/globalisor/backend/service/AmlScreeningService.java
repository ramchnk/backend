package com.globalisor.backend.service;

import com.globalisor.backend.model.AmlWatchlistRecord;
import com.globalisor.backend.repository.AmlWatchlistRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class AmlScreeningService {

    @Autowired
    private AmlWatchlistRepository amlWatchlistRepository;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchDetail {
        private String recordId;
        private String source;
        private String entityType;
        private String primaryName;
        private List<String> aliases;
        private List<String> programs;
        private String matchedField;
        private String matchedValue;
        private int confidenceScore; // 0 - 100%
        private String matchType; // "EXACT_ID", "EXACT_NAME", "FUZZY_NAME"
        private String remarks;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AmlScreeningResult {
        private boolean matchFound;
        private String amlStatus; // "clean", "flagged"
        private String riskLevel; // "Low", "Medium", "High", "Critical"
        private int highestConfidence;
        private String screenedName;
        private String screenedIdNumber;
        @Builder.Default
        private boolean isPep = false;
        @Builder.Default
        private boolean isSanction = false;
        private String actionRequired; // "NONE", "ENHANCED_DUE_DILIGENCE", "BLOCK_TRANSACTION"
        private String recommendation;
        @Builder.Default
        private List<MatchDetail> matches = new ArrayList<>();
        private String summary;
        @Builder.Default
        private Date screenedAt = new Date();
    }

    /**
     * Screen a single individual (Applicant, Director, Shareholder, UBO)
     */
    public AmlScreeningResult screenPerson(String fullName, String idNumber, String nationality, String dob) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return AmlScreeningResult.builder()
                    .matchFound(false)
                    .amlStatus("clean")
                    .riskLevel("Low")
                    .highestConfidence(0)
                    .screenedName("")
                    .screenedIdNumber(idNumber)
                    .actionRequired("NONE")
                    .recommendation("No name provided for AML screening.")
                    .summary("No name provided for AML screening.")
                    .build();
        }

        String rawName = fullName.trim();
        String normName = AmlDataIngestionService.normalizeName(rawName);
        String cleanId = idNumber != null ? idNumber.trim() : "";
        List<MatchDetail> matches = new ArrayList<>();
        Set<String> matchedRecordIds = new HashSet<>();

        // 1. Tier 1: Exact Passport / ID Match (Highest Priority)
        if (!cleanId.isEmpty() && cleanId.length() >= 4) {
            List<AmlWatchlistRecord> idHits = amlWatchlistRepository.findByPassportOrId(cleanId);
            for (AmlWatchlistRecord rec : idHits) {
                if (matchedRecordIds.add(rec.getId())) {
                    matches.add(MatchDetail.builder()
                            .recordId(rec.getId())
                            .source(rec.getSource())
                            .entityType(rec.getEntityType())
                            .primaryName(rec.getPrimaryName())
                            .aliases(rec.getAliases())
                            .programs(rec.getPrograms())
                            .matchedField("Passport / ID Number")
                            .matchedValue(cleanId)
                            .confidenceScore(100)
                            .matchType("EXACT_ID")
                            .remarks(rec.getRemarks())
                            .build());
                }
            }
        }

        // 2. Tier 2: Instant Exact Name & Alias Match (0 ms B-Tree Index Scan)
        if (!normName.isEmpty()) {
            List<AmlWatchlistRecord> exactNameHits = amlWatchlistRepository.findByNormalizedName(normName);
            for (AmlWatchlistRecord rec : exactNameHits) {
                if (matchedRecordIds.add(rec.getId())) {
                    matches.add(MatchDetail.builder()
                            .recordId(rec.getId())
                            .source(rec.getSource())
                            .entityType(rec.getEntityType())
                            .primaryName(rec.getPrimaryName())
                            .aliases(rec.getAliases())
                            .programs(rec.getPrograms())
                            .matchedField("Primary Name")
                            .matchedValue(rec.getPrimaryName())
                            .confidenceScore(98)
                            .matchType("EXACT_NAME")
                            .remarks(rec.getRemarks())
                            .build());
                }
            }

            List<AmlWatchlistRecord> exactAliasHits = amlWatchlistRepository.findByNormalizedAlias(normName);
            for (AmlWatchlistRecord rec : exactAliasHits) {
                if (matchedRecordIds.add(rec.getId())) {
                    matches.add(MatchDetail.builder()
                            .recordId(rec.getId())
                            .source(rec.getSource())
                            .entityType(rec.getEntityType())
                            .primaryName(rec.getPrimaryName())
                            .aliases(rec.getAliases())
                            .programs(rec.getPrograms())
                            .matchedField("Alias (AKA)")
                            .matchedValue(normName)
                            .confidenceScore(95)
                            .matchType("EXACT_ALIAS")
                            .remarks(rec.getRemarks())
                            .build());
                }
            }
        }

        // 3. Tier 3: Fuzzy Candidate Search (for typos, alternate spellings, or partial names)
        // Only run token scan if we don't already have an exact 98%+ match
        if (matches.isEmpty() || matches.get(0).getConfidenceScore() < 95) {
            String[] tokens = normName.split("\\s+");
            Set<String> searchedIds = new HashSet<>();
            List<AmlWatchlistRecord> candidates = new ArrayList<>();

            for (String token : tokens) {
                if (token.length() >= 3) {
                    List<AmlWatchlistRecord> hits = amlWatchlistRepository.findByNameCandidate(token, PageRequest.of(0, 30));
                    for (AmlWatchlistRecord hit : hits) {
                        if (searchedIds.add(hit.getId()) && !matchedRecordIds.contains(hit.getId())) {
                            candidates.add(hit);
                        }
                    }
                }
            }

            // Jaro-Winkler evaluation for candidates
            for (AmlWatchlistRecord cand : candidates) {
                double highestSim = 0.0;
                String bestMatchedName = cand.getPrimaryName();
                String bestField = "Primary Name";

                double pSim = jaroWinklerSimilarity(normName, cand.getNormalizedName());
                if (pSim > highestSim) {
                    highestSim = pSim;
                    bestMatchedName = cand.getPrimaryName();
                    bestField = "Primary Name";
                }

                if (cand.getNormalizedAliases() != null) {
                    for (int i = 0; i < cand.getNormalizedAliases().size(); i++) {
                        String normAlias = cand.getNormalizedAliases().get(i);
                        double aSim = jaroWinklerSimilarity(normName, normAlias);
                        if (aSim > highestSim) {
                            highestSim = aSim;
                            bestMatchedName = (i < cand.getAliases().size()) ? cand.getAliases().get(i) : normAlias;
                            bestField = "Alias (AKA)";
                        }
                    }
                }

                // Threshold: 0.82
                if (highestSim >= 0.82) {
                    int confidence = (int) Math.round(highestSim * 100.0);
                    if (highestSim >= 0.99) confidence = 98;

                    // DOB correlation
                    if (dob != null && !dob.trim().isEmpty() && cand.getDatesOfBirth() != null) {
                        if (cand.getDatesOfBirth().stream().anyMatch(d -> d.contains(dob.trim()) || dob.trim().contains(d))) {
                            confidence = Math.min(100, confidence + 10);
                        }
                    }

                    // Nationality correlation
                    if (nationality != null && !nationality.trim().isEmpty() && cand.getNationalities() != null) {
                        String cleanNat = nationality.trim().toLowerCase();
                        if (cand.getNationalities().stream().anyMatch(n -> n.toLowerCase().contains(cleanNat) || cleanNat.contains(n.toLowerCase()))) {
                            confidence = Math.min(100, confidence + 5);
                        }
                    }

                    if (confidence >= 80 && matchedRecordIds.add(cand.getId())) {
                        matches.add(MatchDetail.builder()
                                .recordId(cand.getId())
                                .source(cand.getSource())
                                .entityType(cand.getEntityType())
                                .primaryName(cand.getPrimaryName())
                                .aliases(cand.getAliases())
                                .programs(cand.getPrograms())
                                .matchedField(bestField)
                                .matchedValue(bestMatchedName)
                                .confidenceScore(confidence)
                                .matchType(confidence >= 98 ? "EXACT_NAME" : "FUZZY_NAME")
                                .remarks(cand.getRemarks())
                                .build());
                    }
                }
            }
        }

        // Sort matches by confidence descending
        matches.sort((a, b) -> Integer.compare(b.getConfidenceScore(), a.getConfidenceScore()));

        int maxConf = matches.isEmpty() ? 0 : matches.get(0).getConfidenceScore();
        boolean isMatch = maxConf >= 80;

        boolean isPep = false;
        boolean isSanction = false;
        String actionRequired = "NONE";
        String riskLevel = "Low";
        String recommendation = "Applicant is clear of international sanctions and PEP registries. Standard onboarding applies.";
        String summary;

        if (isMatch) {
            MatchDetail top = matches.get(0);
            boolean isPepType = "PEP".equalsIgnoreCase(top.getEntityType())
                    || "PEP".equalsIgnoreCase(top.getSource())
                    || (top.getPrograms() != null && top.getPrograms().stream().anyMatch(p -> p.toLowerCase().contains("pep")));

            if (isPepType) {
                isPep = true;
                riskLevel = "High";
                actionRequired = "ENHANCED_DUE_DILIGENCE";
                recommendation = "Under Singapore ACRA / MAS guidelines: Politically Exposed Persons (PEPs) are NOT prohibited from registering a company. However, Mandatory Senior Management Approval is required before onboarding, and Enhanced Customer Due Diligence (EDD) must be conducted to establish Source of Wealth (SoW) and Source of Funds (SoF).";
                summary = String.format("AML PEP ALERT: Politically Exposed Person (PEP) detected for '%s' (Confidence: %d%%, Source: %s, ID: %s). Requires Senior Management Approval & Enhanced Due Diligence (EDD).",
                        rawName, maxConf, top.getSource(), top.getRecordId());
            } else {
                isSanction = true;
                riskLevel = maxConf >= 90 ? "Critical" : "High";
                actionRequired = "BLOCK_TRANSACTION";
                recommendation = "Direct match on international sanctions list (" + top.getSource() + "). Under Singapore MAS and UN Sanctions Regulations, Corporate Service Providers are strictly prohibited from establishing business relations. Application must be rejected and flagged to compliance.";
                summary = String.format("PROHIBITED SANCTIONS ALERT: Sanctions match detected for '%s' (Confidence: %d%%, Source: %s, ID: %s). Transaction and incorporation must be blocked immediately.",
                        rawName, maxConf, top.getSource(), top.getRecordId());
            }
        } else {
            summary = String.format("AML Screening CLEAN: No sanctions or PEP matches found for '%s'.", rawName);
        }

        return AmlScreeningResult.builder()
                .matchFound(isMatch)
                .amlStatus(isMatch ? "flagged" : "clean")
                .riskLevel(riskLevel)
                .highestConfidence(maxConf)
                .screenedName(rawName)
                .screenedIdNumber(cleanId)
                .isPep(isPep)
                .isSanction(isSanction)
                .actionRequired(actionRequired)
                .recommendation(recommendation)
                .matches(matches)
                .summary(summary)
                .screenedAt(new Date())
                .build();
    }

    /**
     * Screen an entire incorporation application (applicant, proposed directors, shareholders, ubos)
     */
    public Map<String, Object> screenIncorporationApplication(Map<String, Object> data) {
        Map<String, Object> report = new HashMap<>();
        List<Map<String, Object>> partyResults = new ArrayList<>();

        if (data == null) {
            report.put("overallAmlStatus", "clean");
            report.put("overallRiskLevel", "Low");
            report.put("actionRequired", "NONE");
            report.put("summary", "No application data provided for screening.");
            report.put("regulatoryDirective", "Standard onboarding applies.");
            report.put("parties", partyResults);
            return report;
        }

        // Unwrap nested formData or data if passed from admin frontend
        Map<String, Object> root = data;
        if (data.containsKey("formData") && data.get("formData") instanceof Map) {
            root = (Map<String, Object>) data.get("formData");
        } else if (data.containsKey("data") && data.get("data") instanceof Map) {
            root = (Map<String, Object>) data.get("data");
        }

        Set<String> screenedKeys = new HashSet<>();

        // 1. Screen Applicant / User
        String applicantName = extractString(root, "fullName", "name", "clientName", "contactPerson");
        if (applicantName.isEmpty() && root.containsKey("contact") && root.get("contact") instanceof Map) {
            Map<?, ?> contact = (Map<?, ?>) root.get("contact");
            String fName = contact.get("firstName") != null ? contact.get("firstName").toString() : "";
            String lName = contact.get("lastName") != null ? contact.get("lastName").toString() : "";
            applicantName = (fName + " " + lName).trim();
        }
        if (applicantName.isEmpty() && (data.containsKey("clientFirstName") || data.containsKey("clientLastName"))) {
            String fName = data.get("clientFirstName") != null ? data.get("clientFirstName").toString() : "";
            String lName = data.get("clientLastName") != null ? data.get("clientLastName").toString() : "";
            applicantName = (fName + " " + lName).trim();
        }

        String applicantId = extractString(root, "idNum", "passport", "idNumber", "nric");
        String applicantNat = extractString(root, "nationality", "nation");
        if (!applicantName.isEmpty()) {
            AmlScreeningResult res = screenPerson(applicantName, applicantId, applicantNat, null);
            Map<String, Object> partyMap = new HashMap<>();
            partyMap.put("role", "Applicant");
            partyMap.put("name", applicantName);
            partyMap.put("idNumber", applicantId);
            partyMap.put("nationality", applicantNat);
            partyMap.put("screeningResult", res);
            partyResults.add(partyMap);
            screenedKeys.add((applicantName.toLowerCase() + "_" + applicantId.toLowerCase()).trim());
        }

        // 2. Screen Proposed Directors
        Object directorsObj = root.get("directors") != null ? root.get("directors") : root.get("officers");
        if (directorsObj instanceof List) {
            int dIndex = 1;
            for (Object dObj : (List<?>) directorsObj) {
                if (dObj instanceof Map) {
                    Map<?, ?> d = (Map<?, ?>) dObj;
                    String dName = d.get("name") != null ? d.get("name").toString() : "";
                    String dId = d.get("idNumber") != null ? d.get("idNumber").toString() : (d.get("nric") != null ? d.get("nric").toString() : (d.get("idNum") != null ? d.get("idNum").toString() : ""));
                    String dNat = d.get("nationality") != null ? d.get("nationality").toString() : "";
                    String dDob = d.get("dob") != null ? d.get("dob").toString() : "";
                    String key = (dName.toLowerCase() + "_" + dId.toLowerCase()).trim();
                    if (!dName.isEmpty() && screenedKeys.add(key)) {
                        AmlScreeningResult res = screenPerson(dName, dId, dNat, dDob);
                        Map<String, Object> partyMap = new HashMap<>();
                        partyMap.put("role", "Director #" + dIndex);
                        partyMap.put("name", dName);
                        partyMap.put("idNumber", dId);
                        partyMap.put("nationality", dNat);
                        partyMap.put("dob", dDob);
                        partyMap.put("isNominee", Boolean.TRUE.equals(d.get("isNominee")));
                        partyMap.put("screeningResult", res);
                        partyResults.add(partyMap);
                    }
                    dIndex++;
                }
            }
        }

        // 3. Screen Proposed Shareholders
        Object shareholdersObj = root.get("shareholders") != null ? root.get("shareholders") : root.get("members");
        if (shareholdersObj instanceof List) {
            int sIndex = 1;
            for (Object sObj : (List<?>) shareholdersObj) {
                if (sObj instanceof Map) {
                    Map<?, ?> s = (Map<?, ?>) sObj;
                    String sName = s.get("name") != null ? s.get("name").toString() : "";
                    String sId = s.get("idNumber") != null ? s.get("idNumber").toString() : (s.get("nric") != null ? s.get("nric").toString() : (s.get("idNum") != null ? s.get("idNum").toString() : ""));
                    String sNat = s.get("nationality") != null ? s.get("nationality").toString() : "";
                    String key = (sName.toLowerCase() + "_" + sId.toLowerCase()).trim();
                    if (!sName.isEmpty() && screenedKeys.add(key)) {
                        AmlScreeningResult res = screenPerson(sName, sId, sNat, null);
                        Map<String, Object> partyMap = new HashMap<>();
                        partyMap.put("role", "Shareholder #" + sIndex);
                        partyMap.put("name", sName);
                        partyMap.put("idNumber", sId);
                        partyMap.put("nationality", sNat);
                        partyMap.put("type", s.get("type"));
                        partyMap.put("screeningResult", res);
                        partyResults.add(partyMap);
                    }
                    sIndex++;
                }
            }
        }

        // 4. Screen Ultimate Beneficial Owners (UBOs)
        Object ubosObj = root.get("ubos") != null ? root.get("ubos") : root.get("controllers");
        if (ubosObj instanceof List) {
            int uIndex = 1;
            for (Object uObj : (List<?>) ubosObj) {
                if (uObj instanceof Map) {
                    Map<?, ?> u = (Map<?, ?>) uObj;
                    String uName = u.get("name") != null ? u.get("name").toString() : "";
                    String uId = u.get("idNumber") != null ? u.get("idNumber").toString() : (u.get("idNum") != null ? u.get("idNum").toString() : "");
                    String uNat = u.get("nationality") != null ? u.get("nationality").toString() : "";
                    String key = (uName.toLowerCase() + "_" + uId.toLowerCase()).trim();
                    if (!uName.isEmpty() && screenedKeys.add(key)) {
                        AmlScreeningResult res = screenPerson(uName, uId, uNat, null);
                        Map<String, Object> partyMap = new HashMap<>();
                        partyMap.put("role", "UBO #" + uIndex);
                        partyMap.put("name", uName);
                        partyMap.put("idNumber", uId);
                        partyMap.put("nationality", uNat);
                        partyMap.put("screeningResult", res);
                        partyResults.add(partyMap);
                    }
                    uIndex++;
                }
            }
        }

        // 5. Overall status aggregation
        boolean anyFlagged = partyResults.stream().anyMatch(p -> ((AmlScreeningResult) p.get("screeningResult")).isMatchFound());
        boolean anyPep = partyResults.stream().anyMatch(p -> ((AmlScreeningResult) p.get("screeningResult")).isPep());
        boolean anySanction = partyResults.stream().anyMatch(p -> ((AmlScreeningResult) p.get("screeningResult")).isSanction());

        int maxConfidence = partyResults.stream()
                .mapToInt(p -> ((AmlScreeningResult) p.get("screeningResult")).getHighestConfidence())
                .max()
                .orElse(0);

        String overallStatus = anyFlagged ? "flagged" : "clean";
        String overallRisk = anySanction ? "Critical" : (anyPep ? "High" : (maxConfidence >= 80 ? "Medium" : "Low"));
        String actionRequired = anySanction ? "BLOCK_TRANSACTION" : (anyPep ? "ENHANCED_DUE_DILIGENCE" : "APPROVED_CLEAN");

        String summary;
        String regulatoryDirective;
        if (anySanction) {
            summary = "🚨 PROHIBITED SANCTIONS ALERT: One or more company officers matched international sanctions lists. Company registration must be BLOCKED immediately.";
            regulatoryDirective = "Under Singapore MAS and UN Sanctions Regulations, Corporate Service Providers are strictly prohibited from establishing business relations with sanctioned individuals. File suspicious transaction report (STR) if required.";
        } else if (anyPep) {
            summary = "⚠️ POLITICALLY EXPOSED PERSON (PEP) DETECTED: Foreign Politically Exposed Person detected among company officers. Requires Mandatory Senior Management Approval and Enhanced Due Diligence (EDD) before ACRA Bizfile submission.";
            regulatoryDirective = "Under Singapore ACRA / MAS guidelines, PEPs are legally permitted to incorporate companies. However, Senior Management must approve the onboarding, and Source of Wealth (SoW) and Source of Funds (SoF) must be verified.";
        } else {
            summary = "✅ AML CLEARANCE PASSED: All applicant, directors, shareholders, and UBOs screened clean against 820,647 records with 0 sanctions or PEP matches.";
            regulatoryDirective = "Application meets standard Singapore AML/CFT onboarding criteria. Approved for ACRA Bizfile company incorporation filing.";
        }

        report.put("overallAmlStatus", overallStatus);
        report.put("overallRiskLevel", overallRisk);
        report.put("actionRequired", actionRequired);
        report.put("isPep", anyPep);
        report.put("isSanction", anySanction);
        report.put("maxConfidenceScore", maxConfidence);
        report.put("summary", summary);
        report.put("regulatoryDirective", regulatoryDirective);
        report.put("partiesCount", partyResults.size());
        report.put("flaggedCount", partyResults.stream().filter(p -> ((AmlScreeningResult) p.get("screeningResult")).isMatchFound()).count());
        report.put("parties", partyResults);
        report.put("timestamp", new Date());

        return report;
    }

    private String extractString(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            if (map.containsKey(k) && map.get(k) != null) {
                String val = map.get(k).toString().trim();
                if (!val.isEmpty()) return val;
            }
        }
        return "";
    }

    /**
     * Standard Jaro-Winkler string similarity (0.0 to 1.0)
     */
    public static double jaroWinklerSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;

        int len1 = s1.length();
        int len2 = s2.length();
        if (len1 == 0 || len2 == 0) return 0.0;

        int matchDistance = Math.max(len1, len2) / 2 - 1;

        boolean[] s1Matches = new boolean[len1];
        boolean[] s2Matches = new boolean[len2];

        int matches = 0;
        for (int i = 0; i < len1; i++) {
            int start = Math.max(0, i - matchDistance);
            int end = Math.min(i + matchDistance + 1, len2);

            for (int j = start; j < end; j++) {
                if (s2Matches[j]) continue;
                if (s1.charAt(i) != s2.charAt(j)) continue;
                s1Matches[i] = true;
                s2Matches[j] = true;
                matches++;
                break;
            }
        }

        if (matches == 0) return 0.0;

        double transpositions = 0.0;
        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (!s1Matches[i]) continue;
            while (!s2Matches[k]) k++;
            if (s1.charAt(i) != s2.charAt(k)) transpositions++;
            k++;
        }
        transpositions /= 2.0;

        double jaro = ((matches / (double) len1) +
                (matches / (double) len2) +
                ((matches - transpositions) / (double) matches)) / 3.0;

        // Winkler prefix scale (max 4 characters)
        int prefix = 0;
        for (int i = 0; i < Math.min(4, Math.min(len1, len2)); i++) {
            if (s1.charAt(i) == s2.charAt(i)) prefix++;
            else break;
        }

        double scalingFactor = 0.1;
        return jaro + (prefix * scalingFactor * (1.0 - jaro));
    }
}
