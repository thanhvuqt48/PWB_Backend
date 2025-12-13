package com.fpt.producerworkbench.configuration;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Configuration for dual database setup:
 * - MySQL: Main application data (existing)
 * - PostgreSQL: AI User Guides data (new)
 * 
 * Note: Repository scanning is handled by @EnableJpaRepositories on Application class
 */
@Slf4j
@Configuration
@EnableTransactionManagement
public class PrimaryDataSourceConfig {

    /**
     * Primary DataSource (MySQL) - Main application
     */
    @Primary
    @Bean(name = "primaryDataSourceProperties")
    @ConfigurationProperties("spring.datasource.primary")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "primaryDataSource")
    public DataSource primaryDataSource(
            @Qualifier("primaryDataSourceProperties") DataSourceProperties properties) {
        
        log.info("🔧 Configuring Primary DataSource (MySQL)");
        log.info("   JDBC URL: {}", properties.getUrl());
        
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        
        dataSource.setPoolName("PrimaryHikariPool");
        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(5);
        dataSource.setConnectionTimeout(30000);
        
        return dataSource;
    }

    /**
     * Primary EntityManagerFactory (MySQL)
     * Note: Uses custom PersistenceUnitPostProcessor to exclude userguide entities
     */
    @Primary
    @Bean(name = "primaryEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean primaryEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("primaryDataSource") DataSource dataSource) {
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "update");
        properties.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        properties.put("hibernate.show_sql", false);
        properties.put("hibernate.format_sql", true);
        
        LocalContainerEntityManagerFactoryBean em = builder
                .dataSource(dataSource)
                .packages("com.fpt.producerworkbench.entity")
                .persistenceUnit("primary")
                .properties(properties)
                .build();
        
        // ⚠️ CRITICAL: Add post-processor to filter out userguide entities
        em.setPersistenceUnitPostProcessors(persistenceUnitInfo -> {
            // Get managed class names and filter out userguide entities
            List<String> managedClassNames = persistenceUnitInfo.getManagedClassNames();
            List<String> filteredClassNames = managedClassNames.stream()
                    .filter(className -> !className.contains(".userguide."))
                    .collect(Collectors.toList());
            
            log.info("🔍 Primary EntityManagerFactory: Filtered {} entities → {} (excluded userguide)", 
                    managedClassNames.size(), filteredClassNames.size());
            
            // Clear and re-add filtered classes
            managedClassNames.clear();
            managedClassNames.addAll(filteredClassNames);
        });
        
        return em;
    }

    /**
     * Primary TransactionManager (MySQL)
     */
    @Primary
    @Bean(name = "primaryTransactionManager")
    public PlatformTransactionManager primaryTransactionManager(
            @Qualifier("primaryEntityManagerFactory") LocalContainerEntityManagerFactoryBean primaryEntityManagerFactory) {
        
        return new JpaTransactionManager(Objects.requireNonNull(
                primaryEntityManagerFactory.getObject()));
    }
}
