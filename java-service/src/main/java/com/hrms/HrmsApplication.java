package com.hrms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HrmsApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(HrmsApplication.class, args);
        if (context.getEnvironment().matchesProfiles("parity")) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
