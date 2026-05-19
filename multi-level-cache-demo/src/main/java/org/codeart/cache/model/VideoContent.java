package org.codeart.cache.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Video content for streaming cache demo (Netflix/YouTube style).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoContent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String title;
    private String description;
    private String thumbnailUrl;
    private String streamUrl;
    private int durationSeconds;
    private ContentType type;
    private String genre;
    private List<String> cast;
    private double rating;
    private long viewCount;
    private Instant releaseDate;
    private Instant lastWatchedAt;
    private int watchProgress; // Percentage watched (0-100)
    private boolean inWatchlist;

    public enum ContentType {
        MOVIE, TV_SERIES, DOCUMENTARY, SHORT, TRAILER
    }

    /**
     * Check if content is inactive (not watched recently).
     */
    public boolean isInactive() {
        if (lastWatchedAt == null)
            return true;
        // Inactive if not watched in last 7 days
        return Instant.now().minusSeconds(7 * 24 * 3600).isAfter(lastWatchedAt);
    }

    /**
     * Check if user is currently watching (progress > 0 and < 100).
     */
    public boolean isCurrentlyWatching() {
        return watchProgress > 0 && watchProgress < 95;
    }
}
