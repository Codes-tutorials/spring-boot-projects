package org.codeart.cache.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Redis Pub/Sub based cache invalidation for multi-instance deployments.
 * When one instance invalidates cache, all instances receive the message.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEventListener implements MessageListener {

    private final StringRedisTemplate redisTemplate;
    private final CacheManager cacheManager;
    private final RedisMessageListenerContainer listenerContainer;

    public static final String CACHE_INVALIDATION_CHANNEL = "cache:invalidation";

    @PostConstruct
    public void init() {
        // Subscribe to invalidation channel
        listenerContainer.addMessageListener(this, new ChannelTopic(CACHE_INVALIDATION_CHANNEL));
        log.info("Subscribed to cache invalidation channel: {}", CACHE_INVALIDATION_CHANNEL);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody());
        log.debug("Received cache invalidation message: {}", payload);

        try {
            // Parse message format: "cacheName:key" or "cacheName:*" for all
            String[] parts = payload.split(":", 2);
            if (parts.length < 2) {
                log.warn("Invalid invalidation message format: {}", payload);
                return;
            }

            String cacheName = parts[0];
            String key = parts[1];

            var cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                log.debug("Cache '{}' not found, skipping invalidation", cacheName);
                return;
            }

            if ("*".equals(key)) {
                // Evict all entries
                cache.clear();
                log.info("Cleared all entries from cache '{}' via pub/sub", cacheName);
            } else {
                // Evict specific key
                cache.evict(key);
                log.info("Evicted key '{}' from cache '{}' via pub/sub", key, cacheName);
            }
        } catch (Exception e) {
            log.error("Error processing cache invalidation message: {}", payload, e);
        }
    }

    /**
     * Publish cache invalidation event to all instances.
     */
    public void publishInvalidation(String cacheName, Object key) {
        String message = cacheName + ":" + key;
        redisTemplate.convertAndSend(CACHE_INVALIDATION_CHANNEL, message);
        log.debug("Published cache invalidation: {}", message);
    }

    /**
     * Publish clear all event.
     */
    public void publishClearAll(String cacheName) {
        publishInvalidation(cacheName, "*");
    }
}
