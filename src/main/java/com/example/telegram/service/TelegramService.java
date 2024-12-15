package com.example.telegram.service;

import com.example.telegram.config.BotConfig;
import com.example.telegram.repo.PublishPostRepo;
import com.example.telegram.repo.TelegramLastMessageRepo;
import com.example.telegram.repo.UsersRepository;
import com.example.telegram.repo.VkGroupRepository;
import com.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
public class TelegramService extends TelegramLongPollingBot {
    @Autowired
    BotConfig botConfig;

    @Autowired
    PublishPostRepo publishPostRepo;

    @Autowired
    UsersRepository usersRepository;

    @Autowired
    VkGroupRepository vkGroupRepository;

    @Autowired
    TelegramLastMessageRepo telegramLastMessageRepo;


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
        List<String> attach = new ArrayList<>();
        String caption = null;
        boolean ok = true;
        Chat chat = null;
        if (update.hasMessage()) {
            Message message = update.getMessage();
            long chatId = message.getChatId();
            Users users = usersRepository.findByTelegramName(message.getFrom().getUserName());
            if (users == null){
                return;
            }
            if (message.hasText() ) {
                String text = message.getText();
                TelegramLastMessage tg = TelegramLastMessage.builder()
                                .user(users)
                                .text(text)
                                        .build();
                telegramLastMessageRepo.save(tg);
                List<VkGroup> vkGroups = vkGroupRepository.getAllByUserAndGroupName(users, text);
                if (vkGroups.isEmpty()){
                    text = "не найденно групп. попробуйте ещё раз";
                }
                sendTGMessage(chatId, "Посты будут публиковатся в " + text);
                return;
            }
            else {
                String text = telegramLastMessageRepo.findTopByUser(users).getText();
                List<VkGroup> vkGroups = vkGroupRepository.getAllByUserAndGroupName(users, text);
                if (vkGroups.isEmpty()){
                    return;
                }
                for (VkGroup vkGroup : vkGroups) {
                    if (message.hasPhoto()) {
                        String url = photoUpload(message);
                        System.out.println(url);
                        attach.add(url);
                    }
                    if (message.hasVideo()) {
                        String url = videoUpload(message);
                        System.out.println(url);
                        attach.add(url);
                    }
                    if (message.hasEntities()) {
                        List<MessageEntity> messageEntities = message.getEntities();
                        //в цикле пройтись по листу messageEntities из него получить url которые содержат "/video/" или "/photo/" и ими заполнить лист attach
                        //если url содержит "/video/" то конечная строка должна выглядеть video_http://cdn-cf-east.streamable.com/video/mp4/...
                        //если фото то photo_http://...
                    }
                    if (message.getCaption() != null) {
                        caption = message.getCaption();
                    }
                    if (message.getForwardFromChat() != null) {
                        chat = message.getForwardFromChat();
                    } else {
                        ok = false;
                    }
                    if (attach.isEmpty()) {
                        ok = false;
                    }
                    if (ok) {
                        LocalDateTime now = LocalDateTime.now(ZoneId.of("Europe/Moscow"));
                        PublishPost publishPost = PublishPost.builder()
                                .date(now.toString())
                                .status("telegram")
                                .build();
                        publishPost.setNewText(caption);
                        publishPost.setOldText(caption);
                        PublishPost savedPost = publishPostRepo.save(publishPost);
                        UrlPost urlPost = UrlPost.builder()
                                .text(caption)
                                .vkGroupId(vkGroup.getVkId())
                                .postingId(chat.getId())
                                .attachment(attach)
                                .postId(savedPost.getId())
                                .userId(users.getVkId())
                                .build();
                        sendTGMessage(chatId, "Опубликованно в " + text);
                    }
                }
            }
        }

    }
    private boolean sendTGMessage(long id , String text){
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(id));
        sendMessage.setText(text);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            return false;
        }
        return true;

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
