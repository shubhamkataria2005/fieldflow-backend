// src/main/java/com/Shubham/carDealership/service/EmailService.java
package com.Shubham.carDealership.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${mail.from.address}")
    private String fromAddress;

    @Value("${mail.from.name}")
    private String fromName;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, h:mm a");

    public void sendTestDriveBooked(String toEmail, String userName, String carDescription, LocalDateTime appointmentDate) {
        String subject = "Test Drive Request Received";
        String body = String.format(
                "Hi %s,%n%n" +
                        "Thanks for booking a test drive%s for %s.%n%n" +
                        "Your request has been sent to our team for approval. We'll email you again as soon as it's confirmed.%n%n" +
                        "— Shubham's Car Dealership",
                userName,
                appointmentDate != null ? " on " + appointmentDate.format(DATE_FMT) : "",
                carDescription
        );
        send(toEmail, subject, body);
    }

    public void sendTestDriveConfirmed(String toEmail, String userName, String carDescription, LocalDateTime appointmentDate) {
        String subject = "Test Drive Confirmed!";
        String body = String.format(
                "Hi %s,%n%n" +
                        "Good news — your test drive%s for %s has been confirmed.%n%n" +
                        "We look forward to seeing you. Need to reschedule? Just reply to this email or get in touch.%n%n" +
                        "— Shubham's Car Dealership",
                userName,
                appointmentDate != null ? " on " + appointmentDate.format(DATE_FMT) : "",
                carDescription
        );
        send(toEmail, subject, body);
    }

    public void sendTestDriveCancelled(String toEmail, String userName, String carDescription) {
        String subject = "Test Drive Cancelled";
        String body = String.format(
                "Hi %s,%n%n" +
                        "Unfortunately your test drive request for %s has been cancelled.%n%n" +
                        "If you have any questions, feel free to get in touch, or book another time that suits you better.%n%n" +
                        "— Shubham's Car Dealership",
                userName,
                carDescription
        );
        send(toEmail, subject, body);
    }

    private void send(String toEmail, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(String.format("%s <%s>", fromName, fromAddress));
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            System.out.println("✅ Email sent to " + toEmail + ": " + subject);
        } catch (Exception e) {
            // Deliberately never let an email failure break the actual booking/
            // approval flow — the database change has already succeeded by the
            // time this runs. Email is a side effect, not a hard dependency.
            System.err.println("⚠️ Failed to send email to " + toEmail + ": " + e.getMessage());
        }
    }
}