package org.codeart.redis.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConditionalOnClass(RedisClient.class)
@EnableConfigurationProperties(LettuceProperties.class)
public class LettuceAutoConfiguration {
    @Autowired
    private LettuceProperties properties;

    @Bean
    public RedisClient redisClient() {
        RedisURI uri = RedisURI.builder()
                .withHost(properties.getHost())
                .withPort(properties.getPort())
                .withDatabase(properties.getDatabase())
                .withTimeout(Duration.ofMillis(properties.getTimeout()))
                .withSsl(properties.isSsl())
                .build();
        return RedisClient.create(uri);
    }
}
