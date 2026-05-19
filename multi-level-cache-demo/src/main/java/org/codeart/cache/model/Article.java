package org.codeart.cache.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * News article for trending cache demo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Article implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String title;
    private String content;
    private String author;
    private String category; // TECH, SPORTS, POLITICS, etc.
    private List<String> tags;
    private int views;
    private int shares;
    private int comments;
    private boolean trending;
    private double trendingScore; // Calculated based on engagement
    private Instant publishedAt;
    private Instant lastAccessedAt;
    private int accessCount; // For LFU tracking

    /**
     * Calculate trending score based on engagement.
     */
    public double calculateTrendingScore() {
        // Higher score = more trending
        // Recent articles with high engagement score higher
        long ageHours = java.time.Duration.between(publishedAt, Instant.now()).toHours() + 1;
        return (views * 1.0 + shares * 5.0 + comments * 3.0) / ageHours;
    }
}
