package com.fpt.producerworkbench.configuration;

import com.fpt.producerworkbench.common.UserRole;
import com.fpt.producerworkbench.common.UserStatus;
import com.fpt.producerworkbench.entity.Genre;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.repository.GenreRepository;
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

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ApplicationInitConfiguration {

    PasswordEncoder passwordEncoder;

    @NonFinal
    @Value("${admin.username}")
    String ADMIN_USER_NAME;

    @NonFinal
    @Value("${admin.password}")
    String ADMIN_PASSWORD;

    @Bean
    @ConditionalOnProperty(
            prefix = "spring",
            value = "datasource.driver-class-name",
            havingValue = "com.mysql.cj.jdbc.Driver")
    ApplicationRunner applicationRunner(UserRepository userRepository, GenreRepository genreRepository) {
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

            log.info("Application initialization completed .....");
        };
    }
}
