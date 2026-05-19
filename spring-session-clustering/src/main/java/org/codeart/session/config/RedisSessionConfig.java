package org.codeart.session.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@Configuration
@Profile("redis")
@EnableRedisHttpSession
public class RedisSessionConfig {
    // This annotation is enough to enable Redis-backed sessions.
    // By default, it uses 'spring:session' namespace in Redis.
}
