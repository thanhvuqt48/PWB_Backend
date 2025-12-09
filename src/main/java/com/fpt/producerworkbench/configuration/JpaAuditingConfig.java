package com.fpt.producerworkbench.configuration;

import com.fpt.producerworkbench.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
@RequiredArgsConstructor
public class JpaAuditingConfig {

    private final SecurityUtils securityUtils;

    @Bean
    public AuditorAware<Long> auditorProvider() {
        return () -> {
            try {
                return Optional.of(securityUtils.getCurrentUserId());
            } catch (Exception e) {
                // Return empty if no authenticated user (e.g., system operations, scheduled tasks)
                return Optional.empty();
            }
        };
    }
}

