package com.edutrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EduTrackApplication {
    public static void main(String[] args) {
        SpringApplication.run(EduTrackApplication.class, args);
    }
}
