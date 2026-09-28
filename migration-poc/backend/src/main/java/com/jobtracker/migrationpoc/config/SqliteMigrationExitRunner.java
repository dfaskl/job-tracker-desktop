package com.jobtracker.migrationpoc.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Order(1000)
public class SqliteMigrationExitRunner implements ApplicationRunner {
    private final Environment environment;
    private final ConfigurableApplicationContext context;

    public SqliteMigrationExitRunner(Environment environment, ConfigurableApplicationContext context) {
        this.environment = environment;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (Boolean.parseBoolean(environment.getProperty("SQLITE_MIGRATION_EXIT", "false"))) {
            SpringApplication.exit(context, () -> 0);
        }
    }
}
