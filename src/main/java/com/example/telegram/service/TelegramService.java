package com.example.telegram.service;

import com.example.telegram.config.BotConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

@Component
public class TelegramService extends TelegramLongPollingBot {
    @Autowired
    BotConfig botConfig;


    @Override
    public String getBotUsername() {
        return botConfig.getBotName();
    }

    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        String url = null;
        List<String> attach = new ArrayList<>();
        if (update.hasMessage()) {
            Message message = update.getMessage();
            if (message.hasPhoto()) {
                url = photoUpload(message);
                System.out.println(url);
                attach.add(url);
            }
            if (message.hasVideo()) {
                url = videoUpload(message);
                System.out.println(url);
                attach.add(url);
            }
            if (message.hasEntities()){
                List<MessageEntity> messageEntities = message.getEntities();
                //в цикле пройтись по листу messageEntities из него получить url которые содержат "/video/" или "/photo/" и ими заполнить лист attach
                //если url содержит "/video/" то конечная строка должна выглядеть video_http://cdn-cf-east.streamable.com/video/mp4/...
                //если фото то photo_http://...
            }
            if (message.hasText() || message.getCaption()!= null) {
                String text = message.getText();
                if (text == null){
                    text = message.getCaption();
                }
                System.out.println("Текст сообщения: " + text);
            }

        }

    }

    public String photoUpload(Message message){
        List<PhotoSize> photos = message.getPhoto();
        String fileId = photos.get(photos.size() - 1).getFileId();
        String filePath = "";
        try {
            GetFile getFileMethod = new GetFile(fileId);
            File file = execute(getFileMethod); // execute() — метод из TelegramLongPollingBot
            filePath = file.getFilePath();
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
        return "photo_https://api.telegram.org/file/bot"+ botConfig.getToken() + "/" + filePath;
    }
    public String videoUpload(Message message){
        String filePath = "";
        //Метод должен вернуть ссылку на видео, если её вставит в бораузер она скачается
        return "video_https://api.telegram.org/file/bot"+ botConfig.getToken() + "/" + filePath;
    }
}
