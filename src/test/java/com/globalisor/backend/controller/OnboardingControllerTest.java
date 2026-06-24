package com.globalisor.backend.controller;

import com.globalisor.backend.model.Onboarding;
import com.globalisor.backend.repository.OnboardingRepository;
import com.globalisor.backend.repository.UserRepository;
import com.globalisor.backend.service.NotificationService;
import com.globalisor.backend.websocket.ChatWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
public class OnboardingControllerTest {

    @Mock
    private OnboardingRepository onboardingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ChatWebSocketHandler chatWebSocketHandler;

    @InjectMocks
    private OnboardingController controller;

    @Test
    public void testUpdateStepAndOverallStatus() {
        Onboarding ob = new Onboarding();
        ob.setId("OB-1");
        ob.setClientId("C-1001");
        ob.setStatus("in_progress");

        Mockito.when(onboardingRepository.findById("OB-1")).thenReturn(Optional.of(ob));
        Mockito.when(onboardingRepository.save(any(Onboarding.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Submit step 1
        Map<String, Object> body = new HashMap<>();
        body.put("status", "submitted");
        Map<String, Object> data = new HashMap<>();
        data.put("fullName", "John Doe");
        body.put("data", data);

        ResponseEntity<?> response = controller.updateStep("OB-1", "individual_verification", body);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Onboarding saved = (Onboarding) response.getBody();
        assertNotNull(saved);
        assertEquals("submitted", saved.getStep1IndividualVerification().getStatus());
        // Since only step 1 is submitted and others are pending, overall status should still be "in_progress"
        assertEquals("in_progress", saved.getStatus());

        // Now submit all other steps to test if overall status becomes "submitted"
        saved.getStep2DirectorDetails().setStatus("submitted");
        saved.getStep3IndividualShareholder().setStatus("submitted");
        saved.getStep4CorporateShareholder().setStatus("submitted");
        saved.getStep5UBO().setStatus("submitted");
        saved.getStep6CorporateRep().setStatus("submitted");
        
        // Let's submit step 7 through the controller to trigger overall status recalculation
        ResponseEntity<?> finalResponse = controller.updateStep("OB-1", "final_declaration", body);
        Onboarding finalSaved = (Onboarding) finalResponse.getBody();
        assertNotNull(finalSaved);
        assertEquals("submitted", finalSaved.getStep7FinalDeclaration().getStatus());
        // All steps are submitted, overall status should be "submitted"
        assertEquals("submitted", finalSaved.getStatus());
    }
}
