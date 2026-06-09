package com.hi.api.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Gửi mã OTP xác nhận quên mật khẩu dưới dạng giao diện HTML cao cấp
     */
    @Async("mailExecutor")
    public void sendOtpEmail(String to, String name, String otp) {
        String displayName = name != null && !name.isBlank() ? name : "bạn";
        String heading = "Chào " + displayName + " thân thương,";
        String body = "<p style=\"margin: 0 0 15px 0;\">Hi nghe nói bạn đang cần lấy lại mật khẩu để vào trò chuyện cùng Hi đúng không nè? Đừng lo lắng nha, Hi đã chuẩn bị sẵn mã OTP cho bạn rồi đây:</p>" +
                "<div style=\"text-align: center; margin: 30px 0;\">" +
                "  <span style=\"display: inline-block; background-color: #fff0f2; border: 2px dashed #ff758c; color: #ff758c; font-size: 32px; font-weight: 700; padding: 12px 35px; letter-spacing: 5px; border-radius: 16px;\">" +
                otp +
                "  </span>" +
                "</div>" +
                "<p style=\"color: #e15b64; font-weight: 600; margin: 0 0 15px 0;\">Mã này chỉ có hiệu lực trong vòng 15 phút thôi, và bạn nhớ giữ bí mật đừng chia sẻ với ai khác nhé!</p>" +
                "<p style=\"margin: 0;\">Nếu không phải bạn yêu cầu đổi mật khẩu thì cứ yên tâm bỏ qua email này nha, mật khẩu của bạn vẫn an toàn tuyệt đối. Hi đợi bạn ở ứng dụng nha!</p>";

        String html = buildHtmlTemplate(
                "Mã OTP đặt lại mật khẩu - Hi App",
                heading,
                body,
                null,
                null,
                to,
                "Thương bạn,"
        );

        sendRequiredEmail(to, "Mã OTP đặt lại mật khẩu - Hi App", html, true);
    }

    /**
     * Email hỏi thăm sức khỏe hằng ngày
     */
    @Async("mailExecutor")
    public void sendDailyCheckInEmail(String to, String name, String message) {
        String heading = "Chào " + name + " nè,";
        String body = "<p style=\"margin: 0 0 20px 0; font-size: 16px; line-height: 1.6;\">" +
                "Hôm nay của bạn thế nào rồi? Có điều gì vui hay chút mệt mỏi muốn nhỏ to tâm sự cùng Hi không?</p>" +
                "<p style=\"margin: 0 0 20px 0; font-size: 16px; line-height: 1.6;\">" +
                "Chỉ cần một vài giây chạm nhẹ thôi, kể cho Hi nghe bạn đang cảm thấy thế nào hôm nay nhé. Hi luôn ở đây lắng nghe và chuẩn bị sẵn những gợi ý chăm sóc siêu ngọt ngào dành riêng cho bạn đó!</p>";

        String html = buildHtmlTemplate(
                "Hi hỏi thăm bạn hôm nay",
                heading,
                body,
                "Nhỏ to cùng Hi nha",
                "https://hilover.space/notifications",
                to,
                "Thương bạn,"
        );

        sendRequiredEmail(to, "Hi hỏi thăm bạn hôm nay", html, true);
    }

    /**
     * Email nhắc nhở đối tác quan tâm hằng ngày
     */
    @Async("mailExecutor")
    public void sendPartnerDailyCheckInEmail(String to, String name, String partnerName, String message) {
        String heading = "Ting ting! Chào " + name + " nha,";
        String body = "<p style=\"margin: 0 0 20px 0; font-size: 16px; line-height: 1.6;\">" +
                "Hôm nay bạn đã hỏi thăm <strong>" + partnerName + "</strong> chưa nè?</p>" +
                "<p style=\"margin: 0 0 20px 0; font-size: 16px; line-height: 1.6;\">" +
                "Giữa bộn bề cuộc sống, đôi khi chỉ một lời nhắn ngắn ngủi 'Hôm nay thế nào rồi?' hay một cái ôm ấm áp cũng đủ làm người ấy mỉm cười cả ngày rồi đó. Hãy gửi một lời quan tâm thật dễ thương đến người ấy hôm nay nhé. Cùng Hi giữ lửa yêu thương nha!</p>";

        String html = buildHtmlTemplate(
                "Hi nhắc bạn quan tâm Người ấy",
                heading,
                body,
                "Gửi lời yêu thương ngay",
                "https://hilover.space/notifications",
                to,
                "Yêu hai bạn nhiều,"
        );

        sendRequiredEmail(to, "Hi nhắc bạn quan tâm Người ấy", html, true);
    }

    /**
     * Email nhắc nhở kỳ kinh sắp tới
     */
    @Async("mailExecutor")
    public void sendPeriodUpcomingEmail(String to, String name, int daysBefore, LocalDate nextPeriodStartDate) {
        String heading = "Chào " + name + " yêu dấu,";
        String formattedDate = nextPeriodStartDate != null ? nextPeriodStartDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "";
        String dateText = nextPeriodStartDate != null ? " (tầm ngày " + formattedDate + " nè)" : "";
        
        String body = "<p style=\"margin: 0 0 20px 0; font-size: 16px; line-height: 1.6;\">" +
                "Hi ghé tai nhắc nhỏ nè: khoảng <strong style=\"color: #ff758c; font-size: 18px;\">" + daysBefore + " ngày</strong> nữa là 'ngày đèn đỏ' của bạn dự kiến ghé thăm rồi đó" + dateText + ".</p>" +
                "<p style=\"margin: 0 0 20px 0; font-size: 15px; line-height: 1.6;\">" +
                "Cơ thể tụi mình sắp bước vào những ngày nhạy cảm rồi, bạn nhớ chuẩn bị sẵn 'bảo bối' (băng vệ sinh, đồ ấm) và uống nhiều nước ấm nha. Đừng làm việc quá sức, hãy nuông chiều bản thân một chút nhé, có Hi luôn đồng hành cùng bạn mà!</p>";

        String html = buildHtmlTemplate(
                "Hi nhắc kỳ kinh sắp tới",
                heading,
                body,
                "Xem lịch chu kỳ",
                "https://hilover.space/cycles",
                to,
                "Thương bạn thật nhiều,"
        );

        sendRequiredEmail(to, "Hi nhắc kỳ kinh sắp tới", html, true);
    }

    /**
     * Email nhắc đối tác về kỳ kinh sắp tới của người yêu
     */
    @Async("mailExecutor")
    public void sendPartnerPeriodUpcomingEmail(String to, String name, String partnerName, int daysBefore) {
        String heading = "Chào " + name + " thân mến,";
        String body = "<p style=\"margin: 0 0 20px 0; font-size: 16px; line-height: 1.6;\">" +
                "Hi bật mí cho bạn một bí mật nhỏ này nha: khoảng <strong style=\"color: #ff758c; font-size: 18px;\">" + daysBefore + " ngày</strong> nữa là bạn đồng hành <strong>" + partnerName + "</strong> của bạn sẽ bước vào 'mùa dâu rụng' đó.</p>" +
                "<p style=\"margin: 0 0 20px 0; font-size: 15px; line-height: 1.6;\">" +
                "Những ngày này cô ấy có thể sẽ hơi mệt mỏi, nhạy cảm hoặc dễ xúc động hơn bình thường một xíu. Bạn nhớ dành thêm sự kiên nhẫn, chuẩn bị một chút nước ấm, túi chườm hay những cái ôm thật chặt để chăm sóc cô ấy nha. Người ấy chắc chắn sẽ cảm động lắm đó!</p>";

        String html = buildHtmlTemplate(
                "Hi nhắc bạn quan tâm Người ấy",
                heading,
                body,
                "Ghé xem Dashboard chăm sóc",
                "https://hilover.space/male-dashboard",
                to,
                "Thương hai bạn,"
        );

        sendRequiredEmail(to, "Hi nhắc bạn quan tâm Người ấy", html, true);
    }

    /**
     * Email nhắc nhở ghi triệu chứng hằng ngày
     */
    @Async("mailExecutor")
    public void sendSymptomReminderEmail(String to, String name, String message, boolean endOfDay) {
        String heading = "Chào " + name + " ơi,";
        String buttonText = endOfDay ? "Ghi nhận cùng Hi nha" : "Ghi triệu chứng ngay";
        
        String body;
        if (endOfDay) {
            body = "<p style=\"margin: 0 0 20px 0; font-size: 16px; line-height: 1.6;\">" +
                    "Một ngày dài sắp khép lại rồi, bạn đã chuẩn bị đi ngủ chưa nè? Trước khi nhắm mắt nghỉ ngơi, hãy dành ra 30 giây cùng Hi nhìn lại ngày hôm nay nhé.</p>" +
                    "<p style=\"margin: 0; font-size: 15px; line-height: 1.6;\">" +
                    "Nếu bạn đang trong chu kỳ hoặc có triệu chứng gì khó chịu, nhớ ghi nhanh lại cho Hi biết nha. Chúc bạn có một giấc ngủ thật ngon và những giấc mơ thật đẹp. Ngủ ngon nha bạn của Hi!</p>";
        } else {
            body = "<p style=\"margin: 0 0 20px 0; font-size: 16px; line-height: 1.6;\">" +
                    "Hôm nay bạn có thấy cơ thể mình có gì khác lạ hay có dấu hiệu 'dâu rụng' không nè?</p>" +
                    "<p style=\"margin: 0; font-size: 15px; line-height: 1.6;\">" +
                    "Hi ghé nhắc nhẹ bạn ghi lại vài triệu chứng hoặc lượng kinh hôm nay nha. Chỉ cần vài chạm thôi là Hi đã hiểu rõ cơ thể bạn hơn để đưa ra các dự đoán siêu chuẩn xác rồi đó. Cùng nhau chăm sóc sức khỏe thật tốt nha!</p>";
        }

        String html = buildHtmlTemplate(
                "Hi nhắc bạn ghi triệu chứng hôm nay",
                heading,
                body,
                buttonText,
                "https://hilover.space/cycles",
                to,
                endOfDay ? "Thương bạn, ngủ ngon nha!" : "Thương bạn nhiều,"
        );

        sendRequiredEmail(to, "Hi nhắc bạn ghi triệu chứng hôm nay", html, true);
    }

    /**
     * Email gợi ý đối tác quan tâm tối nay
     */
    @Async("mailExecutor")
    public void sendPartnerSymptomNudgeEmail(String to, String name, String partnerName, String message) {
        String heading = "Chào " + name + " nha,";
        String body = "<p style=\"margin: 0 0 20px 0; font-size: 16px; line-height: 1.6;\">" +
                "Tối muộn rồi nè, không biết bạn đồng hành <strong>" + partnerName + "</strong> của bạn hôm nay thế nào rồi ta?</p>" +
                "<p style=\"margin: 0 0 20px 0; font-size: 16px; line-height: 1.6;\">" +
                "Hình như cô ấy đang cảm thấy hơi mệt mỏi hoặc có chút triệu chứng nhạy cảm trong người đó. Bạn hãy gửi một tin nhắn chúc ngủ ngon thật ngọt ngào, hoặc hỏi thăm xem cô ấy có cần bạn chườm ấm hay pha giúp cốc sữa nóng không nha. Sự tinh tế của bạn chính là liều thuốc ngọt ngào nhất lúc này đó!</p>";

        String html = buildHtmlTemplate(
                "Hi gợi ý bạn quan tâm Người ấy tối nay",
                heading,
                body,
                "Xem gợi ý quan tâm",
                "https://hilover.space/male-dashboard",
                to,
                "Thương hai bạn,"
        );

        sendRequiredEmail(to, "Hi gợi ý bạn quan tâm Người ấy tối nay", html, true);
    }

    /**
     * Fallback cho các trường hợp gửi mail tuỳ chọn khác (Plain-text/HTML tự do)
     */
    @Async("mailExecutor")
    public void sendOptionalEmail(String to, String subject, String body) {
        try {
            String formattedBody = body.replace("\n", "<br>");
            String html = buildHtmlTemplate(
                    subject,
                    "Xin chào bạn,",
                    "<p style=\"margin: 0; line-height: 1.6;\">" + formattedBody + "</p>",
                    "Mở Hi App",
                    "https://hilover.space",
                    to,
                    "Thương mến từ Hi Lover,"
            );
            sendRequiredEmail(to, subject, html, true);
        } catch (MailException ex) {
            log.warn("[EMAIL] Không gửi được email tùy chọn tới {}: {}", to, ex.getMessage());
        }
    }

    private void sendRequiredEmail(String to, String subject, String body, boolean isHtml) {
        log.info("[EMAIL] Chuẩn bị gửi email đến: {}", to);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String senderName = "Hi Lover App";
            if (fromEmail != null && !fromEmail.isBlank()) {
                helper.setFrom(fromEmail, senderName);
            } else {
                helper.setFrom("hilover.space@gmail.com", senderName);
            }

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, isHtml);

            mailSender.send(message);
            log.info("[EMAIL] Gửi email thành công đến: {}", to);
        } catch (Exception ex) {
            log.error("[EMAIL] Gửi email thất bại đến {}: {}", to, ex.getMessage(), ex);
            throw new MailSendException("Gửi email thất bại: " + ex.getMessage(), ex);
        }
    }

    private String buildHtmlTemplate(String title, String heading, String bodyContent, String ctaText, String ctaUrl, String unsubscribeEmail, String signOff) {
        String ctaHtml = "";
        if (ctaText != null && ctaUrl != null && !ctaText.isBlank() && !ctaUrl.isBlank()) {
            ctaHtml = "<table width=\"100%\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\" style=\"margin-bottom: 30px;\">" +
                    "  <tr>" +
                    "    <td align=\"center\">" +
                    "      <a href=\"" + ctaUrl + "\" style=\"background: linear-gradient(135deg, #ff758c 0%, #ff7eb3 100%); color: #ffffff; text-decoration: none; padding: 14px 35px; border-radius: 50px; font-size: 16px; font-weight: 600; display: inline-block; box-shadow: 0 4px 15px rgba(255, 117, 140, 0.4);\">" +
                    ctaText +
                    "      </a>" +
                    "    </td>" +
                    "  </tr>" +
                    "</table>";
        }

        String unsubscribeHtml = "";
        if (unsubscribeEmail != null && !unsubscribeEmail.isBlank()) {
            unsubscribeHtml = "<a href=\"https://hilover.space/unsubscribe?email=" + unsubscribeEmail + "\" style=\"color: #a0a0a0; text-decoration: underline;\">Hủy nhận mail</a>";
        } else {
            unsubscribeHtml = "<a href=\"https://hilover.space/profile/notifications\" style=\"color: #a0a0a0; text-decoration: underline;\">Hủy nhận mail</a>";
        }

        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "  <meta charset=\"utf-8\">" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "  <title>" + title + "</title>" +
                "</head>" +
                "<body style=\"margin: 0; padding: 0; background-color: #fcf8f8; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\">" +
                "  <table width=\"100%\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\" style=\"background-color: #fcf8f8; padding: 40px 10px;\">" +
                "    <tr>" +
                "      <td align=\"center\">" +
                "        <table width=\"600\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\" style=\"background-color: #ffffff; border-radius: 24px; overflow: hidden; box-shadow: 0 8px 30px rgba(255, 182, 193, 0.15);\">" +
                "          <!-- Header (Brand color - Pink/Purple gradient) -->" +
                "          <tr>" +
                "            <td align=\"center\" style=\"background: linear-gradient(135deg, #ff758c 0%, #ff7eb3 100%); padding: 30px 20px;\">" +
                "              <h1 style=\"color: #ffffff; margin: 0; font-size: 28px; font-weight: 700; letter-spacing: 1px;\">Hi Lover</h1>" +
                "              <p style=\"color: rgba(255,255,255,0.9); margin: 5px 0 0 0; font-size: 14px;\">Bạn đồng hành chăm sóc sức khỏe sinh sản</p>" +
                "            </td>" +
                "          </tr>" +
                "          " +
                "          <!-- Content Body -->" +
                "          <tr>" +
                "            <td style=\"padding: 40px 30px; text-align: left;\">" +
                "              <h2 style=\"color: #333333; font-size: 20px; font-weight: 600; margin: 0 0 20px 0;\">" + heading + "</h2>" +
                "              <div style=\"font-size: 16px; color: #4a4a4a; line-height: 1.6; margin: 0 0 20px 0;\">" +
                bodyContent +
                "              </div>" +
                "              <p style=\"font-size: 16px; color: #ff758c; font-weight: 600; margin: 0 0 30px 0;\">" + signOff + "<br>Hi Lover</p>" +
                ctaHtml +
                "            </td>" +
                "          </tr>" +
                "          " +
                "          <!-- Footer -->" +
                "          <tr>" +
                "            <td align=\"center\" style=\"background-color: #fff6f7; padding: 25px 20px; border-top: 1px solid #ffebeb;\">" +
                "              <p style=\"font-size: 12px; color: #a0a0a0; margin: 0 0 10px 0;\">" +
                "                Bạn nhận được email này vì bạn đã đăng ký nhận thông báo nhắc nhở từ Hi App." +
                "              </p>" +
                "              <p style=\"font-size: 12px; color: #a0a0a0; margin: 0;\">" +
                "                <a href=\"https://hilover.space/profile/notifications\" style=\"color: #ff758c; text-decoration: underline;\">Cài đặt thông báo</a>" +
                "                &nbsp;•&nbsp; " +
                unsubscribeHtml +
                "              </p>" +
                "            </td>" +
                "          </tr>" +
                "        </table>" +
                "      </td>" +
                "    </tr>" +
                "  </table>" +
                "</body>" +
                "</html>";
    }
}
