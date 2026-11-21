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
     * Screen an entire incorporation application (applicant, proposed directors, shareholders, company name)
     */
    public Map<String, Object> screenIncorporationApplication(Map<String, Object> data) {
        Map<String, Object> report = new HashMap<>();
        List<AmlScreeningResult> partyResults = new ArrayList<>();

        if (data == null) {
            report.put("overallAmlStatus", "clean");
            report.put("overallRiskLevel", "Low");
            report.put("parties", partyResults);
            return report;
        }

        // 1. Screen Applicant / User
        String applicantName = extractString(data, "fullName", "name", "clientName", "contactPerson");
        String applicantId = extractString(data, "idNum", "passport", "idNumber", "nric");
        String applicantNat = extractString(data, "nationality", "nation");
        if (!applicantName.isEmpty()) {
            partyResults.add(screenPerson(applicantName, applicantId, applicantNat, null));
        }

        // 2. Screen Proposed Directors
        Object directorsObj = data.get("directors");
        if (directorsObj instanceof List) {
            for (Object dObj : (List<?>) directorsObj) {
                if (dObj instanceof Map) {
                    Map<?, ?> d = (Map<?, ?>) dObj;
                    String dName = d.get("name") != null ? d.get("name").toString() : "";
                    String dId = d.get("idNumber") != null ? d.get("idNumber").toString() : (d.get("nric") != null ? d.get("nric").toString() : "");
                    String dNat = d.get("nationality") != null ? d.get("nationality").toString() : "";
                    String dDob = d.get("dob") != null ? d.get("dob").toString() : "";
                    if (!dName.isEmpty()) {
                        partyResults.add(screenPerson(dName, dId, dNat, dDob));
                    }
                }
            }
        }

        // 3. Screen Proposed Shareholders / UBOs
        Object shareholdersObj = data.get("shareholders") != null ? data.get("shareholders") : data.get("members");
        if (shareholdersObj instanceof List) {
            for (Object sObj : (List<?>) shareholdersObj) {
                if (sObj instanceof Map) {
                    Map<?, ?> s = (Map<?, ?>) sObj;
                    String sName = s.get("name") != null ? s.get("name").toString() : "";
                    String sId = s.get("idNumber") != null ? s.get("idNumber").toString() : (s.get("nric") != null ? s.get("nric").toString() : "");
                    String sNat = s.get("nationality") != null ? s.get("nationality").toString() : "";
                    if (!sName.isEmpty()) {
                        partyResults.add(screenPerson(sName, sId, sNat, null));
                    }
                }
            }
        }

        // 4. Overall status aggregation
        boolean anyFlagged = partyResults.stream().anyMatch(AmlScreeningResult::isMatchFound);
        int maxConfidence = partyResults.stream().mapToInt(AmlScreeningResult::getHighestConfidence).max().orElse(0);

        String overallStatus = anyFlagged ? "flagged" : "clean";
        String overallRisk = maxConfidence >= 90 ? "High" : (maxConfidence >= 80 ? "Medium" : "Low");

        report.put("overallAmlStatus", overallStatus);
        report.put("overallRiskLevel", overallRisk);
        report.put("maxConfidenceScore", maxConfidence);
        report.put("partiesCount", partyResults.size());
        report.put("flaggedCount", partyResults.stream().filter(AmlScreeningResult::isMatchFound).count());
        report.put("partyScreeningResults", partyResults);
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
