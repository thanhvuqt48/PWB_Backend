package com.fpt.producerworkbench.configuration;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Secondary DataSource Configuration for PostgreSQL (User Guides)
 * This database stores AI-powered user guide data
 * 
 * Note: Repository scanning is handled by JpaRepositoriesConfig
 */
@Slf4j
@Configuration
@EnableTransactionManagement
public class UserGuideDataSourceConfig {

    /**
     * Secondary DataSource (PostgreSQL) - User Guides
     */
    @Bean(name = "userGuideDataSourceProperties")
    @ConfigurationProperties("spring.datasource.userguide")
    public DataSourceProperties userGuideDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "userGuideDataSource")
    public DataSource userGuideDataSource(
            @Qualifier("userGuideDataSourceProperties") DataSourceProperties properties) {
        
        log.info("🔧 Configuring User Guide DataSource (PostgreSQL - Neon)");
        log.info("   JDBC URL: {}", properties.getUrl());
        
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        
        dataSource.setPoolName("UserGuideHikariPool");
        dataSource.setMaximumPoolSize(5);
        dataSource.setMinimumIdle(2);
        dataSource.setConnectionTimeout(30000);
        
        return dataSource;
    }

    /**
     * Secondary EntityManagerFactory (PostgreSQL)
     */
    @Bean(name = "userGuideEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean userGuideEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("userGuideDataSource") DataSource dataSource) {
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "update"); // Keep tables, only update schema
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.put("hibernate.show_sql", true);
        properties.put("hibernate.format_sql", true);
        properties.put("hibernate.jdbc.lob.non_contextual_creation", true);
        properties.put("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        
        log.info("🔧 UserGuide EntityManagerFactory - packages: com.fpt.producerworkbench.entity.userguide");
        
        return builder
                .dataSource(dataSource)
                .packages("com.fpt.producerworkbench.entity.userguide")
                .persistenceUnit("userguide")
                .properties(properties)
                .build();
    }

    /**
     * Secondary TransactionManager (PostgreSQL)
     */
    @Bean(name = "userGuideTransactionManager")
    public PlatformTransactionManager userGuideTransactionManager(
            @Qualifier("userGuideEntityManagerFactory") LocalContainerEntityManagerFactoryBean userGuideEntityManagerFactory) {
        
        return new JpaTransactionManager(Objects.requireNonNull(
                userGuideEntityManagerFactory.getObject()));
    }
}
