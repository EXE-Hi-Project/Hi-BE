package com.hi.api.service;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {
    @Test
    void symptomReminderUsesResendTransactionalDelivery() {
        ResendOtpEmailService resend = mock(ResendOtpEmailService.class);
        when(resend.sendTransactional(eq("user@example.com"), eq("Hi nhắc bạn ghi triệu chứng hôm nay"), contains("Ghi triệu chứng")))
                .thenReturn("resend-reminder-1");
        EmailService service = new EmailService(resend);

        service.sendSymptomReminderEmail("user@example.com", "Linh", "Ghi triệu chứng hôm nay nhé", false);

        verify(resend).sendTransactional(eq("user@example.com"), eq("Hi nhắc bạn ghi triệu chứng hôm nay"), contains("Ghi triệu chứng"));
    }
}
