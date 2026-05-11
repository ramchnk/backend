package com.globalisor.backend.payload.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {
    private List<ClientInfo> clients;
    private Map<String, Long> stats;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientInfo {
        private String clientId;
        private String name;
        private String email;
        private int serviceCount;
        private String latestActivity;
        private String latestStatus;
        private List<String> companyNames;
        private String phone;
        private long createdAt;
    }
}
