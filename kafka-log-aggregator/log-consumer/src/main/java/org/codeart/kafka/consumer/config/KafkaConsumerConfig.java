package org.codeart.kafka.consumer.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.codeart.kafka.model.LogEvent;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.MicrometerConsumerListener;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration with:
 * - Concurrent listener containers for parallel processing
 * - Dead letter topic for failed messages
 * - Metrics integration
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfig {

    private final KafkaProperties kafkaProperties;
    private final LogConsumerProperties consumerProperties;
    private final MeterRegistry meterRegistry;

    @Bean
    public ConsumerFactory<String, LogEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));

        // Configure deserializers
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // Client ID for identification
        props.put(ConsumerConfig.CLIENT_ID_CONFIG,
                "log-consumer-" + consumerProperties.getInstanceId());

        // JSON deserializer config
        JsonDeserializer<LogEvent> deserializer = new JsonDeserializer<>(LogEvent.class);
        deserializer.addTrustedPackages("org.codeart.kafka.model");
        deserializer.setUseTypeMapperForKey(false);

        DefaultKafkaConsumerFactory<String, LogEvent> factory = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), deserializer);

        // Add Micrometer metrics listener
        factory.addListener(new MicrometerConsumerListener<>(meterRegistry));

        log.info("Kafka consumer factory created with instance ID: {}",
                consumerProperties.getInstanceId());

        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, LogEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, LogEvent> consumerFactory,
            KafkaTemplate<String, LogEvent> dltKafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory<String, LogEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);

        // Enable batch consumption
        factory.setBatchListener(true);

        // Set error handler with dead letter topic
        if (consumerProperties.isDeadLetterEnabled()) {
            factory.setCommonErrorHandler(errorHandler(dltKafkaTemplate));
        }

        // Observation for metrics
        factory.getContainerProperties().setObservationEnabled(true);

        log.info("Kafka listener container factory configured with batch mode");

        return factory;
    }

    /**
     * Error handler that sends failed messages to dead letter topic.
     */
    @Bean
    public CommonErrorHandler errorHandler(KafkaTemplate<String, LogEvent> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> {
                    log.error("Sending to DLT: topic={}, key={}, error={}",
                            record.topic(), record.key(), exception.getMessage());
                    return new org.apache.kafka.common.TopicPartition(
                            record.topic() + ".DLT", record.partition());
                });

        // Retry with fixed backoff before sending to DLT
        FixedBackOff backOff = new FixedBackOff(1000L, consumerProperties.getMaxRetries());

        return new DefaultErrorHandler(recoverer, backOff);
    }

    /**
     * Kafka template for sending to dead letter topic.
     */
    @Bean
    public KafkaTemplate<String, LogEvent> dltKafkaTemplate(
            org.springframework.kafka.core.ProducerFactory<String, LogEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * Producer factory for DLT (using same config as consumer's origin).
     */
    @Bean
    public org.springframework.kafka.core.ProducerFactory<String, LogEvent> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaProperties.getBootstrapServers());
        props.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class);
        props.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.springframework.kafka.support.serializer.JsonSerializer.class);

        return new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(props);
    }
}
