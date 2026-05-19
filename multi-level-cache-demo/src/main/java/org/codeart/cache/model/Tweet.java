package org.codeart.cache.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Tweet model for timeline caching demo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tweet implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String userId;
    private String username;
    private String content;
    private int likes;
    private int retweets;
    private int replies;
    private Instant createdAt;
    private boolean isRetweet;
    private String retweetedFrom;
}
