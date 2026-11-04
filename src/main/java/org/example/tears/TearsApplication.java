package org.example.tears;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TearsApplication {

    public static void main(String[] args) {
        SpringApplication.run(TearsApplication.class, args);
    }

}
