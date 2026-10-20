package com.omsai.coaching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Om Sai Coaching ERP - Phase 1 application entry point.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CoachingErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoachingErpApplication.class, args);
    }
}
