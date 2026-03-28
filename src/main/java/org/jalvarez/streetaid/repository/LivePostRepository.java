package org.jalvarez.streetaid.repository;

import org.jalvarez.streetaid.service.LivePost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LivePostRepository extends JpaRepository<LivePost, Long> {

    // All active non-expired posts
    @Query("SELECT p FROM LivePost p WHERE p.active = true AND p.expiresAt > :now ORDER BY p.postedAt DESC")
    List<LivePost> findActivePosts(LocalDateTime now);

    // Active posts for a specific zip
    @Query("SELECT p FROM LivePost p WHERE p.active = true AND p.expiresAt > :now AND p.zipCode = :zip ORDER BY p.postedAt DESC")
    List<LivePost> findActivePostsByZip(String zip, LocalDateTime now);

    // For SMS CLOSE command — find posts by the phone that created them
    List<LivePost> findByContactPhoneAndActiveTrue(String phone);
}