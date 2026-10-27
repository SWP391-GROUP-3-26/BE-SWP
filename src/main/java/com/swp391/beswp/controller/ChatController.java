package com.swp391.beswp.controller;

import com.swp391.beswp.dto.ChatDtos;
import com.swp391.beswp.service.ChatMediaService;
import com.swp391.beswp.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatMediaService chatMediaService;

    @GetMapping("/contacts")
    public ChatDtos.ContactListResponse contacts(
            Authentication authentication,
            @RequestParam(required = false) String search
    ) {
        return chatService.getContacts(authentication.getName(), search);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ChatDtos.MessagePageResponse messages(
            Authentication authentication,
            @PathVariable Integer conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        return chatService.getMessages(authentication.getName(), conversationId, page, size);
    }

    @PutMapping("/conversations/{conversationId}/read")
    public ChatDtos.ReadResponse markRead(
            Authentication authentication,
            @PathVariable Integer conversationId
    ) {
        return chatService.markRead(authentication.getName(), conversationId);
    }

    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ChatDtos.UploadResponse uploadImage(@RequestPart("file") MultipartFile file) {
        String url = chatMediaService.upload(file);
        return new ChatDtos.UploadResponse(true, "Tải ảnh chat thành công", url);
    }
}
