package com.hi.api.repository;

import com.hi.api.model.HealthVideo;
import com.hi.api.model.HealthVideoStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface HealthVideoRepository extends MongoRepository<HealthVideo, Long> {
    Optional<HealthVideo> findByYoutubeVideoId(String youtubeVideoId);
    List<HealthVideo> findByStatusOrderByPriorityDescTitleAsc(HealthVideoStatus status);
}
