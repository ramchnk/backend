package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Document(collection = "attendance")
public class Attendance {
    @Id
    private String id;
    private String userId;
    private String date; // YYYY-MM-DD format
    private Long signInTime;
    private Long signOutTime;
    private Double totalWorkingHours = 0.0;
    private String timeZone;
    private String ipAddress;
    private String device;
    private Long lastActivity;
    private String status = "SIGNED_OUT"; // SIGNED_IN, SIGNED_OUT, BREAK_IN
    private List<BreakSession> breaks = new ArrayList<>();
    private List<AuditLog> auditLogs = new ArrayList<>();

    @Data
    @NoArgsConstructor
    public static class BreakSession {
        private Long breakIn;
        private Long breakOut;
        private Double durationHours = 0.0;

        public BreakSession(Long breakIn, Long breakOut) {
            this.breakIn = breakIn;
            this.breakOut = breakOut;
            if (breakIn != null && breakOut != null) {
                this.durationHours = (breakOut - breakIn) / 3600000.0;
            }
        }
    }

    @Data
    @NoArgsConstructor
    public static class AuditLog {
        private Long timestamp;
        private String action;
        private String performedBy;
        private String description;

        public AuditLog(Long timestamp, String action, String performedBy, String description) {
            this.timestamp = timestamp;
            this.action = action;
            this.performedBy = performedBy;
            this.description = description;
        }
    }
}
