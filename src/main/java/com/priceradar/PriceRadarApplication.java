package com.priceradar;

import com.priceradar.configuration.PriceRadarPolicyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PriceRadarPolicyProperties.class)
public class PriceRadarApplication {

    public static void main(String[] args) {
        SpringApplication.run(PriceRadarApplication.class, args);
    }
}
