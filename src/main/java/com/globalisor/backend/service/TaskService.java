package com.globalisor.backend.service;

import com.globalisor.backend.model.Task;
import com.globalisor.backend.repository.TaskRepository;
import com.globalisor.backend.repository.UserRepository;
import com.globalisor.backend.repository.OnboardingRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TaskService {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired(required = false)
    private UserRepository userRepository;

    @Autowired(required = false)
    private OnboardingRepository onboardingRepository;

    @Autowired
    private NotificationService notificationService;

    @PostConstruct
    public void seedDefaultTasks() {
        if (taskRepository.count() == 0) {
            long now = System.currentTimeMillis();
            
            Task t1 = Task.builder()
                    .ticketNumber("TSK-1001")
                    .clientId("C-1001")
                    .clientName("Ethan Tan")
                    .clientEmail("ethan@lionpath.sg")
                    .companyName("ABBEY HOLDINGS PTE LTD")
                    .title("Change Registered Office Address to Marina Bay")
                    .description("We are relocating our corporate office from Tanjong Pagar to Marina Bay Financial Centre Tower 2. Please prepare directors resolution in writing and lodge with ACRA.")
                    .type("CHANGE")
                    .category("Registered Address Change")
                    .priority("HIGH")
                    .status("IN_PROGRESS")
                    .dueDate("2026-09-20")
                    .assignedTo(Task.UserRef.builder().id("usr-staff").name("Sarah Lim").role("STAFF").avatar("SL").email("staff@globalisor.com").build())
                    .createdBy(Task.UserRef.builder().id("C-1001").name("Ethan Tan").role("CLIENT").build())
                    .createdAt(now - 86400000L * 2)
                    .updatedAt(now - 3600000L * 5)
                    .comments(new ArrayList<>(List.of(
                            Task.Comment.builder()
                                    .id("c-1")
                                    .authorName("Ethan Tan")
                                    .authorRole("CLIENT")
                                    .authorAvatar("ET")
                                    .text("Please find our signed lease agreement attached.")
                                    .timestamp(now - 86400000L)
                                    .isInternal(false)
                                    .build(),
                            Task.Comment.builder()
                                    .id("c-2")
                                    .authorName("Sarah Lim")
                                    .authorRole("STAFF")
                                    .authorAvatar("SL")
                                    .text("Received. Drafted the board resolution and sending for e-signatures.")
                                    .timestamp(now - 3600000L * 5)
                                    .isInternal(false)
                                    .build()
                    )))
                    .activityLog(new ArrayList<>(List.of(
                            Task.ActivityLog.builder().id("act-1").action("CREATED").details("Task created by client").performedBy("Ethan Tan").performedByRole("CLIENT").timestamp(now - 86400000L * 2).build()
                    )))
                    .build();

            Task t2 = Task.builder()
                    .ticketNumber("TSK-1002")
                    .clientId("C-1001")
                    .clientName("Priya Sharma")
                    .clientEmail("priya@merlion.sg")
                    .companyName("ABBEY HOLDINGS PTE LTD")
                    .title("Corporate Income Tax (Form C-S) Estimation Query")
                    .description("We would like to clarify if our recent cross-border SaaS subscription revenue qualifies for the startup tax exemption scheme under YA2026.")
                    .type("QUERY")
                    .category("Tax & Accounting")
                    .priority("MEDIUM")
                    .status("WAITING_CLIENT_INPUT")
                    .dueDate("2026-09-25")
                    .assignedTo(Task.UserRef.builder().id("usr-admin").name("Admin User").role("ADMIN").avatar("AU").email("admin@globalisor.com").build())
                    .createdBy(Task.UserRef.builder().id("C-1001").name("Priya Sharma").role("CLIENT").build())
                    .createdAt(now - 86400000L * 3)
                    .updatedAt(now - 3600000L * 12)
                    .comments(new ArrayList<>(List.of(
                            Task.Comment.builder()
                                    .id("c-3")
                                    .authorName("Admin User")
                                    .authorRole("ADMIN")
                                    .authorAvatar("AU")
                                    .text("To evaluate your startup tax exemption eligibility, could you upload your draft balance sheet and shareholder registry?")
                                    .timestamp(now - 3600000L * 12)
                                    .isInternal(false)
                                    .build()
                    )))
                    .activityLog(new ArrayList<>(List.of(
                            Task.ActivityLog.builder().id("act-2").action("CREATED").details("Query submitted").performedBy("Priya Sharma").performedByRole("CLIENT").timestamp(now - 86400000L * 3).build()
                    )))
                    .build();

            Task t3 = Task.builder()
                    .ticketNumber("TSK-1003")
                    .clientId("C-1001")
                    .clientName("Ravi Kumar")
                    .clientEmail("ravi@harbouredge.sg")
                    .companyName("ABBEY HOLDINGS PTE LTD")
                    .title("Appointment of Additional Resident Director")
                    .description("Urgent request: We are onboarding a new local director (Singapore Citizen). Need Form 45 prepared, identity verified via Singpass KYC, and ACRA notification lodged.")
                    .type("CHANGE")
                    .category("Director / Shareholder Change")
                    .priority("URGENT")
                    .status("PENDING")
                    .dueDate("2026-09-15")
                    .createdBy(Task.UserRef.builder().id("C-1001").name("Ravi Kumar").role("CLIENT").build())
                    .createdAt(now - 3600000L * 4)
                    .updatedAt(now - 3600000L * 4)
                    .comments(new ArrayList<>())
                    .activityLog(new ArrayList<>(List.of(
                            Task.ActivityLog.builder().id("act-3").action("CREATED").details("Urgent appointment request logged").performedBy("Ravi Kumar").performedByRole("CLIENT").timestamp(now - 3600000L * 4).build()
                    )))
                    .build();

            taskRepository.saveAll(List.of(t1, t2, t3));
        } else {
            // Sanitize any existing tasks with placeholder LionPath company name to Abbey Holdings
            List<Task> allTasks = taskRepository.findAll();
            boolean changed = false;
            for (Task t : allTasks) {
                if (t.getCompanyName() != null && t.getCompanyName().contains("LionPath")) {
                    t.setCompanyName("ABBEY HOLDINGS PTE LTD");
                    changed = true;
                }
            }
            if (changed) {
                taskRepository.saveAll(allTasks);
            }
        }
    }

    public Task updateTask(String id, Task updated) {
        Task existing = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + id));

        if (updated.getTitle() != null) existing.setTitle(updated.getTitle());
        if (updated.getDescription() != null) existing.setDescription(updated.getDescription());
        if (updated.getCategory() != null) existing.setCategory(updated.getCategory());
        if (updated.getType() != null) existing.setType(updated.getType());
        if (updated.getPriority() != null) existing.setPriority(updated.getPriority());
        if (updated.getStatus() != null) existing.setStatus(updated.getStatus());
        if (updated.getCompanyName() != null) existing.setCompanyName(updated.getCompanyName());
        if (updated.getClientName() != null) existing.setClientName(updated.getClientName());
        if (updated.getClientEmail() != null) existing.setClientEmail(updated.getClientEmail());
        if (updated.getDueDate() != null) existing.setDueDate(updated.getDueDate());
        if (updated.getAssignedTo() != null) existing.setAssignedTo(updated.getAssignedTo());
        existing.setUpdatedAt(System.currentTimeMillis());

        return taskRepository.save(existing);
    }

    public List<Task> getAllTasks(String status, String priority, String category, String clientId, String companyName, String assignedToId) {
        List<Task> all = taskRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        return all.stream().filter(task -> {
            if (status != null && !status.isEmpty() && !status.equalsIgnoreCase("ALL") && !status.equalsIgnoreCase(task.getStatus())) {
                return false;
            }
            if (priority != null && !priority.isEmpty() && !priority.equalsIgnoreCase("ALL") && !priority.equalsIgnoreCase(task.getPriority())) {
                return false;
            }
            if (category != null && !category.isEmpty() && !category.equalsIgnoreCase("ALL") && !category.equalsIgnoreCase(task.getCategory())) {
                return false;
            }
            if (clientId != null && !clientId.isEmpty() && !clientId.equalsIgnoreCase("ALL") && !clientId.equalsIgnoreCase(task.getClientId())) {
                return false;
            }
            if (companyName != null && !companyName.isEmpty() && !companyName.equalsIgnoreCase("ALL")) {
                if (task.getCompanyName() == null || !task.getCompanyName().trim().equalsIgnoreCase(companyName.trim())) {
                    return false;
                }
            }
            if (assignedToId != null && !assignedToId.isEmpty()) {
                if (assignedToId.equalsIgnoreCase("UNASSIGNED")) {
                    if (task.getAssignedTo() != null && task.getAssignedTo().getId() != null) return false;
                } else if (task.getAssignedTo() == null || !assignedToId.equals(task.getAssignedTo().getId())) {
                    return false;
                }
            }
            return true;
        }).toList();
    }

    public Optional<Task> getTaskById(String id) {
        return taskRepository.findById(id);
    }

    public Optional<Task> getTaskByTicketNumber(String ticketNumber) {
        return taskRepository.findByTicketNumber(ticketNumber);
    }

    public Task createTask(Task task) {
        long count = taskRepository.count();
        if (task.getTicketNumber() == null || task.getTicketNumber().isEmpty()) {
            task.setTicketNumber("TSK-" + (1000 + count + 1));
        }
        
        long now = System.currentTimeMillis();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        
        if (task.getStatus() == null || task.getStatus().isEmpty()) {
            task.setStatus("PENDING");
        }
        if (task.getPriority() == null || task.getPriority().isEmpty()) {
            task.setPriority("MEDIUM");
        }
        if (task.getType() == null || task.getType().isEmpty()) {
            task.setType("REQUEST");
        }
        if (task.getAttachments() == null) {
            task.setAttachments(new ArrayList<>());
        }
        if (task.getComments() == null) {
            task.setComments(new ArrayList<>());
        }
        if (task.getActivityLog() == null) {
            task.setActivityLog(new ArrayList<>());
        }

        String creatorName = task.getCreatedBy() != null ? task.getCreatedBy().getName() : "Client";
        String creatorRole = task.getCreatedBy() != null ? task.getCreatedBy().getRole() : "CLIENT";

        task.getActivityLog().add(Task.ActivityLog.builder()
                .id("act-" + UUID.randomUUID())
                .action("CREATED")
                .details("Task created: " + task.getTitle())
                .performedBy(creatorName)
                .performedByRole(creatorRole)
                .timestamp(now)
                .build());

        Task saved = taskRepository.save(task);

        // Notify client
        try {
            if (task.getClientId() != null) {
                notificationService.sendNotification(
                        task.getClientId(),
                        "Task Raised: " + task.getTicketNumber(),
                        "Your request '" + task.getTitle() + "' has been submitted successfully.",
                        "TASK_CREATED",
                        saved.getId(),
                        "Info",
                        "/client/portal.html?tab=tasks"
                );
            }
        } catch (Exception e) {
            // Ignore notification failure during creation
        }

        // Notify admin & staff for real-time toast notification
        try {
            String clientLabel = saved.getClientName() != null ? saved.getClientName() : "Client";
            if (saved.getCompanyName() != null && !saved.getCompanyName().isEmpty()) {
                clientLabel += " (" + saved.getCompanyName() + ")";
            }
            notificationService.sendNotification(
                    "admin",
                    "New Task Raised: " + saved.getTicketNumber(),
                    clientLabel + " raised a new task: " + saved.getTitle(),
                    "TASK_CREATED",
                    saved.getId(),
                    "High",
                    "tasks.html?id=" + saved.getId()
            );
        } catch (Exception e) {
            // Ignore notification failure
        }

        return saved;
    }

    public Task assignTask(String taskId, Task.UserRef assignee, String performedByName, String performedByRole) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + taskId));

        long now = System.currentTimeMillis();
        task.setAssignedTo(assignee);
        if ("PENDING".equals(task.getStatus())) {
            task.setStatus("ASSIGNED");
        }
        task.setUpdatedAt(now);

        String details = assignee != null && assignee.getName() != null 
                ? "Task assigned to " + assignee.getName() + " (" + assignee.getRole() + ")"
                : "Task unassigned";

        task.getActivityLog().add(Task.ActivityLog.builder()
                .id("act-" + UUID.randomUUID())
                .action("ASSIGNED")
                .details(details)
                .performedBy(performedByName != null ? performedByName : "Admin")
                .performedByRole(performedByRole != null ? performedByRole : "ADMIN")
                .timestamp(now)
                .build());

        Task saved = taskRepository.save(task);

        // Notify assigned staff
        if (assignee != null && assignee.getId() != null) {
            try {
                notificationService.sendNotification(
                        assignee.getId(),
                        "New Task Assigned: " + task.getTicketNumber(),
                        "You have been assigned to handle: " + task.getTitle(),
                        "TASK_ASSIGNED",
                        task.getId(),
                        "Warning"
                );
            } catch (Exception ignored) {}
        }

        // Notify client that someone is working on their task
        if (task.getClientId() != null && assignee != null) {
            try {
                notificationService.sendNotification(
                        task.getClientId(),
                        "Task Assigned: " + task.getTicketNumber(),
                        "Your request is now assigned to specialist " + assignee.getName() + ".",
                        "TASK_STATUS",
                        task.getId(),
                        "Info"
                );
            } catch (Exception ignored) {}
        }

        return saved;
    }

    public Task updateStatus(String taskId, String newStatus, String resolutionNotes, String performedByName, String performedByRole) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + taskId));

        long now = System.currentTimeMillis();
        String oldStatus = task.getStatus();
        task.setStatus(newStatus);
        task.setUpdatedAt(now);

        if ("COMPLETED".equalsIgnoreCase(newStatus) || "RESOLVED".equalsIgnoreCase(newStatus)) {
            task.setResolvedAt(now);
            if (resolutionNotes != null && !resolutionNotes.isEmpty()) {
                task.setResolutionNotes(resolutionNotes);
            }
        }

        task.getActivityLog().add(Task.ActivityLog.builder()
                .id("act-" + UUID.randomUUID())
                .action("STATUS_CHANGED")
                .details("Status changed from " + oldStatus + " to " + newStatus + (resolutionNotes != null ? ". Notes: " + resolutionNotes : ""))
                .performedBy(performedByName != null ? performedByName : "System")
                .performedByRole(performedByRole != null ? performedByRole : "STAFF")
                .timestamp(now)
                .build());

        Task saved = taskRepository.save(task);

        // Notify client of status change
        if (task.getClientId() != null) {
            try {
                notificationService.sendNotification(
                        task.getClientId(),
                        "Task Status Updated: " + task.getTicketNumber(),
                        "Status of '" + task.getTitle() + "' is now " + newStatus + ".",
                        "TASK_STATUS",
                        task.getId(),
                        "COMPLETED".equalsIgnoreCase(newStatus) ? "Info" : "Warning"
                );
            } catch (Exception ignored) {}
        }

        return saved;
    }

    public Task addComment(String taskId, Task.Comment comment) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + taskId));

        long now = System.currentTimeMillis();
        if (comment.getId() == null || comment.getId().isEmpty()) {
            comment.setId("cmt-" + UUID.randomUUID());
        }
        comment.setTimestamp(now);
        if (comment.getIsInternal() == null) {
            comment.setIsInternal(false);
        }

        if (task.getComments() == null) {
            task.setComments(new ArrayList<>());
        }
        task.getComments().add(comment);
        task.setUpdatedAt(now);

        // If client commented, set status back to IN_PROGRESS if it was WAITING_CLIENT_INPUT
        if ("CLIENT".equalsIgnoreCase(comment.getAuthorRole()) && "WAITING_CLIENT_INPUT".equalsIgnoreCase(task.getStatus())) {
            task.setStatus("IN_PROGRESS");
        }

        task.getActivityLog().add(Task.ActivityLog.builder()
                .id("act-" + UUID.randomUUID())
                .action("COMMENTED")
                .details((comment.getIsInternal() ? "[Internal Note] " : "") + comment.getAuthorName() + " replied.")
                .performedBy(comment.getAuthorName())
                .performedByRole(comment.getAuthorRole())
                .timestamp(now)
                .build());

        Task saved = taskRepository.save(task);

        // Notify relevant party
        try {
            if (!comment.getIsInternal()) {
                if ("CLIENT".equalsIgnoreCase(comment.getAuthorRole())) {
                    if (task.getAssignedTo() != null && task.getAssignedTo().getId() != null) {
                        notificationService.sendNotification(
                                task.getAssignedTo().getId(),
                                "New Client Message: " + task.getTicketNumber(),
                                comment.getAuthorName() + ": " + comment.getText(),
                                "TASK_COMMENT",
                                task.getId(),
                                "Info",
                                "tasks.html?id=" + task.getId()
                        );
                    }
                    notificationService.sendNotification(
                            "admin",
                            "New Client Message: " + task.getTicketNumber(),
                            comment.getAuthorName() + " (" + (task.getCompanyName() != null ? task.getCompanyName() : "Client") + "): " + comment.getText(),
                            "TASK_COMMENT",
                            task.getId(),
                            "Info",
                            "tasks.html?id=" + task.getId()
                    );
                } else {
                    if (task.getClientId() != null) {
                        notificationService.sendNotification(
                                task.getClientId(),
                                "New Update: " + task.getTicketNumber(),
                                comment.getAuthorName() + " responded to your task.",
                                "TASK_COMMENT",
                                task.getId(),
                                "Info"
                        );
                    }
                }
            }
        } catch (Exception ignored) {}

        return saved;
    }

    public Map<String, Object> getTaskStats() {
        List<Task> all = taskRepository.findAll();
        long total = all.size();
        long pending = all.stream().filter(t -> "PENDING".equalsIgnoreCase(t.getStatus())).count();
        long assigned = all.stream().filter(t -> "ASSIGNED".equalsIgnoreCase(t.getStatus())).count();
        long inProgress = all.stream().filter(t -> "IN_PROGRESS".equalsIgnoreCase(t.getStatus())).count();
        long waitingClient = all.stream().filter(t -> "WAITING_CLIENT_INPUT".equalsIgnoreCase(t.getStatus())).count();
        long completed = all.stream().filter(t -> "COMPLETED".equalsIgnoreCase(t.getStatus()) || "RESOLVED".equalsIgnoreCase(t.getStatus())).count();
        long urgent = all.stream().filter(t -> "URGENT".equalsIgnoreCase(t.getPriority()) && !"COMPLETED".equalsIgnoreCase(t.getStatus())).count();
        long unassigned = all.stream().filter(t -> t.getAssignedTo() == null || t.getAssignedTo().getId() == null).count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("assigned", assigned);
        stats.put("inProgress", inProgress);
        stats.put("waitingClient", waitingClient);
        stats.put("completed", completed);
        stats.put("urgent", urgent);
        stats.put("unassigned", unassigned);
        return stats;
    }

    public Map<String, Object> getTaskFormOptions() {
        Map<String, Map<String, Object>> entityMap = new LinkedHashMap<>();

        // 1. Collect entities from Onboarding
        if (onboardingRepository != null) {
            try {
                onboardingRepository.findAll().forEach(o -> {
                    String compName = o.getClientName();
                    // Check if company details are available or client name
                    if (compName != null && !compName.trim().isEmpty() && !"N/A".equalsIgnoreCase(compName.trim())) {
                        String key = compName.trim().toUpperCase();
                        if (!entityMap.containsKey(key)) {
                            Map<String, Object> item = new HashMap<>();
                            item.put("companyId", o.getId() != null ? o.getId() : "COMP-" + o.getClientId());
                            item.put("companyName", compName.trim());
                            item.put("clientId", o.getClientId() != null ? o.getClientId() : "C-1001");
                            item.put("clientName", o.getClientName() != null ? o.getClientName() : "Client User");
                            item.put("clientEmail", o.getClientEmail() != null ? o.getClientEmail() : "client@globalisor.com");
                            entityMap.put(key, item);
                        }
                    }
                });
            } catch (Exception ignored) {}
        }

        // 2. Collect entities from Users
        if (userRepository != null) {
            try {
                userRepository.findAll().forEach(u -> {
                    String compName = u.getCompanyName();
                    if (compName != null && !compName.trim().isEmpty() && !"N/A".equalsIgnoreCase(compName.trim())) {
                        String key = compName.trim().toUpperCase();
                        if (!entityMap.containsKey(key)) {
                            Map<String, Object> item = new HashMap<>();
                            item.put("companyId", "COMP-" + (u.getId() != null ? u.getId() : "101"));
                            item.put("companyName", compName.trim());
                            item.put("clientId", u.getId() != null ? u.getId() : "C-1001");
                            String name = ((u.getFirstName() != null ? u.getFirstName() : "") + " " + (u.getLastName() != null ? u.getLastName() : "")).trim();
                            item.put("clientName", !name.isEmpty() ? name : "Client User");
                            item.put("clientEmail", u.getEmail() != null ? u.getEmail() : "client@globalisor.com");
                            entityMap.put(key, item);
                        }
                    }
                });
            } catch (Exception ignored) {}
        }

        // 3. Collect entities from existing tasks
        try {
            taskRepository.findAll().forEach(t -> {
                String compName = t.getCompanyName();
                if (compName != null && !compName.trim().isEmpty() && !"N/A".equalsIgnoreCase(compName.trim())) {
                    String key = compName.trim().toUpperCase();
                    if (!entityMap.containsKey(key)) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("companyId", t.getCompanyId() != null ? t.getCompanyId() : "COMP-101");
                        item.put("companyName", compName.trim());
                        item.put("clientId", t.getClientId() != null ? t.getClientId() : "C-1001");
                        item.put("clientName", t.getClientName() != null ? t.getClientName() : "Client User");
                        item.put("clientEmail", t.getClientEmail() != null ? t.getClientEmail() : "client@globalisor.com");
                        entityMap.put(key, item);
                    }
                }
            });
        } catch (Exception ignored) {}

        if (entityMap.isEmpty()) {
            Map<String, Object> defaultComp = new HashMap<>();
            defaultComp.put("companyId", "COMP-101");
            defaultComp.put("companyName", "ABBEY HOLDINGS PTE LTD");
            defaultComp.put("clientId", "C-1001");
            defaultComp.put("clientName", "Ethan Tan");
            defaultComp.put("clientEmail", "ethan@abbey.com");
            entityMap.put("ABBEY HOLDINGS PTE LTD", defaultComp);
        }

        List<Map<String, Object>> companies = new ArrayList<>(entityMap.values());
        companies.sort((a, b) -> ((String) a.get("companyName")).compareToIgnoreCase((String) b.get("companyName")));

        // 4. Collect Specialists / Assignees
        List<Map<String, Object>> assignees = new ArrayList<>();
        if (userRepository != null) {
            try {
                userRepository.findAll().stream()
                        .filter(u -> "STAFF".equalsIgnoreCase(u.getRole()) || "ADMIN".equalsIgnoreCase(u.getRole()))
                        .forEach(u -> {
                            String name = ((u.getFirstName() != null ? u.getFirstName() : "") + " " + (u.getLastName() != null ? u.getLastName() : "")).trim();
                            if (name.isEmpty()) {
                                name = "ADMIN".equalsIgnoreCase(u.getRole()) ? "Admin User" : "Staff Specialist";
                            }
                            String avatar = "ST";
                            if (u.getFirstName() != null && !u.getFirstName().isEmpty()) {
                                avatar = String.valueOf(u.getFirstName().charAt(0)).toUpperCase();
                                if (u.getLastName() != null && !u.getLastName().isEmpty()) {
                                    avatar += String.valueOf(u.getLastName().charAt(0)).toUpperCase();
                                }
                            }
                            Map<String, Object> staff = new HashMap<>();
                            staff.put("id", u.getId());
                            staff.put("name", name);
                            staff.put("role", u.getRole() != null ? u.getRole().toUpperCase() : "STAFF");
                            staff.put("email", u.getEmail() != null ? u.getEmail() : "");
                            staff.put("avatar", avatar);
                            assignees.add(staff);
                        });
            } catch (Exception ignored) {}
        }

        if (assignees.stream().noneMatch(s -> "ADMIN".equalsIgnoreCase((String) s.get("role")) || "usr-admin".equals(s.get("id")))) {
            Map<String, Object> admin = new HashMap<>();
            admin.put("id", "usr-admin");
            admin.put("name", "Admin User");
            admin.put("role", "ADMIN");
            admin.put("email", "admin@globalisor.com");
            admin.put("avatar", "AU");
            assignees.add(0, admin);
        }
        if (assignees.stream().noneMatch(s -> "usr-staff".equals(s.get("id")))) {
            Map<String, Object> staff = new HashMap<>();
            staff.put("id", "usr-staff");
            staff.put("name", "Sarah Lim");
            staff.put("role", "STAFF");
            staff.put("email", "staff@globalisor.com");
            staff.put("avatar", "SL");
            assignees.add(staff);
        }

        // 5. Standard Categories
        List<String> defaultCategories = List.of(
                "Registered Address Change",
                "Director / Shareholder Change",
                "Share Capital & Allotment",
                "Company Incorporation",
                "Corporate Secretarial",
                "Tax & Accounting Filing",
                "GST & Bookkeeping",
                "Compliance & Annual Return",
                "Bank Account Opening Assistance",
                "Employment Pass & Work Visas",
                "Strike Off & Liquidation",
                "General Operations & Inquiries"
        );
        Set<String> catSet = new LinkedHashSet<>(defaultCategories);
        try {
            taskRepository.findAll().forEach(t -> {
                if (t.getCategory() != null && !t.getCategory().trim().isEmpty()) {
                    catSet.add(t.getCategory().trim());
                }
            });
        } catch (Exception ignored) {}

        List<Map<String, Object>> priorities = List.of(
                Map.of("value", "LOW", "label", "Low"),
                Map.of("value", "MEDIUM", "label", "Medium"),
                Map.of("value", "HIGH", "label", "High"),
                Map.of("value", "URGENT", "label", "Urgent")
        );

        Map<String, Object> result = new HashMap<>();
        result.put("companies", companies);
        result.put("assignees", assignees);
        result.put("categories", new ArrayList<>(catSet));
        result.put("priorities", priorities);
        return result;
    }

    public void deleteTask(String id) {
        taskRepository.deleteById(id);
    }
}
