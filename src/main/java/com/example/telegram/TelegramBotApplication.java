package com.example.telegram;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@SpringBootApplication
@EntityScan(basePackages = "com.model")
public class TelegramBotApplication {

    public TelegramBotApplication() throws TelegramApiException {
    }

    public static void main(String[] args) {
        SpringApplication.run(TelegramBotApplication.class, args);
    }
}
