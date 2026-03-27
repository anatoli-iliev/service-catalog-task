package com.catalog.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.catalog.repository.ApplicationRepository;
import com.catalog.repository.ReleaseRepository;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end test: Kafka JSON event -> Consumer -> Service -> DB -> REST API.
 * Uses @EmbeddedKafka (in-process broker, no Docker needed).
 * Sends plain JSON strings — same as external producers would.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@EmbeddedKafka(
        topics = {"catalog.applications", "catalog.releases"},
        partitions = 1,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class EndToEndKafkaTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ReleaseRepository releaseRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        releaseRepository.deleteAll();
        applicationRepository.deleteAll();

        var producerFactory = new DefaultKafkaProducerFactory<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
        kafkaTemplate = new KafkaTemplate<>(producerFactory);
    }

    @Test
    void applicationEvent_persistedAndQueryableViaApi() throws Exception {
        kafkaTemplate.send("catalog.applications", """
                {"applicationId":"e2e-app-1","name":"postgresql","description":"Relational Database","repositoryUrl":"https://github.com/bitnami/containers/postgresql"}""");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(applicationRepository.findByExternalApplicationId("e2e-app-1")).isPresent());

        mockMvc.perform(get("/api/v1/applications/e2e-app-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value("e2e-app-1"))
                .andExpect(jsonPath("$.name").value("postgresql"))
                .andExpect(jsonPath("$.description").value("Relational Database"));
    }

    @Test
    void releaseEvent_createsGhostThenPromoted() throws Exception {
        kafkaTemplate.send("catalog.releases", """
                {"releaseId":"e2e-rel-1","applicationId":"e2e-app-ghost","version":"1.0.0","ociReference":"registry.io/app:1.0.0","releaseDate":"2026-01-15T10:00:00Z"}""");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(releaseRepository.findByExternalReleaseId("e2e-rel-1")).isPresent());

        mockMvc.perform(get("/api/v1/applications/e2e-app-ghost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value("e2e-app-ghost"))
                .andExpect(jsonPath("$.name").doesNotExist());

        kafkaTemplate.send("catalog.applications", """
                {"applicationId":"e2e-app-ghost","name":"Ghost Promoted","description":"Now real","repositoryUrl":"https://github.com/org/app"}""");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var app = applicationRepository.findByExternalApplicationId("e2e-app-ghost").orElseThrow();
            assertThat(app.getName()).isEqualTo("Ghost Promoted");
        });

        mockMvc.perform(get("/api/v1/applications/e2e-app-ghost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ghost Promoted"))
                .andExpect(jsonPath("$.description").value("Now real"));

        mockMvc.perform(get("/api/v1/applications/e2e-app-ghost/releases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].releaseId").value("e2e-rel-1"))
                .andExpect(jsonPath("$.content[0].version").value("1.0.0"));
    }

    @Test
    void multipleReleases_sortedBySemVerViaApi() throws Exception {
        kafkaTemplate.send("catalog.applications", """
                {"applicationId":"e2e-app-sort","name":"Sort App","description":"Test","repositoryUrl":"https://repo.url"}""");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(applicationRepository.findByExternalApplicationId("e2e-app-sort")).isPresent());

        kafkaTemplate.send("catalog.releases", """
                {"releaseId":"e2e-s1","applicationId":"e2e-app-sort","version":"1.0.0","ociReference":"r/a:1.0.0","releaseDate":"2026-01-15T10:00:00Z"}""");
        kafkaTemplate.send("catalog.releases", """
                {"releaseId":"e2e-s2","applicationId":"e2e-app-sort","version":"2.1.0","ociReference":"r/a:2.1.0","releaseDate":"2026-01-15T10:00:00Z"}""");
        kafkaTemplate.send("catalog.releases", """
                {"releaseId":"e2e-s3","applicationId":"e2e-app-sort","version":"1.10.0","ociReference":"r/a:1.10.0","releaseDate":"2026-01-15T10:00:00Z"}""");
        kafkaTemplate.send("catalog.releases", """
                {"releaseId":"e2e-s4","applicationId":"e2e-app-sort","version":"1.9.0","ociReference":"r/a:1.9.0","releaseDate":"2026-01-15T10:00:00Z"}""");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(releaseRepository.findByApplicationExternalId("e2e-app-sort",
                        PageRequest.of(0, 10)).getTotalElements()).isEqualTo(4));

        mockMvc.perform(get("/api/v1/applications/e2e-app-sort/releases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].version").value("2.1.0"))
                .andExpect(jsonPath("$.content[1].version").value("1.10.0"))
                .andExpect(jsonPath("$.content[2].version").value("1.9.0"))
                .andExpect(jsonPath("$.content[3].version").value("1.0.0"));
    }

    @Test
    void duplicateEvents_handledIdempotently() throws Exception {
        String payload = """
                {"applicationId":"e2e-app-idem","name":"App","description":"Desc","repositoryUrl":"https://repo.url"}""";

        kafkaTemplate.send("catalog.applications", payload);
        kafkaTemplate.send("catalog.applications", payload);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(applicationRepository.findByExternalApplicationId("e2e-app-idem")).isPresent());

        Thread.sleep(2000);

        long count = applicationRepository.findAll().stream()
                .filter(a -> "e2e-app-idem".equals(a.getExternalApplicationId()))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void correlationIdHeader_echoedInRestResponse() throws Exception {
        kafkaTemplate.send("catalog.applications", """
                {"applicationId":"e2e-app-corr","name":"Corr Test","description":"Test","repositoryUrl":"https://repo.url"}""");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(applicationRepository.findByExternalApplicationId("e2e-app-corr")).isPresent());

        mockMvc.perform(get("/api/v1/applications/e2e-app-corr")
                        .header("X-Correlation-ID", "test-trace-abc"))
                .andExpect(status().isOk())
                .andExpect(result ->
                        assertThat(result.getResponse().getHeader("X-Correlation-ID"))
                                .isEqualTo("test-trace-abc"));
    }
}
