package org.jalvarez.streetaid.service;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "live_posts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LivePost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String orgName;
    private String contactPhone;

    @Column(length = 500)
    private String description;

    private String address;
    private String zipCode;
    private double latitude;
    private double longitude;

    private LocalDateTime postedAt;
    private LocalDateTime expiresAt;

    // SURPLUS, RESTOCK, EMPTY, POPUP
    private String postType;

    private boolean active;

    // WEB or SMS
    private String source;

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public long minutesRemaining() {
        if (expiresAt == null) return 0;
        return java.time.Duration.between(LocalDateTime.now(), expiresAt).toMinutes();
    }
}