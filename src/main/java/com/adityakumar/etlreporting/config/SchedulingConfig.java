package com.adityakumar.etlreporting.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled jobs unless app.etl.scheduling.enabled=false (tests switch it off). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.etl.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
