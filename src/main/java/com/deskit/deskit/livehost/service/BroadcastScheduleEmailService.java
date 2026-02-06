package com.deskit.deskit.livehost.service;

import com.deskit.deskit.livehost.entity.Broadcast;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class BroadcastScheduleEmailService {

    private final SendGrid sendGrid;
    private final String senderEmail;

    public BroadcastScheduleEmailService(
            @Value("${spring.sendgrid.api-key}") String apiKey,
            @Value("${spring.sendgrid.sender-email:dyniiyeyo@naver.com}") String senderEmail
    ) {
        this.sendGrid = new SendGrid(apiKey);
        this.senderEmail = senderEmail;
    }

    public void sendStartReminder(Broadcast broadcast) {
        if (broadcast == null || broadcast.getSeller() == null) {
            return;
        }
        String toEmail = broadcast.getSeller().getLoginId();
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }
        String subject = "[DESKIT] 라이브 방송 시작 30분 전 알림";
        String title = broadcast.getBroadcastTitle();
        String scheduledAt = broadcast.getScheduledAt() != null ? broadcast.getScheduledAt().toString() : "";

        Content content = new Content(
                "text/html",
                "<h2>라이브 방송 시작 예정 알림</h2>" +
                        "<p>곧 방송이 시작됩니다. 준비를 완료해 주세요.</p>" +
                        "<p><strong>방송 제목:</strong> " + (title != null ? title : "") + "</p>" +
                        "<p><strong>방송 시간:</strong> " + scheduledAt + "</p>"
        );

        try {
            Email from = new Email(senderEmail);
            Email to = new Email(toEmail);
            Mail mail = new Mail(from, subject, to, content);
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            sendGrid.api(request);
        } catch (Exception e) {
            log.warn("Failed to send broadcast start reminder. broadcastId={}", broadcast.getBroadcastId(), e);
        }
    }
}
