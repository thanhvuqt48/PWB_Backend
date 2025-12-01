package com.fpt.producerworkbench.configuration;

import com.fpt.producerworkbench.common.UserRole;
import com.fpt.producerworkbench.common.UserStatus;
import com.fpt.producerworkbench.entity.Genre;
import com.fpt.producerworkbench.entity.Portfolio;
import com.fpt.producerworkbench.entity.Tag;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.repository.GenreRepository;
import com.fpt.producerworkbench.repository.PortfolioRepository;
import com.fpt.producerworkbench.repository.TagRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.*;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ProducerDataInitializer {

    PasswordEncoder passwordEncoder;
    UserRepository userRepository;
    PortfolioRepository portfolioRepository;
    GenreRepository genreRepository;
    TagRepository tagRepository;

    // Avatar URLs - all under 1000 characters (verified)
    private static final String[] AVATAR_URLS = {
            "https://media.vov.vn/sites/default/files/styles/large/public/2022-07/k-icm_dj_mag.jpeg",
            "https://cdn.tienphong.vn/armin1_YSVQ.jpg",
            "https://www.festivalinfo.nl/img/upload/4/1/Headhunterz.jpg",
            "https://i.scdn.co/image/ab6761610000e5eb0bae7d0b0e0e0e0e0e0e0e0e0",
            "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f",
            "https://images.unsplash.com/photo-1516280440614-37939bbacd81",
            "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad",
            "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=500",
            "https://images.unsplash.com/photo-1516280440614-37939bbacd81?w=500",
            "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=500",
            "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=500&h=500",
            "https://images.unsplash.com/photo-1516280440614-37939bbacd81?w=500&h=500",
            "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=500&h=500",
            "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=500&h=500&fit=crop",
            "https://images.unsplash.com/photo-1516280440614-37939bbacd81?w=500&h=500&fit=crop",
            "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=500&h=500&fit=crop",
            "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=500&h=500&fit=crop&q=80",
            "https://images.unsplash.com/photo-1516280440614-37939bbacd81?w=500&h=500&fit=crop&q=80",
            "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=500&h=500&fit=crop&q=80",
            "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=500&h=500&fit=crop&q=80&auto=format"
    };

    // Producer data with genres distribution
    private static final List<ProducerData> PRODUCER_DATA = Arrays.asList(
            // K-Pop producers (5)
            new ProducerData("Min", "Kim", "min.kim.pwb@gmail.com", "K-Pop", Arrays.asList("K-Pop"), Arrays.asList("k-pop", "korean", "idol")),
            new ProducerData("Ji", "Park", "ji.park.pwb@gmail.com", "K-Pop", Arrays.asList("K-Pop", "Pop"), Arrays.asList("k-pop", "korean", "dance")),
            new ProducerData("Soo", "Lee", "soo.lee.pwb@gmail.com", "K-Pop", Arrays.asList("K-Pop", "EDM"), Arrays.asList("k-pop", "electronic", "korean")),
            new ProducerData("Hyun", "Choi", "hyun.choi.pwb@gmail.com", "K-Pop", Arrays.asList("K-Pop", "Hip Hop / Rap"), Arrays.asList("k-pop", "hip hop", "korean")),
            new ProducerData("Yoon", "Jung", "yoon.jung.pwb@gmail.com", "K-Pop", Arrays.asList("K-Pop", "Ballad"), Arrays.asList("k-pop", "ballad", "korean")),

            // V-Pop producers (5)
            new ProducerData("Anh", "Tran", "anh.tran.pwb@gmail.com", "V-Pop", Arrays.asList("V-Pop"), Arrays.asList("v-pop", "vietnamese", "pop")),
            new ProducerData("Binh", "Nguyen", "binh.nguyen.pwb@gmail.com", "V-Pop", Arrays.asList("V-Pop", "Ballad"), Arrays.asList("v-pop", "vietnamese", "ballad")),
            new ProducerData("Cuong", "Le", "cuong.le.pwb@gmail.com", "V-Pop", Arrays.asList("V-Pop", "Acoustic"), Arrays.asList("v-pop", "vietnamese", "acoustic")),
            new ProducerData("Dung", "Pham", "dung.pham.pwb@gmail.com", "V-Pop", Arrays.asList("V-Pop", "Indie"), Arrays.asList("v-pop", "vietnamese", "indie")),
            new ProducerData("Em", "Hoang", "em.hoang.pwb@gmail.com", "V-Pop", Arrays.asList("V-Pop", "Folk"), Arrays.asList("v-pop", "vietnamese", "folk")),

            // Vinahouse producers (5)
            new ProducerData("Giang", "Vu", "giang.vu.pwb@gmail.com", "Vinahouse", Arrays.asList("Vinahouse"), Arrays.asList("vinahouse", "vietnamese", "house")),
            new ProducerData("Hoa", "Do", "hoa.do.pwb@gmail.com", "Vinahouse", Arrays.asList("Vinahouse", "EDM"), Arrays.asList("vinahouse", "edm", "vietnamese")),
            new ProducerData("Hung", "Bui", "hung.bui.pwb@gmail.com", "Vinahouse", Arrays.asList("Vinahouse", "Pop"), Arrays.asList("vinahouse", "pop", "vietnamese")),
            new ProducerData("Khanh", "Ngo", "khanh.ngo.pwb@gmail.com", "Vinahouse", Arrays.asList("Vinahouse", "EDM"), Arrays.asList("vinahouse", "dance", "vietnamese")),
            new ProducerData("Linh", "Dang", "linh.dang.pwb@gmail.com", "Vinahouse", Arrays.asList("Vinahouse", "Lofi"), Arrays.asList("vinahouse", "electronic", "vietnamese")),

            // Vietnamese Hip Hop producers (5)
            new ProducerData("Minh", "Ly", "minh.ly.pwb@gmail.com", "Vietnamese Hip Hop", Arrays.asList("Hip Hop / Rap"), Arrays.asList("vietnamese hip hop", "hip hop", "rap")),
            new ProducerData("Nam", "Vo", "nam.vo.pwb@gmail.com", "Vietnamese Hip Hop", Arrays.asList("Hip Hop / Rap", "R&B"), Arrays.asList("vietnamese hip hop", "r&b", "hip hop")),
            new ProducerData("Oanh", "Truong", "oanh.truong.pwb@gmail.com", "Vietnamese Hip Hop", Arrays.asList("Hip Hop / Rap", "Rock"), Arrays.asList("vietnamese hip hop", "trap", "rap")),
            new ProducerData("Phuc", "Lam", "phuc.lam.pwb@gmail.com", "Vietnamese Hip Hop", Arrays.asList("Hip Hop / Rap", "Lofi"), Arrays.asList("vietnamese hip hop", "urban", "hip hop")),
            new ProducerData("Quang", "Phan", "quang.phan.pwb@gmail.com", "Vietnamese Hip Hop", Arrays.asList("Hip Hop / Rap", "Jazz"), Arrays.asList("vietnamese hip hop", "street", "rap"))
    );

    @Bean
    @ConditionalOnProperty(
            prefix = "spring",
            value = "datasource.driver-class-name",
            havingValue = "com.mysql.cj.jdbc.Driver")
    @Order(2) // Run after ApplicationInitConfiguration
    ApplicationRunner initializeProducerSampleData() {
        return args -> {
            log.info("Initializing producer sample data...");

            // Get all genres
            List<Genre> allGenres = genreRepository.findAll();
            Map<String, Genre> genreMap = new HashMap<>();
            for (Genre genre : allGenres) {
                genreMap.put(genre.getName(), genre);
            }

            int createdCount = 0;
            int skippedCount = 0;
            Random random = new Random();

            for (int i = 0; i < PRODUCER_DATA.size(); i++) {
                ProducerData data = PRODUCER_DATA.get(i);

                // Check if user already exists
                if (userRepository.findByEmail(data.email).isPresent()) {
                    log.debug("Producer {} already exists, skipping", data.email);
                    skippedCount++;
                    continue;
                }

                // Central Vietnam location names (matching coordinates array)
                String[] centralVietnamLocationNames = {
                        "Da Nang, Vietnam",
                        "Hue, Vietnam",
                        "Hoi An, Vietnam",
                        "Tam Ky, Vietnam",
                        "Da Nang, Vietnam",
                        "Da Nang, Vietnam",
                        "Da Nang, Vietnam",
                        "Hue, Vietnam",
                        "Hoi An, Vietnam",
                        "Da Nang, Vietnam"
                };

                // Create User
                User user = User.builder()
                        .email(data.email)
                        .firstName(data.firstName)
                        .lastName(data.lastName)
                        .passwordHash(passwordEncoder.encode("Producer@123456"))
                        .role(UserRole.PRODUCER)
                        .status(UserStatus.ACTIVE)
                        .avatarUrl(AVATAR_URLS[i % AVATAR_URLS.length])
                        .isVerified(false)
                        .build();

                User savedUser = userRepository.save(user);
                log.debug("Created producer user: {} ({})", data.email, savedUser.getFullName());

                // Get genres for this producer
                Set<Genre> producerGenres = new HashSet<>();
                for (String genreName : data.genres) {
                    Genre genre = genreMap.get(genreName);
                    if (genre != null) {
                        producerGenres.add(genre);
                    } else {
                        log.warn("Genre '{}' not found in database, skipping", genreName);
                    }
                }

                // Get or create tags
                Set<Tag> producerTags = new HashSet<>();
                for (String tagName : data.tags) {
                    Tag tag = tagRepository.findByName(tagName)
                            .orElseGet(() -> {
                                Tag newTag = Tag.builder()
                                        .name(tagName)
                                        .build();
                                return tagRepository.save(newTag);
                            });
                    producerTags.add(tag);
                }

                // Central Vietnam locations (Da Nang, Hue, Hoi An, etc.)
                // Da Nang: 16.0544, 108.2022
                // Hue: 16.4637, 107.5909
                // Hoi An: 15.8801, 108.3380
                // Tam Ky: 15.5736, 108.4740
                double[][] centralVietnamLocations = {
                        {16.0544, 108.2022},  // Da Nang center
                        {16.4637, 107.5909},  // Hue center
                        {15.8801, 108.3380},  // Hoi An
                        {15.5736, 108.4740},  // Tam Ky
                        {16.0678, 108.2208},  // Da Nang - Hai Chau
                        {16.0514, 108.2267},  // Da Nang - Thanh Khe
                        {16.0700, 108.1500},  // Da Nang - Son Tra
                        {16.5000, 107.6000},  // Hue - Phu Vang
                        {15.9000, 108.3000},  // Hoi An area
                        {16.0000, 108.2000}   // Between Da Nang and Hue
                };
                
                // Pick a random location from central Vietnam (same index for name and coordinates)
                int locationIndex = random.nextInt(centralVietnamLocations.length);
                String location = centralVietnamLocationNames[locationIndex];
                double baseLat = centralVietnamLocations[locationIndex][0];
                double baseLon = centralVietnamLocations[locationIndex][1];
                
                // Add small random offset (within ~5km radius)
                double lat = baseLat + (random.nextDouble() - 0.5) * 0.05;
                double lon = baseLon + (random.nextDouble() - 0.5) * 0.05;
                
                // Update user location
                savedUser.setLocation(location);
                userRepository.save(savedUser);

                // Create Portfolio
                Portfolio portfolio = Portfolio.builder()
                        .user(savedUser)
                        .headline(data.headline)
                        .isPublic(true)
                        .genres(producerGenres)
                        .tags(producerTags)
                        .latitude(lat)
                        .longitude(lon)
                        .build();

                portfolioRepository.save(portfolio);
                createdCount++;
                log.info("Created producer with portfolio: {} ({}) - Genres: {}, Tags: {}", 
                        data.email, savedUser.getFullName(), data.genres, data.tags);
            }

            log.info("Producer sample data initialization completed. Created {} new producers with portfolios, skipped {} existing producers.", 
                    createdCount, skippedCount);
        };
    }

    private static class ProducerData {
        final String firstName;
        final String lastName;
        final String email;
        final String headline;
        final List<String> genres;
        final List<String> tags;

        ProducerData(String firstName, String lastName, String email, String headline,
                     List<String> genres, List<String> tags) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.headline = headline;
            this.genres = genres;
            this.tags = tags;
        }
    }
}

