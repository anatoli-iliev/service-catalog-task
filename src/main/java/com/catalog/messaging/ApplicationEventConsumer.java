package com.catalog.messaging;

import com.catalog.messaging.event.ApplicationEvent;
import com.catalog.service.ApplicationService;
import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ApplicationEventConsumer {

    private static final String TOPIC = "catalog.applications";

    private final ApplicationService applicationService;
    private final ObjectMapper objectMapper;
    private final Counter successCounter;
    private final Counter failureCounter;

    public ApplicationEventConsumer(ApplicationService applicationService,
                                    ObjectMapper objectMapper,
                                    MeterRegistry meterRegistry) {
        this.applicationService = applicationService;
        this.objectMapper = objectMapper;
        this.successCounter = Counter.builder("catalog.events.processed")
                .tag("topic", TOPIC).tag("status", "success")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("catalog.events.processed")
                .tag("topic", TOPIC).tag("status", "failure")
                .register(meterRegistry);
    }

    @KafkaListener(topics = TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String payload) {
        MDC.put("correlationId", UUID.randomUUID().toString());
        MDC.put("topic", TOPIC);
        try {
            ApplicationEvent event = objectMapper.readValue(payload, ApplicationEvent.class);
            MDC.put("eventId", event.applicationId());

            if (event.applicationId() == null || event.applicationId().isBlank()) {
                log.warn("Skipping application event with missing applicationId");
                failureCounter.increment();
                return;
            }
            log.info("Processing application event: externalId={}", event.applicationId());
            applicationService.upsert(
                    event.applicationId(),
                    event.name(),
                    event.description(),
                    event.repositoryUrl());
            successCounter.increment();
        } catch (Exception ex) {
            log.error("Failed to process application event", ex);
            failureCounter.increment();
        } finally {
            MDC.clear();
        }
    }
}
