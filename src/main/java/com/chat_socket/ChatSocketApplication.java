package com.chat_socket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ChatSocketApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatSocketApplication.class, args);
    }
}
