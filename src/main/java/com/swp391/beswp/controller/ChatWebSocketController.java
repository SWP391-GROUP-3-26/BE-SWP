package com.swp391.beswp.controller;

import com.swp391.beswp.dto.ChatDtos;
import com.swp391.beswp.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void send(
            Authentication authentication,
            @Valid @Payload ChatDtos.SendRequest request
    ) {
        ChatDtos.MessageResponse response = chatService.send(authentication.getName(), request);
        messagingTemplate.convertAndSendToUser(
                String.valueOf(response.receiverId()), "/queue/messages", response);
        messagingTemplate.convertAndSendToUser(
                String.valueOf(response.senderId()), "/queue/messages", response);
    }
}
