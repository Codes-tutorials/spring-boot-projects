package org.codeart.saga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SagaPatternDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SagaPatternDemoApplication.class, args);
    }
}
