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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@EnableScheduling
public class ApplicationInitConfiguration {

    PasswordEncoder passwordEncoder;

    @NonFinal
    @Value("${admin.username}")
    String ADMIN_USERNAME;

    @NonFinal
    @Value("${admin.password}")
    String ADMIN_PASSWORD;

    @NonFinal
    @Value("${producer1.username}")
    String PRODUCER1_USERNAME;

    @NonFinal
    @Value("${producer1.password}")
    String PRODUCER1_PASSWORD;

    @NonFinal
    @Value("${producer2.username}")
    String PRODUCER2_USERNAME;

    @NonFinal
    @Value("${producer2.password}")
    String PRODUCER2_PASSWORD;

    @NonFinal
    @Value("${customer1.username}")
    String CUSTOMER1_USERNAME;

    @NonFinal
    @Value("${customer1.password}")
    String CUSTOMER1_PASSWORD;

    @NonFinal
    @Value("${customer2.username}")
    String CUSTOMER2_USERNAME;

    @NonFinal
    @Value("${customer2.password}")
    String CUSTOMER2_PASSWORD;


    @Bean
    @ConditionalOnProperty(
            prefix = "spring",
            value = "datasource.primary.driver-class-name",
            havingValue = "com.mysql.cj.jdbc.Driver")
    @org.springframework.core.annotation.Order(1) // Run before ProducerDataInitializer
    ApplicationRunner applicationRunner(UserRepository userRepository, GenreRepository genreRepository, ProPackageRepository proPackageRepository, PortfolioRepository portfolioRepository) {
        log.info("Initializing application.....");

        return args -> {

            // Initialize data only if database is empty (first time running)
            // Create Admin account: Nguyen Xuan Long
            if (userRepository.findByEmail(ADMIN_USERNAME).isEmpty()) {
                User admin = User.builder()
                        .email(ADMIN_USERNAME)
                        .firstName("Xuan Long")
                        .lastName("Nguyen")
                        .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                        .role(UserRole.ADMIN)
                        .status(UserStatus.ACTIVE)
                        .balance(BigDecimal.valueOf(1000000))
                        .isVerified(false)
                        .avatarUrl("https://scontent.fdad3-4.fna.fbcdn.net/v/t39.30808-6/489957892_24099273106327542_1116435352321896905_n.jpg?_nc_cat=104&ccb=1-7&_nc_sid=6ee11a&_nc_ohc=NBRJip3LaRoQ7kNvwGMglXm&_nc_oc=Admu4uwi_3MfM9wdpsY9hDCvRlwDQyQsIlJUP4ka3AktK4HeNGltpF_r_J0ANWxvt-c&_nc_zt=23&_nc_ht=scontent.fdad3-4.fna&_nc_gid=q1RtH71JcqKb-f315Idh2Q&oh=00_Afjr59W-QReEMDcjlu8yhRO3tpjj6ez00u_bkOZ8alnhbQ&oe=693351B8")
                        .build();
                userRepository.save(admin);
                
                // Tạo portfolio mặc định cho admin
                Portfolio adminPortfolio = Portfolio.builder()
                        .user(admin)
                        .isPublic(true)
                        .sections(new HashSet<>())
                        .personalProjects(new HashSet<>())
                        .socialLinks(new HashSet<>())
                        .genres(new HashSet<>())
                        .tags(new HashSet<>())
                        .build();
                portfolioRepository.save(adminPortfolio);
                log.info("Admin account created: {} ({})", ADMIN_USERNAME, admin.getFullName());
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

            // Create Producer account: SlimV Nguyen
            if (userRepository.findByEmail(PRODUCER1_USERNAME).isEmpty()) {
                User producer1 = User.builder()
                        .email(PRODUCER1_USERNAME)
                        .firstName("SlimV")
                        .lastName("Nguyen")
                        .passwordHash(passwordEncoder.encode(PRODUCER1_PASSWORD))
                        .role(UserRole.PRODUCER)
                        .status(UserStatus.ACTIVE)
                        .balance(BigDecimal.valueOf(1000000))
                        .isVerified(false)
                        .avatarUrl("https://photo-zmp3.zadn.vn/avatars/a/8/d/4/a8d4d2939ee11cd73a159a6dd5bcbe65.jpg")
                        .build();
                userRepository.save(producer1);
                
                // Tạo portfolio mặc định cho producer1
                Portfolio producer1Portfolio = Portfolio.builder()
                        .user(producer1)
                        .isPublic(true)
                        .sections(new HashSet<>())
                        .personalProjects(new HashSet<>())
                        .socialLinks(new HashSet<>())
                        .genres(new HashSet<>())
                        .tags(new HashSet<>())
                        .build();
                portfolioRepository.save(producer1Portfolio);
                log.info("Producer account created: {} ({})", PRODUCER1_USERNAME, producer1.getFullName());
            }

            // Create Producer account: OnlyC Production
            if (userRepository.findByEmail(PRODUCER2_USERNAME).isEmpty()) {
                User producer2 = User.builder()
                        .email(PRODUCER2_USERNAME)
                        .firstName("OnlyC")
                        .lastName("Production")
                        .passwordHash(passwordEncoder.encode(PRODUCER2_PASSWORD))
                        .role(UserRole.PRODUCER)
                        .status(UserStatus.ACTIVE)
                        .balance(BigDecimal.valueOf(1000000))
                        .isVerified(false)
                        .avatarUrl("https://cdn.tuoitre.vn/471584752817336320/2023/2/5/item1thumbnaildesktop-img550405-167553207368489306755.jpg")
                        .build();
                userRepository.save(producer2);
                
                // Tạo portfolio mặc định cho producer2
                Portfolio producer2Portfolio = Portfolio.builder()
                        .user(producer2)
                        .isPublic(true)
                        .sections(new HashSet<>())
                        .personalProjects(new HashSet<>())
                        .socialLinks(new HashSet<>())
                        .genres(new HashSet<>())
                        .tags(new HashSet<>())
                        .build();
                portfolioRepository.save(producer2Portfolio);
                log.info("Producer account created: {} ({})", PRODUCER2_USERNAME, producer2.getFullName());
            }

            // Create Customer account: Noo Phuoc Thinh
            if (userRepository.findByEmail(CUSTOMER1_USERNAME).isEmpty()) {
                User customer1 = User.builder()
                        .email(CUSTOMER1_USERNAME)
                        .firstName("Phuoc Thinh")
                        .lastName("Noo")
                        .passwordHash(passwordEncoder.encode(CUSTOMER1_PASSWORD))
                        .role(UserRole.CUSTOMER)
                        .status(UserStatus.ACTIVE)
                        .balance(BigDecimal.valueOf(1000000))
                        .isVerified(false)
                        .avatarUrl("https://image.vietnamnews.vn/uploadvnnews/Article/2017/11/20/Noo58251613PM.jpg")
                        .build();
                userRepository.save(customer1);
                
                // Tạo portfolio mặc định cho customer1
                Portfolio customer1Portfolio = Portfolio.builder()
                        .user(customer1)
                        .isPublic(true)
                        .sections(new HashSet<>())
                        .personalProjects(new HashSet<>())
                        .socialLinks(new HashSet<>())
                        .genres(new HashSet<>())
                        .tags(new HashSet<>())
                        .build();
                portfolioRepository.save(customer1Portfolio);
                log.info("Customer account created: {} ({})", CUSTOMER1_USERNAME, customer1.getFullName());
            }

            // Create Customer account: An Coong Piano
            if (userRepository.findByEmail(CUSTOMER2_USERNAME).isEmpty()) {
                User customer2 = User.builder()
                        .email(CUSTOMER2_USERNAME)
                        .firstName("An Coong")
                        .lastName("Piano")
                        .passwordHash(passwordEncoder.encode(CUSTOMER2_PASSWORD))
                        .role(UserRole.CUSTOMER)
                        .status(UserStatus.ACTIVE)
                        .balance(BigDecimal.valueOf(1000000))
                        .isVerified(false)
                        .avatarUrl("https://i.scdn.co/image/ab67616d0000b273bb9527c1b12d606df71b4aa5")
                        .build();
                userRepository.save(customer2);
                
                // Tạo portfolio mặc định cho customer2
                Portfolio customer2Portfolio = Portfolio.builder()
                        .user(customer2)
                        .isPublic(true)
                        .sections(new HashSet<>())
                        .personalProjects(new HashSet<>())
                        .socialLinks(new HashSet<>())
                        .genres(new HashSet<>())
                        .tags(new HashSet<>())
                        .build();
                portfolioRepository.save(customer2Portfolio);
                log.info("Customer account created: {} ({})", CUSTOMER2_USERNAME, customer2.getFullName());
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