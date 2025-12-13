package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.configuration.FrontendProperties;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.entity.LiveSession;
import com.fpt.producerworkbench.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class EmailServiceImpl implements EmailService {

    @NonFinal
    @Value("${spring.mail.username}")
    String emailFrom;

    JavaMailSender mailSender;
    SpringTemplateEngine templateEngine;
    FrontendProperties frontendProperties;

    @Async
    public void sendEmail(String subject, String content, List<String> toList)
            throws MessagingException, UnsupportedEncodingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();

        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage);
        helper.setFrom(emailFrom, "Producer Workbench");
        helper.setTo(toList.toArray(new String[0]));
        helper.setSubject(subject);
        helper.setText(content, true);

        mailSender.send(mimeMessage);
    }

    @KafkaListener(topics = "notification-delivery", groupId = "my-consumer-group")
    public void sendEmailByKafka(NotificationEvent event, Acknowledgment acknowledgment)
            throws MessagingException, UnsupportedEncodingException {
        log.info("Received Kafka message to send email: {}", event);

        Context context = new Context();
        context.setVariable("recipientName", event.getRecipient());

        if (event.getParam() != null) {
            context.setVariables(event.getParam());
        } else {
            log.warn("Event param is null, cannot set variables in email template.");
        }

        String htmlContent = templateEngine.process(event.getTemplateCode(), context);

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage,
                MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());

        helper.setFrom(emailFrom, "Producer Workbench");
        helper.setTo(event.getRecipient());
        helper.setSubject(event.getSubject());
        helper.setText(htmlContent, true);

        mailSender.send(mimeMessage);
        acknowledgment.acknowledge();

        log.info("Email sent to {} successfully!", event.getRecipient());
    }

    @Async
    @Override
    public void sendSessionInviteNotification(LiveSession session, List<String> memberEmails) 
            throws MessagingException, UnsupportedEncodingException {
        
        if (memberEmails == null || memberEmails.isEmpty()) {
            log.warn("No member emails to send session invite notification");
            return;
        }

        log.info("Sending session invite notification to {} members for session: {}", 
                memberEmails.size(), session.getTitle());

        // Prepare template variables
        Context context = new Context();
        context.setVariable("sessionTitle", session.getTitle());
        context.setVariable("sessionDescription", session.getDescription());
        context.setVariable("hostName", getFullName(session.getHost()));
        context.setVariable("projectName", session.getProject().getTitle());
        
        // Format scheduled start time
        if (session.getScheduledStart() != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy 'at' hh:mm a");
            String formattedDate = session.getScheduledStart().format(formatter);
            context.setVariable("scheduledStart", formattedDate);
        } else {
            context.setVariable("scheduledStart", "To be announced");
        }
        
        // Session link (adjust based on your frontend URL structure)
        String sessionLink = String.format("%s/projects/%d/sessions/%s", 
                frontendProperties.getUrl(), session.getProject().getId(), session.getId());
        context.setVariable("sessionLink", sessionLink);

        // Send email to each member
        for (String memberEmail : memberEmails) {
            try {
                context.setVariable("recipientName", extractNameFromEmail(memberEmail));
                String personalizedHtml = templateEngine.process("session-invite-notification", context);
                
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage,
                        MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());

                helper.setFrom(emailFrom, "Producer Workbench");
                helper.setTo(memberEmail);
                helper.setSubject("🎵 You're Invited: " + session.getTitle());
                helper.setText(personalizedHtml, true);

                mailSender.send(mimeMessage);
                log.info("Session invite email sent to {} successfully!", memberEmail);
                
            } catch (Exception e) {
                log.error("Failed to send session invite email to {}: {}", memberEmail, e.getMessage());
            }
        }
    }

    private String getFullName(com.fpt.producerworkbench.entity.User user) {
        if (user.getFirstName() != null && user.getLastName() != null) {
            return user.getFirstName() + " " + user.getLastName();
        } else if (user.getFirstName() != null) {
            return user.getFirstName();
        } else if (user.getLastName() != null) {
            return user.getLastName();
        }
        return user.getEmail();
    }

    private String extractNameFromEmail(String email) {
        if (email.contains("@")) {
            String username = email.substring(0, email.indexOf("@"));
            // Capitalize first letter
            return username.substring(0, 1).toUpperCase() + username.substring(1);
        }
        return email;
    }

    @Async
    @Override
    public void sendSessionReminderNotification(LiveSession session, List<String> participantEmails) 
            throws MessagingException, UnsupportedEncodingException {
        
        if (participantEmails == null || participantEmails.isEmpty()) {
            log.warn("No participant emails to send session reminder notification");
            return;
        }

        log.info("Sending session reminder notification to {} participants for session: {}", 
                participantEmails.size(), session.getTitle());

        // Prepare template variables
        Context context = new Context();
        context.setVariable("sessionTitle", session.getTitle());
        context.setVariable("hostName", getFullName(session.getHost()));
        context.setVariable("projectName", session.getProject().getTitle());
        
        // Format scheduled start time
        if (session.getScheduledStart() != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy 'at' hh:mm a");
            String formattedDate = session.getScheduledStart().format(formatter);
            context.setVariable("scheduledStart", formattedDate);
        } else {
            context.setVariable("scheduledStart", "Now");
        }
        
        // Session link
        String sessionLink = String.format("%s/projects/%d/sessions/%s", 
                frontendProperties.getUrl(), session.getProject().getId(), session.getId());
        context.setVariable("sessionLink", sessionLink);

        // Send email to each participant
        for (String participantEmail : participantEmails) {
            try {
                context.setVariable("recipientName", extractNameFromEmail(participantEmail));
                String personalizedHtml = templateEngine.process("session-reminder-notification", context);
                
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage,
                        MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());

                helper.setFrom(emailFrom, "Producer Workbench");
                helper.setTo(participantEmail);
                helper.setSubject("⏰ REMINDER: " + session.getTitle() + " starts in 5 minutes!");
                helper.setText(personalizedHtml, true);

                mailSender.send(mimeMessage);
                log.info("Session reminder email sent to {} successfully!", participantEmail);
                
            } catch (Exception e) {
                log.error("Failed to send session reminder email to {}: {}", participantEmail, e.getMessage());
            }
        }
    }

    @Async
    @Override
    public void sendSessionCancellationEmail(LiveSession session, List<String> participantEmails, String reason) 
            throws MessagingException, UnsupportedEncodingException {
        
        if (participantEmails == null || participantEmails.isEmpty()) {
            log.warn("No participant emails to send session cancellation notification");
            return;
        }

        log.info("Sending session cancellation notification to {} participants for session: {}", 
                participantEmails.size(), session.getTitle());

        // Prepare template variables
        Context context = new Context();
        context.setVariable("sessionTitle", session.getTitle());
        context.setVariable("sessionDescription", session.getDescription());
        context.setVariable("hostName", getFullName(session.getHost()));
        context.setVariable("projectName", session.getProject().getTitle());
        context.setVariable("reason", reason != null ? reason : "No specific reason provided");
        
        // Format scheduled start time
        if (session.getScheduledStart() != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy 'at' hh:mm a");
            String formattedDate = session.getScheduledStart().format(formatter);
            context.setVariable("scheduledStart", formattedDate);
        } else {
            context.setVariable("scheduledStart", "Not specified");
        }
        
        // Project link
        String projectLink = String.format("%s/projects/%d",
                frontendProperties.getUrl(), 
                session.getProject().getId());
        context.setVariable("projectLink", projectLink);

        // Send email to each participant
        for (String participantEmail : participantEmails) {
            try {
                context.setVariable("recipientName", extractNameFromEmail(participantEmail));
                String personalizedHtml = templateEngine.process("session-cancellation-notification", context);
                
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage,
                        MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());

                helper.setFrom(emailFrom, "Producer Workbench");
                helper.setTo(participantEmail);
                helper.setSubject("❌ Session Cancelled: " + session.getTitle());
                helper.setText(personalizedHtml, true);

                mailSender.send(mimeMessage);
                log.info("Session cancellation email sent to {} successfully!", participantEmail);
                
            } catch (Exception e) {
                log.error("Failed to send session cancellation email to {}: {}", participantEmail, e.getMessage());
            }
        }
    }

    @Override
    @Async
    public void sendSessionScheduleChangeNotification(LiveSession session, List<String> participantEmails, 
            LocalDateTime oldScheduledStart) throws MessagingException, UnsupportedEncodingException {
        
        if (participantEmails == null || participantEmails.isEmpty()) {
            log.warn("No participant emails to send schedule change notification");
            return;
        }

        log.info("Sending schedule change notification to {} participants for session: {}", 
                participantEmails.size(), session.getTitle());

        // Prepare template variables
        Context context = new Context();
        context.setVariable("sessionTitle", session.getTitle());
        context.setVariable("sessionDescription", session.getDescription());
        context.setVariable("hostName", getFullName(session.getHost()));
        context.setVariable("projectName", session.getProject().getTitle());
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy 'at' hh:mm a");
        
        // Old scheduled time
        if (oldScheduledStart != null) {
            context.setVariable("oldScheduledStart", oldScheduledStart.format(formatter));
        } else {
            context.setVariable("oldScheduledStart", "Not previously scheduled");
        }
        
        // New scheduled time
        if (session.getScheduledStart() != null) {
            context.setVariable("newScheduledStart", session.getScheduledStart().format(formatter));
        } else {
            context.setVariable("newScheduledStart", "Not specified");
        }
        
        // Project link
        String projectLink = String.format("%s/projects/%d",
                frontendProperties.getUrl(), 
                session.getProject().getId());
        context.setVariable("projectLink", projectLink);

        // Send email to each participant
        for (String participantEmail : participantEmails) {
            try {
                context.setVariable("recipientName", extractNameFromEmail(participantEmail));
                String personalizedHtml = templateEngine.process("session-schedule-change-notification", context);
                
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage,
                        MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());

                helper.setFrom(emailFrom, "Producer Workbench");
                helper.setTo(participantEmail);
                helper.setSubject("🔄 Session Time Changed: " + session.getTitle());
                helper.setText(personalizedHtml, true);

                mailSender.send(mimeMessage);
                log.info("Schedule change email sent to {} successfully!", participantEmail);
                
            } catch (Exception e) {
                log.error("Failed to send schedule change email to {}: {}", participantEmail, e.getMessage());
            }
        }
    }

    @Override
    @Async
    public void sendReviewReceivedEmail(String producerEmail, String producerName, String clientName, 
                                        String projectTitle, Integer rating, String comment) 
            throws MessagingException, UnsupportedEncodingException {
        
        log.info("Sending review received email to {}", producerEmail);
        
        Context context = new Context();
        context.setVariable("producerName", producerName);
        context.setVariable("clientName", clientName);
        context.setVariable("projectTitle", projectTitle);
        context.setVariable("rating", rating);
        context.setVariable("comment", comment != null ? comment : "Không có nhận xét");
        
        // Generate star icons
        String starsFilled = "⭐".repeat(rating);
        String starsEmpty = "☆".repeat(5 - rating);
        context.setVariable("starsFilled", starsFilled);
        context.setVariable("starsEmpty", starsEmpty);
        
        // Portfolio link
        String portfolioLink = "http://localhost:5173/projectManage";
        context.setVariable("portfolioLink", portfolioLink);
        
        try {
            String html = templateEngine.process("producer-review-received", context);
            
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            
            helper.setFrom(emailFrom, "Producer Workbench");
            helper.setTo(producerEmail);
            helper.setSubject("🌟 Bạn nhận được đánh giá " + rating + " sao từ " + clientName);
            helper.setText(html, true);
            
            mailSender.send(mimeMessage);
            log.info("Review received email sent to {} successfully!", producerEmail);
            
        } catch (Exception e) {
            log.error("Failed to send review received email to {}: {}", producerEmail, e.getMessage());
            throw e;
        }
    }

    @Override
    @Async
    public void sendReviewConfirmationEmail(String clientEmail, String clientName, String producerName, 
                                            String projectTitle, Integer rating) 
            throws MessagingException, UnsupportedEncodingException {
        
        log.info("Sending review confirmation email to {}", clientEmail);
        
        Context context = new Context();
        context.setVariable("clientName", clientName);
        context.setVariable("producerName", producerName);
        context.setVariable("projectTitle", projectTitle);
        context.setVariable("rating", rating);
        
        // Generate star icons
        String starsFilled = "⭐".repeat(rating);
        String starsEmpty = "☆".repeat(5 - rating);
        context.setVariable("starsFilled", starsFilled);
        context.setVariable("starsEmpty", starsEmpty);
        
        // Project manage link
        String projectManageLink = "http://localhost:5173/projectManage";
        context.setVariable("projectManageLink", projectManageLink);
        
        try {
            String html = templateEngine.process("client-review-confirmation", context);
            
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            
            helper.setFrom(emailFrom, "Producer Workbench");
            helper.setTo(clientEmail);
            helper.setSubject("✅ Đánh giá của bạn đã được gửi thành công!");
            helper.setText(html, true);
            
            mailSender.send(mimeMessage);
            log.info("Review confirmation email sent to {} successfully!", clientEmail);
            
        } catch (Exception e) {
            log.error("Failed to send review confirmation email to {}: {}", clientEmail, e.getMessage());
            throw e;
        }
    }

}
