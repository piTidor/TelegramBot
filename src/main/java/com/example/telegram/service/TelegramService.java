package com.example.telegram.service;

import com.example.telegram.config.BotConfig;
import com.example.telegram.kafka.KafkaProducer;
import com.example.telegram.repo.*;
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

    @Autowired
    KafkaProducer kafkaProducer;

    @Autowired
    DeputyUsersRepo deputyUsersRepo;


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
        if (update.hasMessage()) {
            Message message = update.getMessage();
            long chatId = message.getChatId();
            Users users = usersRepository.findByTelegramName(message.getFrom().getUserName());
            if (users == null){
                DeputyUsers deputyUsers = deputyUsersRepo.findByTelegramName(message.getFrom().getUserName());
                if (deputyUsers == null || deputyUsers.getUser() == null){
                    sendTGMessage(chatId,"Не нашёл вас в списке разрещённых полбзователей, обратитесь к администратору");
                    return;
                }
                users = deputyUsers.getUser();
            }
            if (message.hasText() && message.getForwardFromChat() == null) {
                String text = message.getText();
                String send = "";
                List<VkGroup> vkGroups = vkGroupRepository.getAllByUserAndGroupName(users, text);
                if (vkGroups.isEmpty()){
                    send = "не найденно групп "+ text +" попробуйте ещё раз";
                } else {
                    TelegramLastMessage tg = TelegramLastMessage.builder()
                            .user(users)
                            .text(text)
                            .build();
                    telegramLastMessageRepo.save(tg);
                    send = "Посты будут публиковатся в " + text;
                }
                sendTGMessage(chatId, send);
            }
            else {
                String text = telegramLastMessageRepo.findTopByUser(users).getText();
                if (text == null){
                    return;
                }
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
                        //Пройтись циклом по messageEntities и добавить все url которые содержат /video/ в attach
                    }
                    if (message.getCaption() != null) {
                        caption = message.getCaption();
                    } else if (message.hasText()){
                        caption = message.getText();
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
                                .postingId(0)
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
        //Здесь напиши метод
        String filePath = "";
        return "photo_https://api.telegram.org/file/bot"+ botConfig.getToken() + "/" + filePath;
    }
    public String videoUpload(Message message){
        String filePath = "";
        //Метод должен вернуть ссылку на видео, если её вставит в бораузер она скачается
        return "video_https://api.telegram.org/file/bot"+ botConfig.getToken() + "/" + filePath;
    }
}
