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
import java.util.*;

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

    // Хранит временные данные для групп сообщений
    private final Map<String, List<String>> mediaGroupMap = new HashMap<>();
    private final Map<String, Long> chatIdMap = new HashMap<>();
    private final Map<String, String> captionMap = new HashMap<>();

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
        if (update.hasMessage()) {
            Message message = update.getMessage();
            long chatId = message.getChatId();
            String mediaGroupId = message.getMediaGroupId();
            Users users = usersRepository.findByTelegramName(message.getFrom().getUserName());
            if (users == null) {
                DeputyUsers deputyUsers = deputyUsersRepo.findByTelegramName(message.getFrom().getUserName());
                if (deputyUsers == null || deputyUsers.getUser() == null) {
                    sendTGMessage(chatId, "Не нашёл вас в списке разрешённых пользователей, обратитесь к администратору");
                    return;
                }
                users = deputyUsers.getUser();
            }
            if (mediaGroupId != null) {
                processMediaGroup(message, mediaGroupId, chatId, users);
            } else {
                processSingleMessage(message, chatId, users);
            }
        }
    }

    private void processMediaGroup(Message message, String mediaGroupId, long chatId, Users users) {
        chatIdMap.putIfAbsent(mediaGroupId, chatId);

        if (message.getCaption() != null) {
            captionMap.put(mediaGroupId, message.getCaption());
        }

        List<String> attachments = mediaGroupMap.computeIfAbsent(mediaGroupId, k -> new ArrayList<>());
        if (message.hasPhoto()) {
            attachments.add(photoUpload(message));
        } else if (message.hasVideo()) {
            attachments.add(videoUpload(message));
        }
        if (message.hasEntities()){
            EntityUpload(message.getEntities(), attachments);
        }

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                sendMediaGroup(mediaGroupId, users);
            }
        }, 500); // 500 мс задержки
    }

    private void sendMediaGroup(String mediaGroupId, Users users) {
        List<String> attachments = mediaGroupMap.remove(mediaGroupId);
        Long chatId = chatIdMap.remove(mediaGroupId);
        String caption = captionMap.remove(mediaGroupId);

        if (attachments == null || chatId == null) return;

        String text = telegramLastMessageRepo.findTopByUser(users).getText();
        if (text == null) return;

        List<VkGroup> vkGroups = vkGroupRepository.getAllByUserAndGroupName(users, text);
        if (vkGroups.isEmpty()) return;

        for (VkGroup vkGroup : vkGroups) {
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
                    .attachment(attachments)
                    .postId(savedPost.getId())
                    .userId(users.getVkId())
                    .build();

            kafkaProducer.sendMessage("url_post", urlPost.toJson());
            System.out.println(urlPost.toJson());
            sendTGMessage(chatId, "Опубликовано в " + text);
        }
    }

    private void processSingleMessage(Message message, long chatId, Users users) {
        String text = telegramLastMessageRepo.findTopByUser(users).getText();
        if (text == null) return;

        List<VkGroup> vkGroups = vkGroupRepository.getAllByUserAndGroupName(users, text);
        if (vkGroups.isEmpty()) return;

        for (VkGroup vkGroup : vkGroups) {
            String caption = null;
            List<String> attach = new ArrayList<>();
            if (message.hasPhoto()) {
                attach.add(photoUpload(message));
            }
            if (message.hasVideo()) {
                attach.add(videoUpload(message));
            }
            if (message.getCaption() != null) {
                caption = message.getCaption();
            } else if (message.hasText()) {
                caption = message.getText();
            }
            if (message.hasEntities()){
                EntityUpload(message.getEntities(), attach);
            }

            if (attach.isEmpty()) return;

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

        kafkaProducer.sendMessage("url_post", urlPost.toJson());
            System.out.println(urlPost.toJson());
            sendTGMessage(chatId, "Опубликовано в " + text);
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
    public void EntityUpload( List<MessageEntity> entityList , List<String> attach){
        for(MessageEntity messageEntity : entityList){
            String url = messageEntity.getUrl();
            if (url == null){
                continue;
            }
            if (url.contains("/video/")){
                attach.add("video_"+url);
            }
            else if (url.contains("/photo/")){
                attach.add("photo_"+url);
            }
        }
    }

    public String photoUpload(Message message){
        List<PhotoSize> photos = message.getPhoto();
        message.getMediaGroupId();
        String fileId = photos.get(photos.size() - 1).getFileId();
        String filePath = "";
        try {
            GetFile getFileMethod = new GetFile(fileId);
            File file = execute(getFileMethod); // execute() — метод из TelegramLongPollingBot
            filePath = file.getFilePath();
        } catch (TelegramApiException e) {
            e.printStackTrace();
            return null;
        }
        return "photo_https://api.telegram.org/file/bot"+ botConfig.getToken() + "/" + filePath;
    }
    public String videoUpload(Message message){
        Video video = message.getVideo();
        String fileId = video.getFileId();
        String filePath = "";
        try {
        GetFile getFileMethod = new GetFile(fileId);
        File file = execute(getFileMethod);
        filePath = file.getFilePath();
        } catch (TelegramApiException e) {
            e.printStackTrace();
            return null;
        }
        return "video_https://api.telegram.org/file/bot"+ botConfig.getToken() + "/" + filePath;
    }
}
