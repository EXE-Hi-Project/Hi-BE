package com.hi.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendOtpEmail(String to, String name, String otp) {
        log.info("[EMAIL] Chuẩn bị gửi OTP đến: {}", to);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("Mã OTP đặt lại mật khẩu - Hi App");
        message.setText("Xin chào " + (name != null && !name.isBlank() ? name : "bạn") + ",\n\n" +
                "Mã OTP đặt lại mật khẩu của bạn là:\n\n" +
                "    " + otp + "\n\n" +
                "Mã này có hiệu lực trong 15 phút. Không chia sẻ mã này với bất kỳ ai.\n" +
                "Nếu bạn không yêu cầu đổi mật khẩu, vui lòng bỏ qua email này.\n\n" +
                "Trân trọng,\nĐội ngũ Hi Lover");
        try {
            mailSender.send(message);
            log.info("[EMAIL] Gửi OTP thành công đến: {}", to);
        } catch (MailException ex) {
            log.error("[EMAIL] THẤT BẠI khi gửi OTP đến {}: {}", to, ex.getMessage(), ex);
            throw ex;
        }
    }
}