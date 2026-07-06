// src/main/java/com/Shubham/carDealership/service/EmailService.java
package com.Shubham.carDealership.service;

import com.Shubham.carDealership.fsm.model.FsmInvoice;
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

    @Value("${mail.fsm.from.name:FieldFlow}")
    private String fsmFromName;

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

    public void sendTestDriveRescheduled(String toEmail, String userName, String carDescription, LocalDateTime appointmentDate) {
        String subject = "Test Drive Rescheduled";
        String body = String.format(
                "Hi %s,%n%n" +
                        "We've updated your test drive%s for %s.%n%n" +
                        "This change has been sent to our team for approval — we'll confirm shortly.%n%n" +
                        "— Shubham's Car Dealership",
                userName,
                appointmentDate != null ? " to " + appointmentDate.format(DATE_FMT) : "",
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

    // ── FSM: quote sent to customer ─────────────────────────────────────────
    public void sendQuoteToCustomer(
            String custEmail, String custName,
            String businessName, String quoteTitle,
            String description, String total, String validUntil) {
        if (custEmail == null || custEmail.isBlank()) return;
        String subject = "Your quote from " + businessName;
        String body = String.format(
            "Hi %s,%n%n" +
            "%s has prepared a quote for you:%n%n" +
            "  Quote   : %s%n" +
            "  Total   : %s (incl. GST)%n" +
            (validUntil != null ? "  Valid until: %s%n" : "%s") +
            "%n" +
            (description != null && !description.isBlank() ? "Scope of work:%n%s%n%n" : "%s") +
            "To accept this quote or ask any questions, please reply to this email.%n%n" +
            "Thank you for choosing %s.%n%n" +
            "— %s",
            custName != null ? custName : "there",
            businessName,
            quoteTitle,
            total,
            validUntil != null ? validUntil : "",
            description != null && !description.isBlank() ? description : "",
            businessName,
            businessName
        );
        send(custEmail, subject, body, fsmFromName);
    }

    // ── FSM: customer sent a message via tracking link ──────────────────────
    public void sendCustomerMessageAlert(
            String ownerEmail, String ownerUsername,
            String jobType, String address, String messageText, String trackingUrl) {
        String subject = "New customer message — " + (jobType != null ? jobType : "Job");
        String body = String.format(
            "Hi %s,%n%n" +
            "A customer just sent a message on one of your jobs:%n%n" +
            "  Job type : %s%n" +
            "  Address  : %s%n%n" +
            "  Message  : \"%s\"%n%n" +
            "View the conversation here:%n%s%n%n" +
            "— FieldFlow",
            ownerUsername,
            jobType  != null ? jobType  : "—",
            address  != null ? address  : "—",
            messageText,
            trackingUrl
        );
        send(ownerEmail, subject, body, fsmFromName);
    }

    // ── FSM: technician assigned to a job ───────────────────────────────────
    public void sendTechJobAssigned(
            String techEmail, String techName,
            String jobType, String address, LocalDateTime scheduledAt) {
        String schedLine = scheduledAt != null
            ? "  Scheduled : " + scheduledAt.format(DATE_FMT) + "\n"
            : "";
        String subject = "New job assigned to you — " + (jobType != null ? jobType : "Job");
        String body = String.format(
            "Hi %s,%n%n" +
            "You have been assigned to a new job:%n%n" +
            "  Type    : %s%n" +
            "  Address : %s%n" +
            "%s%n" +
            "Log in to FieldFlow to see all the details and update your status.%n%n" +
            "— FieldFlow",
            techName,
            jobType != null ? jobType : "—",
            address != null ? address : "—",
            schedLine
        );
        send(techEmail, subject, body, fsmFromName);
    }

    // ── FSM: job status update to customer ─────────────────────────────────
    public void sendJobStatusUpdate(
            String custEmail, String custName,
            String jobType, String newStatus, String trackingUrl, String businessName) {
        if (custEmail == null || custEmail.isBlank()) return;
        String statusLabel = switch (newStatus) {
            case "SCHEDULED"   -> "Scheduled";
            case "DISPATCHED"  -> "Technician Dispatched";
            case "IN_PROGRESS" -> "In Progress";
            case "COMPLETED"   -> "Completed";
            case "INVOICED"    -> "Invoice Issued";
            default -> newStatus;
        };
        String statusNote = switch (newStatus) {
            case "SCHEDULED"   -> "Your appointment has been confirmed. We will be in touch if anything changes.";
            case "DISPATCHED"  -> "A technician is on their way to your location.";
            case "IN_PROGRESS" -> "Work has started at your location.";
            case "COMPLETED"   -> "The job has been completed. Thank you for choosing us!";
            case "INVOICED"    -> "Your invoice has been prepared. Please check the tracking page for details.";
            default -> "Your job status has been updated.";
        };
        String subject = "Job update: " + statusLabel + " — " + (jobType != null ? jobType : "Service");
        String body = String.format(
            "Hi %s,%n%n" +
            "Your job status has been updated:%n%n" +
            "  Service : %s%n" +
            "  Status  : %s%n%n" +
            "%s%n%n" +
            "Track your job here:%n%s%n%n" +
            "— %s",
            custName != null ? custName : "Customer",
            jobType  != null ? jobType  : "—",
            statusLabel,
            statusNote,
            trackingUrl,
            businessName
        );
        send(custEmail, subject, body, fsmFromName);
    }

    // ── FSM: send invoice to customer ───────────────────────────────────────
    public void sendInvoiceToCustomer(FsmInvoice inv, String businessName) {
        if (inv.getCustomer() == null || inv.getCustomer().getEmail() == null) return;
        String custName  = inv.getCustomer().getName() != null ? inv.getCustomer().getName() : "Customer";
        String invNumber = "INV-" + String.format("%04d", inv.getId());
        String amount    = "$" + String.format("%.2f", inv.getAmount());
        String jobType   = inv.getJob() != null ? inv.getJob().getJobType() : "Service";
        String issued    = inv.getIssuedAt() != null ? inv.getIssuedAt().format(DATE_FMT) : "—";
        String subject   = "Your invoice from " + businessName + " — " + invNumber;
        String body = String.format(
            "Hi %s,%n%n" +
            "Please find your invoice details below:%n%n" +
            "  Invoice : %s%n" +
            "  Service : %s%n" +
            "  Issued  : %s%n" +
            "  Amount  : %s%n%n" +
            "Please arrange payment at your earliest convenience.%n" +
            "If you have any questions, feel free to reply to this email.%n%n" +
            "Thank you for choosing %s.%n%n" +
            "— %s",
            custName, invNumber, jobType, issued, amount, businessName, businessName
        );
        send(inv.getCustomer().getEmail(), subject, body, fsmFromName);
    }

    // ── FSM: payment reminder to customer ───────────────────────────────────
    public void sendPaymentReminder(FsmInvoice inv, String businessName) {
        if (inv.getCustomer() == null || inv.getCustomer().getEmail() == null) return;
        String custName  = inv.getCustomer().getName() != null ? inv.getCustomer().getName() : "Customer";
        String invNumber = "INV-" + String.format("%04d", inv.getId());
        String amount    = "$" + String.format("%.2f", inv.getAmount());
        String issued    = inv.getIssuedAt() != null ? inv.getIssuedAt().format(DATE_FMT) : "—";
        String subject   = "Payment reminder — " + invNumber + " (" + amount + " due)";
        String body = String.format(
            "Hi %s,%n%n" +
            "This is a friendly reminder that the following invoice is still outstanding:%n%n" +
            "  Invoice : %s%n" +
            "  Issued  : %s%n" +
            "  Amount  : %s%n%n" +
            "Please arrange payment at your earliest convenience.%n" +
            "If you believe this is an error or have already paid, please ignore this email.%n%n" +
            "— %s",
            custName, invNumber, issued, amount, businessName
        );
        send(inv.getCustomer().getEmail(), subject, body, fsmFromName);
    }

    public void sendPasswordReset(String toEmail, String username, String resetLink) {
        String subject = "Reset your FieldFlow password";
        String body = String.format(
            "Hi %s,%n%n" +
            "We received a request to reset your FieldFlow password.%n%n" +
            "Click the link below to set a new password (valid for 1 hour):%n%n" +
            "%s%n%n" +
            "If you didn't request this, you can safely ignore this email — your password won't change.%n%n" +
            "— FieldFlow",
            username, resetLink
        );
        send(toEmail, subject, body, fsmFromName);
    }

    private void send(String toEmail, String subject, String body) {
        send(toEmail, subject, body, fromName);
    }

    private void send(String toEmail, String subject, String body, String senderName) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(String.format("%s <%s>", senderName, fromAddress));
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