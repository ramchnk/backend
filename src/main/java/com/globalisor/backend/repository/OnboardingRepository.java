package com.globalisor.backend.repository;

import com.globalisor.backend.model.Onboarding;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface OnboardingRepository extends MongoRepository<Onboarding, String> {
    Optional<Onboarding> findByClientId(String clientId);
    java.util.List<Onboarding> findAllByOrderByCreatedAtDesc();
}
