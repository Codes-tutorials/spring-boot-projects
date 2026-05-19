package org.codeart.kafka.producer.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.codeart.kafka.model.LogEvent;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.MicrometerProducerListener;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration for log events.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class KafkaProducerConfig {

    private final KafkaProperties kafkaProperties;
    private final LogProducerProperties logProducerProperties;
    private final MeterRegistry meterRegistry;

    @Bean
    public ProducerFactory<String, LogEvent> producerFactory() {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties(null));

        // Ensure proper serializers
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Custom partitioner for service-based partitioning
        props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, ServiceNamePartitioner.class.getName());

        // Client ID for identification
        props.put(ProducerConfig.CLIENT_ID_CONFIG,
                "log-producer-" + logProducerProperties.getInstanceId());

        DefaultKafkaProducerFactory<String, LogEvent> factory = new DefaultKafkaProducerFactory<>(props);

        // Add Micrometer metrics listener
        factory.addListener(new MicrometerProducerListener<>(meterRegistry));

        log.info("Kafka producer factory created with instance ID: {}",
                logProducerProperties.getInstanceId());

        return factory;
    }

    @Bean
    public KafkaTemplate<String, LogEvent> kafkaTemplate(ProducerFactory<String, LogEvent> producerFactory) {
        KafkaTemplate<String, LogEvent> template = new KafkaTemplate<>(producerFactory);
        template.setObservationEnabled(true);
        return template;
    }
}
