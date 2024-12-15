package com.example.telegram.repo;

import com.model.DeputyUsers;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeputyUsersRepo extends JpaRepository<DeputyUsers, Long> {
    DeputyUsers findByTelegramName(String name);
}
