package com.fpt.producerworkbench.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA Repositories Configuration
 * Separates repository scanning from datasource configuration to avoid conflicts
 * Scans root repository package but userguide is already claimed by UserGuideJpaRepositoriesConfig
 */
@Configuration
@org.springframework.core.annotation.Order(2)
@EnableJpaRepositories(
        basePackages = {
                "com.fpt.producerworkbench.repository"
        },
        entityManagerFactoryRef = "primaryEntityManagerFactory",
        transactionManagerRef = "primaryTransactionManager",
        considerNestedRepositories = false
)
public class PrimaryJpaRepositoriesConfig {
    // Primary repositories (MySQL) - all repositories except userguide package
}
