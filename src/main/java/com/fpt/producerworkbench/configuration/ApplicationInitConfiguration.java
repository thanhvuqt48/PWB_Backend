package com.fpt.producerworkbench.configuration;

import com.fpt.producerworkbench.common.UserRole;
import com.fpt.producerworkbench.common.UserStatus;
import com.fpt.producerworkbench.entity.Genre;
import com.fpt.producerworkbench.entity.Portfolio;
import com.fpt.producerworkbench.entity.ProPackage;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.repository.GenreRepository;
import com.fpt.producerworkbench.repository.PortfolioRepository;
    import com.fpt.producerworkbench.repository.ProPackageRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@EnableScheduling
public class ApplicationInitConfiguration {

    PasswordEncoder passwordEncoder;

    @NonFinal
    @Value("${admin.username}")
    String ADMIN_USER_NAME;

    @NonFinal
    @Value("${admin.password}")
    String ADMIN_PASSWORD;

    @NonFinal
    @Value("${customer.username}")
    String CUSTOMER_USER_NAME;

    @NonFinal
    @Value("${customer.password}")
    String CUSTOMER_PASSWORD;


    @NonFinal
    @Value("${producer.username}")
    String PRODUCER_USER_NAME;

    @NonFinal
    @Value("${producer.password}")
    String PRODUCER_PASSWORD;


    @Bean
    @ConditionalOnProperty(
            prefix = "spring",
            value = "datasource.driver-class-name",
            havingValue = "com.mysql.cj.jdbc.Driver")
    ApplicationRunner applicationRunner(UserRepository userRepository, GenreRepository genreRepository, ProPackageRepository proPackageRepository) {
        log.info("Initializing application.....");

        return args -> {

            if (userRepository.findByEmail(ADMIN_USER_NAME).isEmpty()) {

                User user = User.builder()
                        .email(ADMIN_USER_NAME)
                        .firstName("Pham Thanh")
                        .lastName("Vu")
                        .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                        .role(UserRole.ADMIN)
                        .status(UserStatus.ACTIVE)
                        .dateOfBirth(LocalDate.of(2003, 8, 4))
                        .build();

                userRepository.save(user);
                log.warn("Admin user has been created with default password: 123456, please change it");
            }

            if (userRepository.findByEmail(CUSTOMER_USER_NAME).isEmpty()) {
                User customer = User.builder()
                        .email(CUSTOMER_USER_NAME)
                        .firstName("An")
                        .lastName("Nguyen")
                        .passwordHash(passwordEncoder.encode(CUSTOMER_PASSWORD))
                        .role(UserRole.CUSTOMER)
                        .status(UserStatus.ACTIVE)
                        .location("Ho Chi Minh City, Vietnam")
                        .build();
                userRepository.save(customer);
                log.info("Sample CUSTOMER created: {}", CUSTOMER_USER_NAME);
            }

            if (genreRepository.count() == 0) {
                log.info("No genres found in DB, creating default genres...");
                List<Genre> defaultGenres = Arrays.asList(
                        Genre.builder().name("Pop").build(),
                        Genre.builder().name("V-Pop").build(),
                        Genre.builder().name("K-Pop").build(),
                        Genre.builder().name("Hip Hop / Rap").build(),
                        Genre.builder().name("R&B").build(),
                        Genre.builder().name("Rock").build(),
                        Genre.builder().name("EDM").build(),
                        Genre.builder().name("Vinahouse").build(),
                        Genre.builder().name("Lofi").build(),
                        Genre.builder().name("Ballad").build(),
                        Genre.builder().name("Jazz").build(),
                        Genre.builder().name("Country").build(),
                        Genre.builder().name("Classical").build(),
                        Genre.builder().name("Acoustic").build(),
                        Genre.builder().name("Indie").build(),
                        Genre.builder().name("Folk").build(),
                        Genre.builder().name("Soundtrack").build()
                );
                genreRepository.saveAll(defaultGenres);
                log.info("Created {} default genres.", defaultGenres.size());
            }

            if (proPackageRepository.count() == 0) {
                log.info("No Pro packages found in DB, creating default packages...");
                
                ProPackage monthlyPackage = ProPackage.builder()
                        .name("Gói PRO Tháng")
                        .description("Gói PRO hàng tháng với đầy đủ tính năng cho producer")
                        .price(new BigDecimal("2000"))
                        .packageType(ProPackage.ProPackageType.MONTHLY)
                        .durationMonths(1)
                        .isActive(true)
                        .build();
                
                ProPackage yearlyPackage = ProPackage.builder()
                        .name("Gói PRO Năm")
                        .description("Gói PRO hàng năm với ưu đãi đặc biệt")
                        .price(new BigDecimal("20000"))
                        .packageType(ProPackage.ProPackageType.YEARLY)
                        .durationMonths(12)
                        .isActive(true)
                        .build();
                
                proPackageRepository.save(monthlyPackage);
                proPackageRepository.save(yearlyPackage);
                log.info("Created 2 default Pro packages: Monthly and Yearly");
            }

            log.info("Application initialization completed .....");
        };
    }
}
