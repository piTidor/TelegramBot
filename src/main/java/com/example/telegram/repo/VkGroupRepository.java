package com.example.telegram.repo;

import com.model.Users;
import com.model.VkGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VkGroupRepository extends JpaRepository<VkGroup, Long> {
    List<VkGroup> getAllByUserAndGroupName(Users users, String name);
}
