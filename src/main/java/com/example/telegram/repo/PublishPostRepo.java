package com.example.telegram.repo;

import com.model.PostingGroup;
import com.model.PublishPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PublishPostRepo extends JpaRepository<PublishPost, Long> {
    PublishPost findByIdInGroup(String idInGroup);
    List<PublishPost> findTop100ByPostingGroupOrderByIdDesc(PostingGroup postingGroup);
}
