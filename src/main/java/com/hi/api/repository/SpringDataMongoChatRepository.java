package com.hi.api.repository;

import com.hi.api.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataMongoChatRepository extends MongoRepository<ChatMessage, String> {

    List<String> findDistinctUserIdBy();

    List<ChatMessage> findByUserIdOrderByCreatedAtAsc(String userId, Pageable pageable);

    // Lấy danh sách tin nhắn theo userId (thay cho conversationId), sắp xếp mới nhất lên đầu và giới hạn số lượng (Pageable)
    List<ChatMessage> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    // Xóa toàn bộ tin nhắn thuộc một user
    void deleteByUserId(String userId);
}
