package com.swp391.beswp.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OtpMailServiceTest {
    @SuppressWarnings("unchecked")
    ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
    @Test void missingConfigurationFailsInsteadOfPretendingToDeliver() {
        var service = new OtpMailService(provider, "");
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> service.send("member@example.com", "REGISTER", "003291")).getStatusCode().value());
    }
    @Test void sendsSixDigitsIncludingLeadingZerosAndDoesNotExposeSmtpFailure() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class); when(provider.getIfAvailable()).thenReturn(sender);
        when(sender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new java.util.Properties())));
        var service = new OtpMailService(provider, "unisport@example.com");
        service.send("member@example.com", "REGISTER", "003291");
        ArgumentCaptor<MimeMessage> messages = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(messages.capture());
        MimeMessage message = messages.getValue(); message.saveChanges();
        assertEquals("UNISPORT - Xác thực email", message.getSubject());
        assertEquals("unisport@example.com", ((jakarta.mail.internet.InternetAddress) message.getFrom()[0]).getAddress());
        assertEquals("member@example.com", message.getAllRecipients()[0].toString());
        String html = body(message, "text/html"), plain = body(message, "text/plain");
        assertTrue(html.contains("003291")); assertTrue(html.contains("#2f64ff"));
        assertTrue(html.contains("Đội ngũ hỗ trợ UNISPORT")); assertTrue(html.contains("Có hiệu lực trong 5 phút"));
        assertTrue(plain.contains("003291")); assertFalse(plain.contains("<html"));
        doThrow(new MailSendException("sensitive SMTP diagnostic")).when(sender).send(any(MimeMessage.class));
        var error = assertThrows(ResponseStatusException.class, () -> service.send("member@example.com", "RESET", "003291"));
        assertEquals(503, error.getStatusCode().value()); assertFalse(error.getReason().contains("diagnostic"));
    }

    @Test void resetEmailUsesPasswordRecoveryWordingInBothBodies() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class); when(provider.getIfAvailable()).thenReturn(sender);
        when(sender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new java.util.Properties())));
        new OtpMailService(provider, "unisport@example.com").send("member@example.com", "RESET", "003291");
        ArgumentCaptor<MimeMessage> messages = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(messages.capture()); MimeMessage message = messages.getValue(); message.saveChanges();
        assertEquals("UNISPORT - Đặt lại mật khẩu", message.getSubject());
        assertTrue(body(message, "text/html").contains("yêu cầu đặt lại mật khẩu"));
        assertTrue(body(message, "text/plain").contains("yêu cầu đặt lại mật khẩu"));
    }

    @Test void changingEmailUsesNewEmailSubjectAndClearPurpose() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class); when(provider.getIfAvailable()).thenReturn(sender);
        when(sender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new java.util.Properties())));
        new OtpMailService(provider, "unisport@example.com").send("real@example.com", "CHANGE_EMAIL", "003291");
        ArgumentCaptor<MimeMessage> messages = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(messages.capture()); MimeMessage message = messages.getValue(); message.saveChanges();
        assertEquals("UNISPORT - Xác thực email mới", message.getSubject());
        assertTrue(body(message, "text/html").contains("yêu cầu thay đổi email"));
    }

    private String body(Part part, String type) throws Exception {
        if (part.isMimeType(type)) return (String) part.getContent();
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                String found = body(multipart.getBodyPart(i), type);
                if (!found.isEmpty()) return found;
            }
        }
        return "";
    }
}
