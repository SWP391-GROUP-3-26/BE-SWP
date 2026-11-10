package com.swp391.beswp.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.util.HtmlUtils;
import jakarta.mail.MessagingException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OtpMailService {
    private final ObjectProvider<JavaMailSender> senders;
    private final String from;
    public OtpMailService(ObjectProvider<JavaMailSender> senders, @Value("${app.mail.from:}") String from) {
        this.senders = senders; this.from = from;
    }
    public void send(String email, String purpose, String otp) {
        JavaMailSender sender = senders.getIfAvailable();
        if (sender == null || from.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình dịch vụ gửi email");
        }
        boolean changingEmail = "CHANGE_EMAIL".equals(purpose) || "VERIFY_CHANGE_EMAIL".equals(purpose);
        String title = "RESET".equals(purpose) ? "Đặt lại mật khẩu" : changingEmail ? "Xác thực email mới" : "Xác thực email";
        String action = "RESET".equals(purpose) ? "đặt lại mật khẩu" : changingEmail ? "thay đổi email" : "xác thực tài khoản";
        String plainText = "Xin chào!\n\nChúng tôi nhận được yêu cầu " + action + " của bạn."
                + "\nVui lòng sử dụng mã OTP bên dưới để hoàn tất quá trình " + action + ":\n\n" + otp
                + "\nCó hiệu lực trong 5 phút.\n\nLưu ý bảo mật:"
                + "\n- Không chia sẻ mã OTP này với bất kỳ ai.\n- Mã OTP chỉ sử dụng được một lần."
                + "\n- Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email."
                + "\n\nTrân trọng,\nĐội ngũ hỗ trợ UNISPORT"
                + "\n\nĐây là email tự động, vui lòng không trả lời email này.";
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from, "UNISPORT"); helper.setTo(email);
            helper.setSubject("UNISPORT - " + title);
            helper.setText(plainText, html(action, otp));
            sender.send(message);
        } catch (MailException | MessagingException | java.io.UnsupportedEncodingException ex) {
            // Mail exceptions can include message contents; never log or expose the OTP.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Không gửi được email, vui lòng thử lại sau");
        }
    }

    private String html(String action, String otp) {
        // Inline styles and table layout work in email clients without external CSS or images.
        return """
                <!doctype html>
                <html lang="vi">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
                <body style="margin:0;padding:10px;background-color:#ffffff;">
                  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0">
                    <tr><td align="center">
                      <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0"
                        style="max-width:600px;font-family:Arial,Helvetica,sans-serif;font-size:13px;line-height:1.6;color:#111111;">
                        <tr><td style="border-top:1px solid #eeeeee;padding-top:12px;">
                          <h1 style="margin:0 0 10px;font-size:20px;line-height:1.4;font-weight:600;">Xin chào!</h1>
                          <p style="margin:0 0 10px;">Chúng tôi nhận được yêu cầu {{ACTION}} của bạn.</p>
                          <p style="margin:0 0 10px;">Vui lòng sử dụng mã OTP bên dưới để hoàn tất quá trình {{ACTION}}:</p>
                        </td></tr>
                        <tr><td align="center" style="padding:10px 12px;background-color:#f2f2f2;border-radius:10px;">
                          <div style="font-size:10px;color:#666666;">MÃ XÁC THỰC OTP</div>
                          <div style="padding:2px 0;font-size:32px;font-weight:700;letter-spacing:2px;line-height:1.3;color:#2f64ff;">{{OTP}}</div>
                          <div style="font-size:10px;color:#777777;">Có hiệu lực trong 5 phút</div>
                        </td></tr>
                        <tr><td style="padding-top:8px;">
                          <p style="margin:0 0 6px;">Lưu ý bảo mật:</p>
                          <ul style="margin:0 0 12px;padding-left:26px;">
                            <li>Không chia sẻ mã OTP này với bất kỳ ai.</li>
                            <li>Mã OTP chỉ sử dụng được một lần.</li>
                            <li>Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.</li>
                          </ul>
                          <p style="margin:0 0 8px;">Trân trọng,</p>
                          <p style="margin:0 0 16px;font-weight:700;">Đội ngũ hỗ trợ UNISPORT</p>
                        </td></tr>
                        <tr><td align="center" style="border-top:1px solid #eeeeee;padding:14px 0 0;font-size:11px;color:#888888;">
                          Đây là email tự động, vui lòng không trả lời email này.
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.replace("{{ACTION}}", HtmlUtils.htmlEscape(action)).replace("{{OTP}}", HtmlUtils.htmlEscape(otp));
    }
}
