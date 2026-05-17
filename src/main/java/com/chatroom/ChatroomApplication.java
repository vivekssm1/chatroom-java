package com.chatroom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ChatRoom — Java equivalent of server.js
 * Run with: mvn spring-boot:run
 * Or build: mvn package  →  java -jar target/chatroom-1.0.0.jar
 */
@SpringBootApplication
public class ChatroomApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatroomApplication.class, args);
    }
}
