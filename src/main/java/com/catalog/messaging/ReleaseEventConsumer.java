package com.catalog.messaging;

import com.catalog.messaging.event.ReleaseEvent;
import com.catalog.service.ReleaseService;
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
public class ReleaseEventConsumer {

    private static final String TOPIC = "catalog.releases";

    private final ReleaseService releaseService;
    private final ObjectMapper objectMapper;
    private final Counter successCounter;
    private final Counter failureCounter;

    public ReleaseEventConsumer(ReleaseService releaseService,
                                ObjectMapper objectMapper,
                                MeterRegistry meterRegistry) {
        this.releaseService = releaseService;
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
            ReleaseEvent event = objectMapper.readValue(payload, ReleaseEvent.class);
            MDC.put("eventId", event.releaseId());

            if (event.releaseId() == null || event.releaseId().isBlank()
                    || event.applicationId() == null || event.applicationId().isBlank()) {
                log.warn("Skipping release event with missing required fields");
                failureCounter.increment();
                return;
            }
            log.info("Processing release event: externalId={}, applicationId={}",
                    event.releaseId(), event.applicationId());
            releaseService.upsert(
                    event.releaseId(),
                    event.applicationId(),
                    event.version(),
                    event.ociReference(),
                    event.releaseDate());
            successCounter.increment();
        } catch (Exception ex) {
            log.error("Failed to process release event", ex);
            failureCounter.increment();
        } finally {
            MDC.clear();
        }
    }
}
