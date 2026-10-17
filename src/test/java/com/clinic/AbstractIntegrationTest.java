package com.clinic;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        // Every table a test can write to, in one statement so their foreign keys to each other are
        // satisfied. TRUNCATE also bypasses the row-level append-only trigger on audit_log.
        jdbc.execute("TRUNCATE TABLE visit, patient, app_user, specialization, audit_log RESTART IDENTITY");
    }
}
