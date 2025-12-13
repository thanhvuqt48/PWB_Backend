package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.entity.LiveSession;
import jakarta.mail.MessagingException;
import org.springframework.kafka.support.Acknowledgment;

import java.io.UnsupportedEncodingException;
import java.util.List;

public interface EmailService {

    void sendEmail(String subject, String content, List<String> toList) throws MessagingException, UnsupportedEncodingException;

    void sendEmailByKafka(NotificationEvent event, Acknowledgment acknowledgment) throws MessagingException, UnsupportedEncodingException;

    void sendSessionInviteNotification(LiveSession session, List<String> memberEmails) throws MessagingException, UnsupportedEncodingException;
    
    void sendSessionReminderNotification(LiveSession session, List<String> participantEmails) throws MessagingException, UnsupportedEncodingException;
    
    void sendSessionCancellationEmail(LiveSession session, List<String> participantEmails, String reason) throws MessagingException, UnsupportedEncodingException;
    
    void sendSessionScheduleChangeNotification(LiveSession session, List<String> participantEmails, java.time.LocalDateTime oldScheduledStart) throws MessagingException, UnsupportedEncodingException;
    
    void sendReviewReceivedEmail(String producerEmail, String producerName, String clientName, 
                                 String projectTitle, Integer rating, String comment) throws MessagingException, UnsupportedEncodingException;
    
    void sendReviewConfirmationEmail(String clientEmail, String clientName, String producerName, 
                                     String projectTitle, Integer rating) throws MessagingException, UnsupportedEncodingException;
}
