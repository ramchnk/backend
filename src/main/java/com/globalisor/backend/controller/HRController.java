package com.globalisor.backend.controller;

import com.globalisor.backend.model.Attendance;
import com.globalisor.backend.model.Attendance.BreakSession;
import com.globalisor.backend.model.Attendance.AuditLog;
import com.globalisor.backend.model.User;
import com.globalisor.backend.repository.AttendanceRepository;
import com.globalisor.backend.repository.UserRepository;
import com.globalisor.backend.websocket.ChatWebSocketHandler;
import com.globalisor.backend.security.EncryptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/hr")
public class HRController {

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatWebSocketHandler chatWebSocketHandler;

    @Autowired
    private EncryptionUtils encryptionUtils;

    private ZoneId getZoneId(String timeZone) {
        try {
            if (timeZone != null && !timeZone.trim().isEmpty()) {
                return ZoneId.of(timeZone);
            }
        } catch (Exception e) {
            // ignore and fallback
        }
        return ZoneId.systemDefault();
    }

    private String getTodayDateString(String timeZone) {
        return LocalDate.now(getZoneId(timeZone)).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    // --- ATTENDANCE ACTIONS ---

    @PostMapping("/attendance/signin")
    public ResponseEntity<?> signIn(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        String timeZone = body.get("timeZone");
        String ipAddress = body.get("ipAddress");
        String device = body.get("device");

        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
        }
        User user = userOpt.get();

        String date = getTodayDateString(timeZone);
        Optional<Attendance> existingAtt = attendanceRepository.findByUserIdAndDate(userId, date);

        Attendance attendance;
        if (existingAtt.isPresent()) {
            attendance = existingAtt.get();
            // If already signed in, just return it
            if (!"SIGNED_OUT".equals(attendance.getStatus())) {
                return ResponseEntity.ok(attendance);
            }
            // Re-signing in (correction or signout override)
            attendance.setSignInTime(System.currentTimeMillis());
            attendance.setStatus("SIGNED_IN");
        } else {
            attendance = new Attendance();
            attendance.setId("att-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000));
            attendance.setUserId(userId);
            attendance.setDate(date);
            attendance.setSignInTime(System.currentTimeMillis());
            attendance.setStatus("SIGNED_IN");
            attendance.setBreaks(new ArrayList<>());
            attendance.setAuditLogs(new ArrayList<>());
        }

        attendance.setTimeZone(timeZone != null ? timeZone : ZoneId.systemDefault().getId());
        attendance.setIpAddress(ipAddress);
        attendance.setDevice(device);
        attendance.setLastActivity(System.currentTimeMillis());
        attendance.getAuditLogs().add(new AuditLog(System.currentTimeMillis(), "SIGN_IN", "staff", "Clocked in via Staff Portal"));

        attendanceRepository.save(attendance);

        user.setAttendanceStatus("SIGNED_IN");
        user.setOnlineStatus("ONLINE");
        userRepository.save(user);

        // Broadcast websocket event
        broadcastAttendanceUpdate(userId, user, attendance);

        return ResponseEntity.ok(attendance);
    }

    @PostMapping("/attendance/signout")
    public ResponseEntity<?> signOut(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        String timeZone = body.get("timeZone");

        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
        }
        User user = userOpt.get();

        String date = getTodayDateString(timeZone);
        Optional<Attendance> existingAtt = attendanceRepository.findByUserIdAndDate(userId, date);
        if (existingAtt.isEmpty()) {
            // Find most recent attendance log
            List<Attendance> history = attendanceRepository.findByUserIdOrderByDateDesc(userId);
            if (!history.isEmpty()) {
                existingAtt = Optional.of(history.get(0));
            }
        }

        if (existingAtt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No attendance record found to clock out."));
        }

        Attendance attendance = existingAtt.get();
        // Calculate break durations
        long breakMillis = 0;
        if (attendance.getBreaks() != null) {
            // Check if there is an active break, close it
            for (BreakSession bs : attendance.getBreaks()) {
                if (bs.getBreakOut() == null) {
                    bs.setBreakOut(System.currentTimeMillis());
                    bs.setDurationHours((bs.getBreakOut() - bs.getBreakIn()) / 3600000.0);
                }
                breakMillis += (bs.getBreakOut() - bs.getBreakIn());
            }
        }

        attendance.setSignOutTime(System.currentTimeMillis());
        attendance.setStatus("SIGNED_OUT");
        attendance.setLastActivity(System.currentTimeMillis());

        long workMillis = attendance.getSignOutTime() - attendance.getSignInTime() - breakMillis;
        double hours = workMillis / 3600000.0;
        attendance.setTotalWorkingHours(Math.max(0.0, Math.round(hours * 100.0) / 100.0));
        attendance.getAuditLogs().add(new AuditLog(System.currentTimeMillis(), "SIGN_OUT", "staff", "Clocked out via Staff Portal"));

        attendanceRepository.save(attendance);

        user.setAttendanceStatus("SIGNED_OUT");
        user.setOnlineStatus("OFFLINE");
        userRepository.save(user);

        // Broadcast websocket event
        broadcastAttendanceUpdate(userId, user, attendance);

        return ResponseEntity.ok(attendance);
    }

    @PostMapping("/attendance/breakin")
    public ResponseEntity<?> breakIn(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        String timeZone = body.get("timeZone");

        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
        }
        User user = userOpt.get();

        String date = getTodayDateString(timeZone);
        Optional<Attendance> existingAtt = attendanceRepository.findByUserIdAndDate(userId, date);
        if (existingAtt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Must clock in first."));
        }

        Attendance attendance = existingAtt.get();
        if (attendance.getBreaks() == null) {
            attendance.setBreaks(new ArrayList<>());
        }

        // Check if already on break
        boolean onBreak = attendance.getBreaks().stream().anyMatch(b -> b.getBreakOut() == null);
        if (!onBreak) {
            BreakSession bs = new BreakSession();
            bs.setBreakIn(System.currentTimeMillis());
            attendance.getBreaks().add(bs);
        }

        attendance.setStatus("BREAK_IN");
        attendance.setLastActivity(System.currentTimeMillis());
        attendance.getAuditLogs().add(new AuditLog(System.currentTimeMillis(), "BREAK_IN", "staff", "Started break"));

        attendanceRepository.save(attendance);

        user.setAttendanceStatus("BREAK_IN");
        user.setOnlineStatus("AWAY");
        userRepository.save(user);

        broadcastAttendanceUpdate(userId, user, attendance);

        return ResponseEntity.ok(attendance);
    }

    @PostMapping("/attendance/breakout")
    public ResponseEntity<?> breakOut(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        String timeZone = body.get("timeZone");

        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
        }
        User user = userOpt.get();

        String date = getTodayDateString(timeZone);
        Optional<Attendance> existingAtt = attendanceRepository.findByUserIdAndDate(userId, date);
        if (existingAtt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "No attendance record found."));
        }

        Attendance attendance = existingAtt.get();
        if (attendance.getBreaks() != null) {
            for (BreakSession bs : attendance.getBreaks()) {
                if (bs.getBreakOut() == null) {
                    bs.setBreakOut(System.currentTimeMillis());
                    bs.setDurationHours((bs.getBreakOut() - bs.getBreakIn()) / 3600000.0);
                }
            }
        }

        attendance.setStatus("SIGNED_IN");
        attendance.setLastActivity(System.currentTimeMillis());
        attendance.getAuditLogs().add(new AuditLog(System.currentTimeMillis(), "BREAK_OUT", "staff", "Ended break"));

        attendanceRepository.save(attendance);

        user.setAttendanceStatus("SIGNED_IN");
        user.setOnlineStatus("ONLINE");
        userRepository.save(user);

        broadcastAttendanceUpdate(userId, user, attendance);

        return ResponseEntity.ok(attendance);
    }

    @GetMapping("/attendance/today")
    public ResponseEntity<?> getTodayAttendance(@RequestParam String userId, @RequestParam(required = false) String timeZone) {
        String date = getTodayDateString(timeZone);
        Optional<Attendance> existingAtt = attendanceRepository.findByUserIdAndDate(userId, date);
        return ResponseEntity.ok(existingAtt.orElse(null));
    }

    @GetMapping("/attendance/history")
    public ResponseEntity<?> getAttendanceHistory(@RequestParam String userId) {
        List<Attendance> history = attendanceRepository.findByUserIdOrderByDateDesc(userId);
        return ResponseEntity.ok(history);
    }

    @PostMapping("/status")
    public ResponseEntity<?> changePresenceStatus(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        String status = body.get("status"); // ONLINE, AWAY, BUSY, OFFLINE

        if (userId == null || status == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId and status are required"));
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
        }
        User user = userOpt.get();
        user.setOnlineStatus(status.toUpperCase());
        userRepository.save(user);

        // Notify via Websocket
        chatWebSocketHandler.setUserStatus(userId, status.toLowerCase());

        return ResponseEntity.ok(Map.of("success", true, "status", status));
    }

    // --- ADMIN PORTAL ATTENDANCE ENDPOINTS ---

    @GetMapping("/attendance/live")
    public ResponseEntity<?> getLiveAttendance() {
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> liveList = new ArrayList<>();
        String date = LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE);

        for (User u : users) {
            if ("STAFF".equalsIgnoreCase(u.getRole()) || "ADMIN".equalsIgnoreCase(u.getRole())) {
                Optional<Attendance> attOpt = attendanceRepository.findByUserIdAndDate(u.getId(), date);
                Map<String, Object> map = new HashMap<>();
                map.put("userId", u.getId());
                map.put("name", (u.getFirstName() + " " + u.getLastName()).trim());
                map.put("email", u.getEmail());
                map.put("employeeId", u.getEmployeeId() != null ? u.getEmployeeId() : "GLOB-STF-TBD");
                map.put("designation", u.getDesignation() != null ? u.getDesignation() : "Compliance Officer");
                map.put("department", u.getDepartment() != null ? u.getDepartment() : "Operations");
                map.put("onlineStatus", u.getOnlineStatus() != null ? u.getOnlineStatus() : "OFFLINE");
                map.put("attendanceStatus", u.getAttendanceStatus() != null ? u.getAttendanceStatus() : "SIGNED_OUT");
                map.put("lastSeen", u.getLastSeenTime() != null ? u.getLastSeenTime() : System.currentTimeMillis());

                if (attOpt.isPresent()) {
                    Attendance att = attOpt.get();
                    map.put("signInTime", att.getSignInTime());
                    map.put("signOutTime", att.getSignOutTime());
                    map.put("totalWorkingHours", att.getTotalWorkingHours());
                    map.put("lastActivity", att.getLastActivity());
                    map.put("attendanceRecordId", att.getId());
                } else {
                    map.put("signInTime", null);
                    map.put("signOutTime", null);
                    map.put("totalWorkingHours", 0.0);
                    map.put("lastActivity", null);
                    map.put("attendanceRecordId", null);
                }

                liveList.add(map);
            }
        }
        return ResponseEntity.ok(liveList);
    }

    @PostMapping("/attendance/correct")
    public ResponseEntity<?> correctAttendance(@RequestBody Map<String, Object> body) {
        String attendanceId = (String) body.get("attendanceId");
        String userId = (String) body.get("userId");
        String date = (String) body.get("date");
        Number signInVal = (Number) body.get("signInTime");
        Number signOutVal = (Number) body.get("signOutTime");
        String reason = (String) body.get("reason");
        String correctedBy = (String) body.get("correctedBy"); // e.g. "admin"

        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
        }
        User user = userOpt.get();

        Attendance attendance;
        if (attendanceId != null) {
            Optional<Attendance> attOpt = attendanceRepository.findById(attendanceId);
            if (attOpt.isEmpty()) return ResponseEntity.notFound().build();
            attendance = attOpt.get();
        } else {
            // Find or create by user & date
            if (date == null) date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            Optional<Attendance> attOpt = attendanceRepository.findByUserIdAndDate(userId, date);
            if (attOpt.isPresent()) {
                attendance = attOpt.get();
            } else {
                attendance = new Attendance();
                attendance.setId("att-" + System.currentTimeMillis() + "-" + (int)(Math.random()*1000));
                attendance.setUserId(userId);
                attendance.setDate(date);
                attendance.setStatus("SIGNED_OUT");
            }
        }

        Long signInTime = signInVal != null ? signInVal.longValue() : null;
        Long signOutTime = signOutVal != null ? signOutVal.longValue() : null;

        if (signInTime != null) attendance.setSignInTime(signInTime);
        if (signOutTime != null) attendance.setSignOutTime(signOutTime);

        // Recalculate working hours
        if (attendance.getSignInTime() != null && attendance.getSignOutTime() != null) {
            long breakMillis = 0;
            if (attendance.getBreaks() != null) {
                for (BreakSession bs : attendance.getBreaks()) {
                    if (bs.getBreakOut() != null) {
                        breakMillis += (bs.getBreakOut() - bs.getBreakIn());
                    }
                }
            }
            double hours = (attendance.getSignOutTime() - attendance.getSignInTime() - breakMillis) / 3600000.0;
            attendance.setTotalWorkingHours(Math.max(0.0, Math.round(hours * 100.0) / 100.0));
        }

        if (attendance.getAuditLogs() == null) {
            attendance.setAuditLogs(new ArrayList<>());
        }
        attendance.getAuditLogs().add(new AuditLog(
                System.currentTimeMillis(),
                "MANUAL_CORRECTION",
                correctedBy != null ? correctedBy : "admin",
                "Manually corrected logs. Reason: " + (reason != null ? reason : "No reason provided")
        ));

        attendanceRepository.save(attendance);

        // Sync and broadcast
        broadcastAttendanceUpdate(userId, user, attendance);

        return ResponseEntity.ok(attendance);
    }

    @GetMapping("/attendance/dashboard-widgets")
    public ResponseEntity<?> getDashboardWidgets() {
        List<User> users = userRepository.findAll();
        String date = LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE);
        
        int totalStaff = 0;
        int presentToday = 0;
        int onlineStaff = 0;
        int lateSignIn = 0;
        int earlySignOut = 0;
        double totalHours = 0.0;

        for (User u : users) {
            if ("STAFF".equalsIgnoreCase(u.getRole())) {
                totalStaff++;
                if (u.getOnlineStatus() != null && !"OFFLINE".equalsIgnoreCase(u.getOnlineStatus())) {
                    onlineStaff++;
                }

                Optional<Attendance> attOpt = attendanceRepository.findByUserIdAndDate(u.getId(), date);
                if (attOpt.isPresent()) {
                    presentToday++;
                    Attendance att = attOpt.get();
                    totalHours += att.getTotalWorkingHours();

                    // Late Check (Late is defined as signed in after 9:15 AM)
                    if (att.getSignInTime() != null) {
                        Calendar cal = Calendar.getInstance();
                        cal.setTimeInMillis(att.getSignInTime());
                        int hour = cal.get(Calendar.HOUR_OF_DAY);
                        int min = cal.get(Calendar.MINUTE);
                        if (hour > 9 || (hour == 9 && min > 15)) {
                            lateSignIn++;
                        }
                    }

                    // Early Sign Out check (clocked out before 5:00 PM)
                    if (att.getSignOutTime() != null) {
                        Calendar cal = Calendar.getInstance();
                        cal.setTimeInMillis(att.getSignOutTime());
                        int hour = cal.get(Calendar.HOUR_OF_DAY);
                        if (hour < 17) {
                            earlySignOut++;
                        }
                    }
                }
            }
        }

        Map<String, Object> map = new HashMap<>();
        map.put("totalStaff", totalStaff);
        map.put("presentToday", presentToday);
        map.put("onlineStaff", onlineStaff);
        map.put("lateSignIn", lateSignIn);
        map.put("earlySignOut", earlySignOut);
        map.put("totalWorkingHours", Math.round(totalHours * 100.0) / 100.0);
        map.put("absentToday", Math.max(0, totalStaff - presentToday));

        return ResponseEntity.ok(map);
    }

    // --- STAFF ID CARDS ENDPOINTS ---

    @GetMapping("/staff-cards")
    public ResponseEntity<?> getStaffCards() {
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> cards = new ArrayList<>();
        int index = 1001;

        for (User u : users) {
            if ("STAFF".equalsIgnoreCase(u.getRole()) || "ADMIN".equalsIgnoreCase(u.getRole())) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", u.getId());
                map.put("name", (u.getFirstName() + " " + u.getLastName()).trim());
                map.put("email", u.getEmail());
                
                // Fallback auto-issue logic if values not set
                if (u.getEmployeeId() == null) u.setEmployeeId("GLOB-STF-" + index);
                if (u.getDesignation() == null) u.setDesignation("Compliance Officer");
                if (u.getDepartment() == null) u.setDepartment("AML Operations");
                if (u.getCardStatus() == null) u.setCardStatus("ACTIVE");
                
                if (u.getCardIssueDate() == null) {
                    u.setCardIssueDate(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
                }
                if (u.getCardValidUntil() == null) {
                    u.setCardValidUntil(LocalDate.now().plusYears(2).format(DateTimeFormatter.ISO_LOCAL_DATE));
                }

                map.put("employeeId", u.getEmployeeId());
                map.put("designation", u.getDesignation());
                map.put("department", u.getDepartment());
                map.put("staffPhoto", u.getStaffPhoto());
                map.put("cardStatus", u.getCardStatus());
                map.put("cardIssueDate", u.getCardIssueDate());
                map.put("cardValidUntil", u.getCardValidUntil());
                cards.add(map);
                index++;
            }
        }
        return ResponseEntity.ok(cards);
    }

    @GetMapping("/staff-cards/{id}")
    public ResponseEntity<?> getStaffCardById(@PathVariable String id) {
        Optional<User> uOpt = userRepository.findById(id);
        if (uOpt.isEmpty() || (!"STAFF".equalsIgnoreCase(uOpt.get().getRole()) && !"ADMIN".equalsIgnoreCase(uOpt.get().getRole()))) {
            return ResponseEntity.notFound().build();
        }
        User u = uOpt.get();
        if (u.getEmployeeId() == null) u.setEmployeeId("GLOB-STF-TBD");
        if (u.getDesignation() == null) u.setDesignation("Compliance Officer");
        if (u.getDepartment() == null) u.setDepartment("AML Operations");
        if (u.getCardStatus() == null) u.setCardStatus("ACTIVE");
        
        return ResponseEntity.ok(u);
    }

    @PutMapping("/staff-cards/{id}")
    public ResponseEntity<?> updateStaffCard(@PathVariable String id, @RequestBody Map<String, String> body) {
        Optional<User> uOpt = userRepository.findById(id);
        if (uOpt.isEmpty()) return ResponseEntity.notFound().build();
        User u = uOpt.get();

        if (body.containsKey("employeeId")) u.setEmployeeId(body.get("employeeId"));
        if (body.containsKey("designation")) u.setDesignation(body.get("designation"));
        if (body.containsKey("department")) u.setDepartment(body.get("department"));
        if (body.containsKey("staffPhoto")) u.setStaffPhoto(body.get("staffPhoto"));
        if (body.containsKey("cardIssueDate")) u.setCardIssueDate(body.get("cardIssueDate"));
        if (body.containsKey("cardValidUntil")) u.setCardValidUntil(body.get("cardValidUntil"));
        if (body.containsKey("cardStatus")) u.setCardStatus(body.get("cardStatus"));

        userRepository.save(u);

        // Broadcast to WebSocket clients
        Map<String, Object> event = new HashMap<>();
        event.put("type", "card_status_update");
        event.put("userId", id);
        event.put("cardStatus", u.getCardStatus());
        chatWebSocketHandler.broadcastEvent(event);

        return ResponseEntity.ok(u);
    }

    @PutMapping("/staff-cards/status/{id}")
    public ResponseEntity<?> toggleCardStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        Optional<User> uOpt = userRepository.findById(id);
        if (uOpt.isEmpty()) return ResponseEntity.notFound().build();
        User u = uOpt.get();

        String status = body.get("status");
        if (status != null) {
            u.setCardStatus(status.toUpperCase());
            userRepository.save(u);

            // Broadcast to WebSocket clients
            Map<String, Object> event = new HashMap<>();
            event.put("type", "card_status_update");
            event.put("userId", id);
            event.put("cardStatus", u.getCardStatus());
            chatWebSocketHandler.broadcastEvent(event);
        }

        return ResponseEntity.ok(u);
    }

    @PostMapping("/staff-cards/reissue/{id}")
    public ResponseEntity<?> reissueCard(@PathVariable String id) {
        Optional<User> uOpt = userRepository.findById(id);
        if (uOpt.isEmpty()) return ResponseEntity.notFound().build();
        User u = uOpt.get();

        u.setCardIssueDate(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        u.setCardValidUntil(LocalDate.now().plusYears(2).format(DateTimeFormatter.ISO_LOCAL_DATE));
        u.setCardStatus("ACTIVE");
        userRepository.save(u);

        // Broadcast to WebSocket clients
        Map<String, Object> event = new HashMap<>();
        event.put("type", "card_status_update");
        event.put("userId", id);
        event.put("cardStatus", u.getCardStatus());
        chatWebSocketHandler.broadcastEvent(event);

        return ResponseEntity.ok(u);
    }

    // --- PUBLIC VERIFICATION API ---

    @GetMapping("/verify/{id}")
    public ResponseEntity<?> verifyStaff(@PathVariable String id) {
        Optional<User> uOpt = userRepository.findById(id);
        if (uOpt.isEmpty() || (!"STAFF".equalsIgnoreCase(uOpt.get().getRole()) && !"ADMIN".equalsIgnoreCase(uOpt.get().getRole()))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Staff profile not found."));
        }
        User u = uOpt.get();

        Map<String, Object> response = new HashMap<>();
        response.put("name", (u.getFirstName() + " " + u.getLastName()).trim());
        response.put("employeeId", u.getEmployeeId() != null ? u.getEmployeeId() : "GLOB-STF-TBD");
        response.put("designation", u.getDesignation() != null ? u.getDesignation() : "Compliance Officer");
        response.put("department", u.getDepartment() != null ? u.getDepartment() : "Operations");
        response.put("staffPhoto", u.getStaffPhoto());
        response.put("cardIssueDate", u.getCardIssueDate());
        response.put("cardValidUntil", u.getCardValidUntil());
        response.put("cardStatus", u.getCardStatus() != null ? u.getCardStatus() : "ACTIVE");

        return ResponseEntity.ok(response);
    }

    // --- HELPER METHOD TO BROADCAST WEBSOCKET UPDATES ---

    private void broadcastAttendanceUpdate(String userId, User user, Attendance att) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "attendance_update");
            event.put("userId", userId);
            event.put("name", (user.getFirstName() + " " + user.getLastName()).trim());
            event.put("attendanceStatus", user.getAttendanceStatus());
            event.put("onlineStatus", user.getOnlineStatus());
            event.put("signInTime", att.getSignInTime());
            event.put("signOutTime", att.getSignOutTime());
            event.put("totalWorkingHours", att.getTotalWorkingHours());
            event.put("lastActivity", att.getLastActivity());
            event.put("attendanceRecordId", att.getId());
            chatWebSocketHandler.broadcastEvent(event);
        } catch (Exception e) {
            // ignore
        }
    }
}
