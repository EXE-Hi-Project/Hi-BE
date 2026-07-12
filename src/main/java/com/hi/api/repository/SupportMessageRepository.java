package com.hi.api.repository;

import com.hi.api.model.SupportMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface SupportMessageRepository extends MongoRepository<SupportMessage, String> {
    List<SupportMessage> findByTicketIdOrderByCreatedAtAsc(String ticketId);
}
