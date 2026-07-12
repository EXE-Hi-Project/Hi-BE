package com.hi.api.repository;

import com.hi.api.model.SupportTicket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SupportTicketRepository extends MongoRepository<SupportTicket, String> {
    Page<SupportTicket> findByUserIdOrderByLastMessageAtDesc(String userId, Pageable pageable);
    Page<SupportTicket> findByUserIdAndStatusOrderByLastMessageAtDesc(String userId, SupportTicket.Status status, Pageable pageable);
}
