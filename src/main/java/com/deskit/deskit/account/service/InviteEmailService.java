package com.deskit.deskit.account.service;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Log4j2
@Service
public class InviteEmailService {

    private final SendGrid sendGrid;

    public InviteEmailService(@Value("${spring.sendgrid.api-key}") String apiKey) {
        this.sendGrid = new SendGrid(apiKey);
    }

    public void sendInviteMail(
            String email,
            String inviteUrl,
            String recipientName,
            String businessName,
            String inviterName,
            LocalDateTime expiresAt
    ) throws IOException {
        Email from = new Email("dyniiyeyo@naver.com");
        Email to = new Email(email);
        String subject = "[DESKIT] 판매자 동업자 초대 안내";

        String htmlTemplate;
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("email/doc/invite_template.html")) {
            if (inputStream == null) {
                throw new IOException("Invite email template not found: email/doc/invite_template.html");
            }
            htmlTemplate = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        String expiryText = "";
        if (expiresAt != null) {
            expiryText = expiresAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        }

        String html = htmlTemplate
                .replace("{{수신자명}}", safeValue(recipientName))
                .replace("{{사업자명}}", safeValue(businessName))
                .replace("{{초대한사람명}}", safeValue(inviterName))
                .replace("{{초대링크}}", inviteUrl)
                .replace("{{만료일}}", expiryText);
        Content content = new Content("text/html", html);

        Mail mail = new Mail(from, subject, to, content);

        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());

        sendGrid.api(request);
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }
}
