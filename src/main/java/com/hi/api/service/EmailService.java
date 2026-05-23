package com.hi.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordResetEmail(String to, String resetCode) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("Mã khôi phục mật khẩu - Hi App");
        message.setText("Xin chào,\n\n" +
                "Bạn đã yêu cầu đặt lại mật khẩu cho tài khoản Hi App của mình.\n" +
                "Mã xác nhận của bạn là: " + resetCode + "\n\n" +
                "Mã này sẽ hết hạn trong 15 phút. Vui lòng không chia sẻ mã này với bất kỳ ai.\n\n" +
                "Trân trọng,\nĐội ngũ Hi App");

        mailSender.send(message);
    }
}