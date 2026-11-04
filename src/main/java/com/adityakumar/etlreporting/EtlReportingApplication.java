package com.adityakumar.etlreporting;

import com.adityakumar.etlreporting.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class EtlReportingApplication {

    public static void main(String[] args) {
        SpringApplication.run(EtlReportingApplication.class, args);
    }
}
