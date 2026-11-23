package com.spendlens.config;

import com.spendlens.statement.StatementService;
import com.spendlens.user.User;
import com.spendlens.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** On first start creates demo@spendlens.dev / demo12345 with the bundled sample monobank statement. */
@Component
@ConditionalOnBooleanProperty("app.seed-demo-data")
public class DemoDataLoader implements ApplicationRunner {

    public static final String DEMO_EMAIL = "demo@spendlens.dev";
    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final UserRepository users;
    private final StatementService statements;
    private final PasswordEncoder passwordEncoder;

    public DemoDataLoader(UserRepository users, StatementService statements, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.statements = statements;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (users.existsByEmailIgnoreCase(DEMO_EMAIL)) {
            return;
        }
        User demo = users.save(new User(DEMO_EMAIL, passwordEncoder.encode("demo12345"), "Demo User"));
        String csv = new ClassPathResource("samples/monobank-sample.csv").getContentAsString(StandardCharsets.UTF_8);
        statements.upload(demo.getId(), "monobank-sample.csv", csv);
        log.info("Demo account created: {} / demo12345", DEMO_EMAIL);
    }
}
