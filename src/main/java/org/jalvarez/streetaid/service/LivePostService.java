package org.jalvarez.streetaid.service;

import org.jalvarez.streetaid.repository.LivePostRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class LivePostService {

    private final LivePostRepository repository;
    private final GeocodingService geocodingService;

    public LivePostService(LivePostRepository repository,
                           GeocodingService geocodingService) {
        this.repository = repository;
        this.geocodingService = geocodingService;
    }

    // ── CREATE ────────────────────────────────────────────────

    public LivePost createWebPost(String orgName, String description,
                                  String zipCode, String address,
                                  int expiryHours) {
        GeocodingService.GeoResult geo = geocodingService.geocodeZip(zipCode);

        LivePost post = LivePost.builder()
                .orgName(orgName)
                .description(description)
                .zipCode(zipCode)
                .address(address.isBlank() ? geo.name() : address)
                .latitude(geo.lat())
                .longitude(geo.lng())
                .postedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(expiryHours))
                .postType("SURPLUS")
                .active(true)
                .source("WEB")
                .build();

        return repository.save(post);
    }

    public LivePost createSmsPost(String fromPhone, String zipCode,
                                  String description, String untilTime) {
        GeocodingService.GeoResult geo = geocodingService.geocodeZip(zipCode);
        LocalDateTime expiresAt = parseExpiryTime(untilTime, 4);

        LivePost post = LivePost.builder()
                .orgName("Community Member")
                .contactPhone(fromPhone)
                .description(description)
                .zipCode(zipCode)
                .address(geo.name())
                .latitude(geo.lat())
                .longitude(geo.lng())
                .postedAt(LocalDateTime.now())
                .expiresAt(expiresAt)
                .postType("SURPLUS")
                .active(true)
                .source("SMS")
                .build();

        return repository.save(post);
    }

    public LivePost createRestockAlert(String orgName, String zipCode,
                                       String fromPhone) {
        GeocodingService.GeoResult geo = geocodingService.geocodeZip(zipCode);

        LivePost post = LivePost.builder()
                .orgName(orgName)
                .contactPhone(fromPhone)
                .description(orgName + " has just been restocked.")
                .zipCode(zipCode)
                .address(geo.name())
                .latitude(geo.lat())
                .longitude(geo.lng())
                .postedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(2))
                .postType("RESTOCK")
                .active(true)
                .source("SMS")
                .build();

        return repository.save(post);
    }

    public LivePost createEmptyFlag(String resourceName, String zipCode,
                                    String fromPhone) {
        GeocodingService.GeoResult geo = geocodingService.geocodeZip(zipCode);

        LivePost post = LivePost.builder()
                .orgName("Community Report")
                .contactPhone(fromPhone)
                .description(resourceName + " has been reported empty.")
                .zipCode(zipCode)
                .address(geo.name())
                .latitude(geo.lat())
                .longitude(geo.lng())
                .postedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .postType("EMPTY")
                .active(true)
                .source("SMS")
                .build();

        return repository.save(post);
    }

    // ── READ ──────────────────────────────────────────────────

    public List<LivePost> getActivePosts() {
        return repository.findActivePosts(LocalDateTime.now());
    }

    public List<LivePost> getActivePostsByZip(String zip) {
        return repository.findActivePostsByZip(zip, LocalDateTime.now());
    }

    // ── CLOSE ─────────────────────────────────────────────────

    public void closeById(Long id) {
        repository.findById(id).ifPresent(p -> {
            p.setActive(false);
            repository.save(p);
        });
    }

    public int closeByPhone(String phone) {
        List<LivePost> posts = repository.findByContactPhoneAndActiveTrue(phone);
        posts.forEach(p -> p.setActive(false));
        repository.saveAll(posts);
        return posts.size();
    }

    // ── AUTO EXPIRE ───────────────────────────────────────────

    // Runs every 15 minutes, cleans up expired posts
    @Scheduled(fixedRate = 15 * 60 * 1000)
    public void expireOldPosts() {
        List<LivePost> active = repository.findActivePosts(
                LocalDateTime.now().minusYears(1));
        active.stream()
                .filter(LivePost::isExpired)
                .forEach(p -> {
                    p.setActive(false);
                    repository.save(p);
                });
    }

    // ── HELPERS ───────────────────────────────────────────────

    private LocalDateTime parseExpiryTime(String untilText, int defaultHours) {
        if (untilText == null || untilText.isBlank()) {
            return LocalDateTime.now().plusHours(defaultHours);
        }
        try {
            String cleaned = untilText.toLowerCase()
                    .replace("until", "").replace("till", "").trim();

            if (cleaned.contains("pm")) {
                cleaned = cleaned.replace("pm", "").trim();
                String[] parts = cleaned.split(":");
                int hour = Integer.parseInt(parts[0].trim());
                int minute = parts.length > 1
                        ? Integer.parseInt(parts[1].trim()) : 0;
                if (hour != 12) hour += 12;
                return LocalDateTime.now().with(LocalTime.of(hour % 24, minute));
            } else if (cleaned.contains("am")) {
                cleaned = cleaned.replace("am", "").trim();
                String[] parts = cleaned.split(":");
                int hour = Integer.parseInt(parts[0].trim());
                int minute = parts.length > 1
                        ? Integer.parseInt(parts[1].trim()) : 0;
                return LocalDateTime.now().with(LocalTime.of(hour, minute));
            }
        } catch (Exception ignored) {}

        return LocalDateTime.now().plusHours(defaultHours);
    }

    public String formatExpiryForSms(LocalDateTime expiresAt) {
        return expiresAt.format(DateTimeFormatter.ofPattern("h:mm a"));
    }
}