package org.codeart.cache.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * User timeline containing a list of tweets.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Timeline implements Serializable {

    private static final long serialVersionUID = 1L;

    private String userId;
    private List<Tweet> tweets;
    private Instant cachedAt;
    private Instant expiresAt;
    private int totalTweets;
    private String cursorNext;
    private String cursorPrev;
}
