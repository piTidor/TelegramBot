package com.example.telegram.repo;

import com.model.TelegramLastMessage;
import com.model.Users;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramLastMessageRepo extends JpaRepository<TelegramLastMessage, Long> {
    TelegramLastMessage findTopByUser(Users users);
}
