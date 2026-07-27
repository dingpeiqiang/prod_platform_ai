package com.sitech.prodai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProdAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProdAiApplication.class, args);
    }
}
