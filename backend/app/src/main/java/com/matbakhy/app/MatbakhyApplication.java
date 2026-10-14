package com.matbakhy.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.matbakhy")
public class MatbakhyApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatbakhyApplication.class, args);
    }
}
