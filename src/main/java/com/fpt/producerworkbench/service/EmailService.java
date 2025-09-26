package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.event.NotificationEvent;
import jakarta.mail.MessagingException;

import java.io.UnsupportedEncodingException;
import java.util.List;

public interface EmailService {

    void sendEmail(String subject, String content, List<String> toList) throws MessagingException, UnsupportedEncodingException;

    void sendEmailByKafka(NotificationEvent event) throws MessagingException, UnsupportedEncodingException;
}
