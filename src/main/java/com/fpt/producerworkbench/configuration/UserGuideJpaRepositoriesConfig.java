package com.fpt.producerworkbench.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * User Guide JPA Repositories Configuration (PostgreSQL)
 * Handles repository scanning for user guide related entities
 * Must be loaded FIRST to claim userguide repositories
 */
@Configuration
@org.springframework.core.annotation.Order(1)
@EnableJpaRepositories(
        basePackages = {
                "com.fpt.producerworkbench.repository.userguide"
        },
        entityManagerFactoryRef = "userGuideEntityManagerFactory",
        transactionManagerRef = "userGuideTransactionManager"
)
public class UserGuideJpaRepositoriesConfig {
    // User Guide repositories (PostgreSQL)
}
