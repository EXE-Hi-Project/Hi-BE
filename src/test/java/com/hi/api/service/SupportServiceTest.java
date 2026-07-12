package com.hi.api.service;

import com.hi.api.model.SupportMessage;
import com.hi.api.model.SupportTicket;
import com.hi.api.model.User;
import com.hi.api.repository.SupportMessageRepository;
import com.hi.api.repository.SupportTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupportServiceTest {
    private SupportTicketRepository tickets;
    private SupportMessageRepository messages;
    private NotificationService notifications;
    private RealtimeEventService realtime;
    private SupportService service;

    @BeforeEach void setUp() {
        tickets = mock(SupportTicketRepository.class); messages = mock(SupportMessageRepository.class);
        notifications = mock(NotificationService.class); realtime = mock(RealtimeEventService.class);
        service = new SupportService(tickets, messages, notifications, realtime, mock(MongoTemplate.class));
        when(messages.findByTicketIdOrderByCreatedAtAsc(anyString())).thenReturn(List.of());
        when(messages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tickets.save(any())).thenAnswer(invocation -> {
            SupportTicket ticket = invocation.getArgument(0); if (ticket.getId() == null) ticket.setId("ticket-1"); return ticket;
        });
    }

    @Test void createsTrimmedTicketAndFirstMessage() {
        User user = new User(); user.setId("user-1"); user.setName("Lan"); user.setEmail("lan@example.com");
        service.create(user, "  Lỗi đăng nhập  ", SupportTicket.Category.ACCOUNT, "  Tôi không thể đăng nhập vào Hi  ");
        verify(tickets).save(argThat(t -> t.getTitle().equals("Lỗi đăng nhập") && t.getStatus() == SupportTicket.Status.OPEN));
        verify(messages).save(argThat(m -> m.getContent().equals("Tôi không thể đăng nhập vào Hi") && m.getActor() == SupportMessage.Actor.USER));
    }

    @Test void hidesTicketOwnedByAnotherUser() {
        SupportTicket ticket = ticket(SupportTicket.Status.OPEN); ticket.setUserId("user-2");
        when(tickets.findById("ticket-1")).thenReturn(Optional.of(ticket));
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.detailMine("user-1", "ticket-1"));
        assertEquals(404, error.getStatusCode().value());
    }

    @Test void closedTicketRequiresReopenBeforeReply() {
        SupportTicket ticket = ticket(SupportTicket.Status.CLOSED);
        when(tickets.findById("ticket-1")).thenReturn(Optional.of(ticket));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.userReply("user-1", "ticket-1", "Tôi vẫn cần được hỗ trợ thêm"));
        assertEquals(409, error.getStatusCode().value());
        verify(messages, never()).save(any());
    }

    private SupportTicket ticket(SupportTicket.Status status) {
        SupportTicket ticket = new SupportTicket(); ticket.setId("ticket-1"); ticket.setUserId("user-1");
        ticket.setTicketCode("HI-12345678"); ticket.setStatus(status); return ticket;
    }
}
