package com.sitech.prodai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.sitech.prodai", "org.example"})
public class ProdAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProdAiApplication.class, args);
    }
}
