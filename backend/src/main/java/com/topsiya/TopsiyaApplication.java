package com.topsiya;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TopsiyaApplication {

    public static void main(String[] args) {
        SpringApplication.run(TopsiyaApplication.class, args);
    }
}
