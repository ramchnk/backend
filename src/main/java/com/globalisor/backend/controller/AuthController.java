package com.globalisor.backend.controller;

import com.globalisor.backend.model.User;
import com.globalisor.backend.payload.request.LoginRequest;
import com.globalisor.backend.payload.request.SignupRequest;
import com.globalisor.backend.payload.response.JwtResponse;
import com.globalisor.backend.payload.response.MessageResponse;
import com.globalisor.backend.repository.UserRepository;
import com.globalisor.backend.security.EncryptionUtils;
import com.globalisor.backend.security.JwtUtils;
import com.globalisor.backend.security.UserDetailsImpl;
import com.globalisor.backend.model.Kyc;
import com.globalisor.backend.model.Compliance;
import com.globalisor.backend.repository.KycRepository;
import com.globalisor.backend.repository.ComplianceRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    KycRepository kycRepository;

    @Autowired
    ComplianceRepository complianceRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    EncryptionUtils encryptionUtils;

    @Autowired
    private com.globalisor.backend.service.NotificationService notificationService;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        String role = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .findFirst()
                .orElse("ROLE_USER")
                .substring(5);

        return ResponseEntity.ok(new JwtResponse(jwt,
                userDetails.getId(),
                userDetails.getEmail(),
                userDetails.getFirstName(),
                userDetails.getLastName(),
                role));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        String encryptedEmailForCheck = encryptionUtils.encryptQueryable(signUpRequest.getEmail());
        if (userRepository.existsByEmail(encryptedEmailForCheck)) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Email is already in use!"));
        }

        // Create new user's account
        User user = new User(signUpRequest.getFirstName(),
                signUpRequest.getLastName(),
                signUpRequest.getEmail(),
                encoder.encode(signUpRequest.getPassword()));

        userRepository.save(user);

        // Auto-initialize KYC record for new client
        Kyc kyc = new Kyc();
        kyc.setId("KYC-" + System.currentTimeMillis());
        kyc.setClientId(user.getId());
        kyc.setName(user.getFirstName() + " " + user.getLastName());
        kyc.setIdType("N/A");
        kyc.setIdNum("N/A");
        kyc.setNation("N/A");
        kyc.setStatus("pending");
        kyc.setRisk("Low");
        kyc.setLastUpdated(System.currentTimeMillis());
        kyc.getAuditLogs().add("KYC profile initialized on user registration.");
        kycRepository.save(kyc);

        // Auto-initialize Compliance record for new client
        Compliance compliance = new Compliance();
        compliance.setId("COMP-" + System.currentTimeMillis());
        compliance.setClientId(user.getId());
        compliance.setName(user.getFirstName() + " " + user.getLastName());
        compliance.setType("AML Screening");
        compliance.setStatus("pending");
        compliance.setRisk("Low");
        compliance.setLastUpdated(System.currentTimeMillis());
        compliance.getAuditLogs().add("AML compliance monitoring initialized on registration.");
        complianceRepository.save(compliance);

        try {
            notificationService.sendNotification(
                    "admin",
                    "New Client Registered",
                    user.getFirstName() + " " + user.getLastName() + " (" + signUpRequest.getEmail() + ") registered as a new client.",
                    "registration",
                    user.getId(),
                    "Info"
            );
        } catch (Exception e) {
            // ignore
        }

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }
}
