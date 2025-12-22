package org.codeart.redis.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("spring.redis")
public class LettuceProperties {
    private String host = "localhost";
    private int port = 6379;
    private int database = 0;
    private long timeout = 1000;
    private boolean ssl;
}
