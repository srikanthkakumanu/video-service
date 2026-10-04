package videos.infrastructure.persistence;

import videos.domain.model.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Testcontainers
@DataJpaTest(properties = {
    "spring.config.import=", "spring.cloud.vault.enabled=false", "spring.cloud.config.enabled=false",
    "spring.datasource.username=theuser", "spring.datasource.password=theuser",
    "spring.flyway.user=videoadmin", "spring.flyway.password=videoadmin", "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({JpaVideoStore.class, VideoPersistenceMapperImpl.class})
class VideoPersistenceTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18")
        .withDatabaseName("videodb").withUsername("root").withPassword("root").withInitScript("postgres-test-users.sql");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) { properties.add("spring.datasource.url", postgres::getJdbcUrl); }
    @Autowired JpaVideoStore videos;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test
    void video_shouldRoundTripOwnerAndApplyDatabaseFilters() {
        var actor = new VideoActor(UUID.randomUUID(), false);
        var saved = videos.save(Video.create(new VideoChanges("Example", "Description", null, null, false), actor));
        entityManager.flush(); entityManager.clear();
        var found = videos.findById(saved.id()).orElseThrow();
        assertThat(found.userId()).isEqualTo(actor.userId());
        assertThat(found.created()).isNotNull();
        videos.save(found.revise(new VideoChanges(null, null, null, null, true), actor));
        entityManager.flush(); entityManager.clear();
        var page = videos.find(new VideoQuery(null, "example", true, 0, 10));
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.content().getFirst().completed()).isTrue();
    }
}
