package com.Shubham.carDealership.fsm.controller;

import com.Shubham.carDealership.fsm.model.FsmJob;
import com.Shubham.carDealership.fsm.model.JobMessage;
import com.Shubham.carDealership.fsm.repository.FsmJobRepository;
import com.Shubham.carDealership.fsm.repository.JobMessageRepository;
import com.Shubham.carDealership.model.User;
import com.Shubham.carDealership.repository.UserRepository;
import com.Shubham.carDealership.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Pure WebSocket controller — ONLY @Controller, never @RestController.
 * Mixing @RestController with @MessageMapping prevents Spring from registering
 * the handler: messages get saved to DB but are never broadcast to subscribers.
 */
@Controller
public class JobWebSocketController {

    @Autowired private JobMessageRepository msgRepo;
    @Autowired private FsmJobRepository     jobRepo;
    @Autowired private UserRepository       userRepo;
    @Autowired private EmailService         emailService;

    @Value("${app.frontend.url:https://dealership.shubhamkataria.com}")
    private String frontendUrl;

    @MessageMapping("/job/{trackingKey}/send")
    @SendTo("/topic/job/{trackingKey}/messages")
    public Map<String, Object> sendMessage(
            @DestinationVariable String trackingKey,
            @Payload Map<String, String> payload) {

        String raw        = payload.getOrDefault("senderType", "CUSTOMER");
        String senderType = (raw.equals("TECHNICIAN") || raw.equals("MANAGER")) ? raw : "CUSTOMER";
        String senderName = payload.getOrDefault("senderName",
                senderType.equals("CUSTOMER") ? "Customer"
                        : senderType.charAt(0) + senderType.substring(1).toLowerCase());
        String message = payload.getOrDefault("message", "").trim();
        if (message.isEmpty()) return null;

        Optional<FsmJob> jobOpt = jobRepo.findByTrackingKey(trackingKey);

        JobMessage msg = new JobMessage();
        msg.setTrackingKey(trackingKey);
        msg.setJobId(jobOpt.map(FsmJob::getId).orElse(null));
        msg.setSenderType(senderType);
        msg.setSenderName(senderName);
        msg.setMessage(message);
        msg.setSentAt(LocalDateTime.now());
        msgRepo.save(msg);

        // Email the business owner when a customer sends a message (fire-and-forget)
        if ("CUSTOMER".equals(senderType)) {
            jobOpt.ifPresent(job -> {
                Long ownerId = job.getBusinessOwnerId();
                String jobType = job.getJobType();
                String address = job.getAddress();
                String trackingUrl = frontendUrl + "/track/" + trackingKey;
                CompletableFuture.runAsync(() ->
                    userRepo.findById(ownerId).ifPresent(owner ->
                        emailService.sendCustomerMessageAlert(
                            owner.getEmail(), owner.getUsername(),
                            jobType, address, message, trackingUrl)
                    )
                );
            });
        }

        return JobMessagingController.msgToMap(msg);
    }
}
