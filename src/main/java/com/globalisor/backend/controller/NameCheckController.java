package com.globalisor.backend.controller;

import com.globalisor.backend.model.AcraEntity;
import com.globalisor.backend.repository.AcraEntityRepository;
import com.globalisor.backend.service.AcraDataIngestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/name-check")
public class NameCheckController {

    @Autowired
    private AcraEntityRepository acraEntityRepository;

    // Reserved or restricted government / statutory words in Singapore
    private static final Set<String> RESTRICTED_WORDS = new HashSet<>(Arrays.asList(
            "singapore", "government", "parliament", "police", "judiciary", "president",
            "minister", "ministry", "temasek", "mas", "acra", "cpf", "iras", "hdb", "changi",
            "national", "state", "republic", "sovereign", "royal", "federal", "asean"
    ));

    private static final String[] PREFIXES = {"SG", "Asia", "Pacific", "United", "Premier", "Elite", "Apex"};

    @PostMapping
    public ResponseEntity<?> checkName(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "").trim();

        Map<String, Object> response = new HashMap<>();
        response.put("checkedAt", Instant.now().toString());
        response.put("name", name);

        // 1. Validate minimum length
        if (name.length() < 3) {
            response.put("status", "invalid");
            response.put("message", "Proposed name must be at least 3 characters long.");
            response.put("suggestions", Collections.emptyList());
            return ResponseEntity.ok(response);
        }

        String normName = AcraDataIngestionService.normalizeCompanyName(name);
        String baseName = AcraDataIngestionService.extractBaseName(normName);

        // 2. Check Singapore Statutory Restricted Words
        for (String restricted : RESTRICTED_WORDS) {
            if (baseName.equalsIgnoreCase(restricted) || baseName.startsWith(restricted + " ") || baseName.endsWith(" " + restricted)) {
                response.put("status", "invalid");
                response.put("message", String.format("The term '%s' is a restricted word under Singapore ACRA naming guidelines requiring prior ministerial approval.", restricted.toUpperCase()));
                response.put("suggestions", generateAlternatives(baseName.replace(restricted, "Global"), name, 5));
                return ResponseEntity.ok(response);
            }
        }

        // 3. Exact match check in ACRA Warehouse (2M+ Singapore Entities)
        List<AcraEntity> exactHits = acraEntityRepository.findByNormalizedName(normName);
        if (exactHits.isEmpty() && !baseName.isEmpty() && !baseName.equals(normName)) {
            exactHits = acraEntityRepository.findByBaseName(baseName);
        }

        if (!exactHits.isEmpty()) {
            AcraEntity hit = exactHits.get(0);
            boolean isLive = hit.isLive();
            response.put("status", "taken");
            String statusMsg = isLive
                    ? String.format("This name is already registered in ACRA as an active entity (%s • UEN: %s).",
                    hit.getEntityName(), hit.getUen())
                    : String.format("This name was previously registered with ACRA (%s • UEN: %s • %s). ACRA name restrictions apply.",
                    hit.getEntityName(), hit.getUen(), hit.getUenStatus());
            response.put("message", statusMsg);
            response.put("matchedUen", hit.getUen());
            response.put("matchedEntityName", hit.getEntityName());
            response.put("matchedStatus", hit.getUenStatus());
            response.put("suggestions", generateAlternatives(baseName, name, 5));
            return ResponseEntity.ok(response);
        }

        // 4. Candidate / Similar entities check in ACRA Warehouse
        if (baseName.length() >= 3) {
            String vowelRegex = "^" + baseName.replaceAll("(?i)[aeiou]", "[aeiou]") + "$";
            String prefix = baseName.length() >= 4 ? "^" + baseName.substring(0, 4) : "^" + baseName;

            List<AcraEntity> candidateHits = acraEntityRepository.findSimilarCandidates(vowelRegex, prefix, PageRequest.of(0, 30));
            if (candidateHits.isEmpty()) {
                candidateHits = acraEntityRepository.findByNameCandidate(baseName, PageRequest.of(0, 15));
            }

            AcraEntity bestSimilar = null;
            int bestDistance = Integer.MAX_VALUE;
            double bestSimilarity = 0.0;
            String similarityReason = "";

            for (AcraEntity cand : candidateHits) {
                if (!cand.isLive()) continue;
                String candBase = cand.getBaseName() != null ? cand.getBaseName().toLowerCase().trim() : "";
                if (candBase.isEmpty()) continue;

                // Identical base name
                if (candBase.equalsIgnoreCase(baseName)) {
                    response.put("status", "taken");
                    response.put("message", String.format("An identical base company name is already registered in ACRA: '%s' (UEN: %s).",
                            cand.getEntityName(), cand.getUen()));
                    response.put("matchedUen", cand.getUen());
                    response.put("matchedEntityName", cand.getEntityName());
                    response.put("matchedStatus", cand.getUenStatus());
                    response.put("suggestions", generateAlternatives(baseName, name, 5));
                    return ResponseEntity.ok(response);
                }

                // Check vowel-swap identity (e.g. "sellinall" vs "sellonall")
                boolean vowelSwap = maskVowels(baseName).equalsIgnoreCase(maskVowels(candBase));
                int dist = computeLevenshteinDistance(baseName, candBase);
                double jaro = computeJaroWinkler(baseName, candBase);

                // High similarity criteria
                boolean isSimilar = vowelSwap || dist <= 1 || (dist <= 2 && baseName.length() >= 6) || jaro >= 0.88;

                if (isSimilar) {
                    if (bestSimilar == null || dist < bestDistance || (dist == bestDistance && jaro > bestSimilarity)) {
                        bestSimilar = cand;
                        bestDistance = dist;
                        bestSimilarity = jaro;
                        if (vowelSwap) {
                            similarityReason = "Vowel variation / phonetic similarity";
                        } else if (dist <= 1) {
                            similarityReason = "Single letter variation";
                        } else {
                            similarityReason = String.format("%.0f%% character similarity", jaro * 100);
                        }
                    }
                }
            }

            if (bestSimilar != null) {
                response.put("status", "similar");
                response.put("message", String.format(
                        "Similar registered entity found in ACRA: '%s' (UEN: %s). %s between '%s' and '%s'. Under ACRA Section 27(1)(a), names confusingly similar to an existing entity require ACRA referral or consent.",
                        bestSimilar.getEntityName(), bestSimilar.getUen(), similarityReason, name, bestSimilar.getEntityName()));
                response.put("similarEntity", bestSimilar.getEntityName());
                response.put("similarUen", bestSimilar.getUen());
                response.put("similarityReason", similarityReason);
                response.put("similarityScore", Math.round(bestSimilarity * 100));
                response.put("suggestions", generateAlternatives(baseName, name, 5));
                return ResponseEntity.ok(response);
            }
        }

        // 5. Cleared & Available
        response.put("status", "available");
        response.put("message", "This name appears to be available in ACRA registry records with 0 conflicts.");
        response.put("suggestions", Collections.emptyList());
        return ResponseEntity.ok(response);
    }

    private static String maskVowels(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[aeiou]", "*");
    }

    private static int computeLevenshteinDistance(String s1, String s2) {
        if (s1 == null) s1 = "";
        if (s2 == null) s2 = "";
        int len1 = s1.length();
        int len2 = s2.length();
        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) dp[i][0] = i;
        for (int j = 0; j <= len2; j++) dp[0][j] = j;

        for (int i = 1; i <= len1; i++) {
            char c1 = s1.charAt(i - 1);
            for (int j = 1; j <= len2; j++) {
                char c2 = s2.charAt(j - 1);
                int cost = (c1 == c2) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[len1][len2];
    }

    private static double computeJaroWinkler(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equalsIgnoreCase(s2)) return 1.0;

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
                if (s2Matches[j] || s1.charAt(i) != s2.charAt(j)) continue;
                s1Matches[i] = true;
                s2Matches[j] = true;
                matches++;
                break;
            }
        }

        if (matches == 0) return 0.0;

        double transpositions = 0;
        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (!s1Matches[i]) continue;
            while (!s2Matches[k]) k++;
            if (s1.charAt(i) != s2.charAt(k)) transpositions++;
            k++;
        }

        double jaro = ((matches / (double) len1) + (matches / (double) len2) + ((matches - transpositions / 2.0) / matches)) / 3.0;

        int prefix = 0;
        for (int i = 0; i < Math.min(4, Math.min(len1, len2)); i++) {
            if (s1.charAt(i) == s2.charAt(i)) prefix++;
            else break;
        }

        return jaro + (prefix * 0.1 * (1.0 - jaro));
    }

    private List<String> generateAlternatives(String baseName, String originalName, int count) {
        List<String> suggestions = new ArrayList<>();
        if (baseName == null || baseName.trim().isEmpty()) baseName = "Enterprise";

        for (String prefix : PREFIXES) {
            if (suggestions.size() >= count) break;
            String candidate = capitalize(prefix + " " + baseName + " Pte. Ltd.");
            if (!candidate.equalsIgnoreCase(originalName)) {
                suggestions.add(candidate);
            }
        }

        int year = 2026;
        suggestions.add(capitalize(baseName) + " " + year + " Pte. Ltd.");

        String[] adjectives = {"Innovations", "Ventures", "Technologies", "Global", "Systems"};
        for (String adj : adjectives) {
            if (suggestions.size() >= count) break;
            suggestions.add(capitalize(baseName) + " " + adj + " Pte. Ltd.");
        }

        if (suggestions.size() > count) {
            suggestions = suggestions.subList(0, count);
        }

        return suggestions;
    }

    private String capitalize(String input) {
        if (input == null || input.isEmpty()) return input;
        String[] words = input.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0 && (word.equalsIgnoreCase("and") || word.equalsIgnoreCase("of") || word.equalsIgnoreCase("for"))) {
                sb.append(word.toLowerCase()).append(" ");
            } else {
                sb.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return sb.toString().trim();
    }
}
