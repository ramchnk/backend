package com.globalisor.backend.controller;

import com.globalisor.backend.model.SsicActivity;
import com.globalisor.backend.repository.SsicActivityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
public class SsicActivityControllerTest {

    @Mock
    private SsicActivityRepository ssicActivityRepository;

    @InjectMocks
    private SsicActivityController controller;

    @Test
    public void testImportActivities() {
        SsicActivity activity1 = new SsicActivity();
        activity1.setCode("62011");
        activity1.setName("Development of software");
        activity1.setCategory("Information");
        activity1.setDescription("Dev");

        SsicActivity activity2 = new SsicActivity();
        activity2.setCode("62021");
        activity2.setName("IT Consultancy");
        activity2.setCategory("Information");
        activity2.setDescription("Consulting");

        // Mock repository lookup: 62011 exists (with changes), 62021 does not exist
        SsicActivity existing = new SsicActivity();
        existing.setId("ssic-62011");
        existing.setCode("62011");
        existing.setName("Development of software OLD");
        existing.setCategory("Information");
        existing.setDescription("Dev");

        Mockito.when(ssicActivityRepository.findByCode("62011")).thenReturn(Optional.of(existing));
        Mockito.when(ssicActivityRepository.findByCode("62021")).thenReturn(Optional.empty());

        Mockito.when(ssicActivityRepository.save(any(SsicActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, List<SsicActivity>> payload = new HashMap<>();
        payload.put("activities", Arrays.asList(activity1, activity2));

        ResponseEntity<?> response = controller.importActivities(payload);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals(true, body.get("success"));
        assertEquals(1, body.get("added"));
        assertEquals(1, body.get("updated"));
    }
}
