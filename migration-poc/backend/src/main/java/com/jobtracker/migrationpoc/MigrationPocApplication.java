package com.jobtracker.migrationpoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MigrationPocApplication {
    public static void main(String[] args) {
        SpringApplication.run(MigrationPocApplication.class, args);
    }
}
