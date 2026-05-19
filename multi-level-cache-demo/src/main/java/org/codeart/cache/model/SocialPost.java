package org.codeart.cache.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Social media post for feed caching demo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialPost implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String userId;
    private String username;
    private String content;
    private String mediaUrl; // Image/video URL
    private PostType type; // TEXT, IMAGE, VIDEO, STORY
    private int likes;
    private int comments;
    private int shares;
    private List<String> hashtags;
    private Instant createdAt;
    private Instant lastInteractionAt;
    private boolean isInteractedWith; // User has liked/commented

    public enum PostType {
        TEXT, IMAGE, VIDEO, STORY, REEL
    }

    /**
     * Check if post is old (no interaction in last hour).
     */
    public boolean isStale() {
        if (lastInteractionAt == null) {
            lastInteractionAt = createdAt;
        }
        return Instant.now().minusSeconds(3600).isAfter(lastInteractionAt);
    }
}
