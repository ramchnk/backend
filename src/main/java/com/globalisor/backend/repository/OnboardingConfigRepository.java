package com.globalisor.backend.repository;

import com.globalisor.backend.model.OnboardingConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OnboardingConfigRepository extends MongoRepository<OnboardingConfig, String> {
    List<OnboardingConfig> findByStatusOrderBySortOrderAsc(String status);
    List<OnboardingConfig> findByStatusNotOrderBySortOrderAsc(String status);
    List<OnboardingConfig> findAllByOrderBySortOrderAsc();
    boolean existsByKey(String key);
    OnboardingConfig findByKey(String key);

    List<OnboardingConfig> findByJourneyTypeAndStatusOrderBySortOrderAsc(String journeyType, String status);
    List<OnboardingConfig> findByJourneyTypeOrderBySortOrderAsc(String journeyType);
    List<OnboardingConfig> findByJourneyTypeAndStatusNotOrderBySortOrderAsc(String journeyType, String status);
    boolean existsByKeyAndJourneyType(String key, String journeyType);
    OnboardingConfig findByKeyAndJourneyType(String key, String journeyType);
}
