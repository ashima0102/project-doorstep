package com.azentio.doorstep.ingest;

import com.azentio.doorstep.service.ConflictException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
@ConditionalOnProperty(name = "doorstep.kafka.enabled", havingValue = "true")
public class KafkaIngestionConfig {

    @Bean
    NewTopic applicationsTopic(@Value("${doorstep.kafka.applications-topic}") String topic) {
        return TopicBuilder.name(topic).partitions(3).build();
    }

    @Bean
    NewTopic applicationsDeadLetterTopic(@Value("${doorstep.kafka.applications-topic}") String topic) {
        return TopicBuilder.name(topic + ".DLT").partitions(3).build();
    }

    @Bean
    NewTopic decisionsTopic(@Value("${doorstep.kafka.decisions-topic}") String topic) {
        return TopicBuilder.name(topic).partitions(3).build();
    }

    /**
     * Malformed/invalid events and conflicting redeliveries go straight to {topic}.DLT. Anything else
     * (e.g. database down) is retried 3 times with exponential backoff, then dead-lettered. The offset is
     * committed only after the listener succeeds or the record has been published to the DLT.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template, MeterRegistry meters) {
        Counter deadLettered = meters.counter("doorstep.ingest.dlt");
        DeadLetterPublishingRecoverer dlt = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1));
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(200);
        backOff.setMultiplier(2.0);
        DefaultErrorHandler handler = new DefaultErrorHandler((record, ex) -> {
            deadLettered.increment();
            dlt.accept(record, ex);
        }, backOff);
        handler.addNotRetryableExceptions(MalformedEventException.class, ConflictException.class);
        return handler;
    }
}
