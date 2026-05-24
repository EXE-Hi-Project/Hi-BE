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

    public void sendPasswordResetEmail(String to, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("Đặt lại mật khẩu - Hi App");
        message.setText("Xin chào,\n\n" +
                "Bạn vừa yêu cầu đặt lại mật khẩu cho tài khoản Hi App.\n" +
                "Vui lòng truy cập đường dẫn dưới đây để tạo mật khẩu mới:\n\n" +
                resetLink + "\n\n" +
                "Đường dẫn này sẽ hết hạn trong 15 phút. Nếu bạn không yêu cầu đổi mật khẩu, vui lòng bỏ qua email này.\n\n" +
                "Trân trọng,\nĐội ngũ Hi App");

        mailSender.send(message);
    }
}