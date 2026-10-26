package com.swp391.beswp.service;

import com.swp391.beswp.dto.ChatDtos;
import com.swp391.beswp.entity.ChatConversation;
import com.swp391.beswp.entity.ChatMessage;
import com.swp391.beswp.entity.ClassBooking;
import com.swp391.beswp.entity.ClassEntity;
import com.swp391.beswp.entity.ClassSession;
import com.swp391.beswp.entity.Role;
import com.swp391.beswp.entity.Subject;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.ChatConversationRepository;
import com.swp391.beswp.repository.ChatMessageRepository;
import com.swp391.beswp.repository.ClassBookingRepository;
import com.swp391.beswp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final ClassBookingRepository bookings = mock(ClassBookingRepository.class);
    private final ChatConversationRepository conversations = mock(ChatConversationRepository.class);
    private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    private ChatService service;
    private User member;
    private User coach;
    private ClassBooking eligibleBooking;

    @BeforeEach
    void setUp() {
        service = new ChatService(users, bookings, conversations, messages);
        member = user(1, "Member", "Nguyễn Minh Anh");
        coach = user(2, "Coach", "HLV Alex Trần");

        Subject subject = Subject.builder().id(7).name("Yoga").build();
        ClassEntity classEntity = ClassEntity.builder()
                .id(10).name("Yoga cơ bản").status("Active")
                .subject(subject).coach(coach).build();
        ClassSession session = ClassSession.builder()
                .id(20).classEntity(classEntity).date(LocalDate.now()).build();
        eligibleBooking = ClassBooking.builder().id(30).user(member).session(session).status("Booked").build();

        when(users.findById(1)).thenReturn(Optional.of(member));
        when(users.findById(2)).thenReturn(Optional.of(coach));
        when(messages.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(99L);
            return message;
        });
    }

    @Test
    void memberCanSendTextToCoachFromEligibleClass() {
        when(bookings.findChatBookingsForPair(eq(1), eq(2), any(LocalDate.class)))
                .thenReturn(List.of(eligibleBooking));
        when(conversations.findByMember_IdAndCoach_Id(1, 2)).thenReturn(Optional.empty());
        when(conversations.saveAndFlush(any(ChatConversation.class))).thenAnswer(invocation -> {
            ChatConversation conversation = invocation.getArgument(0);
            conversation.setId(40);
            return conversation;
        });

        ChatDtos.MessageResponse response = service.send(
                "1", new ChatDtos.SendRequest(2, "Em cần chỉnh tư thế", "TEXT", null));

        assertEquals(99L, response.messageId());
        assertEquals(40, response.conversationId());
        assertEquals("Em cần chỉnh tư thế", response.content());
        assertEquals("TEXT", response.messageType());
        verify(messages).save(any(ChatMessage.class));
    }

    @Test
    void senderCannotChatWithoutEligibleClass() {
        when(bookings.findChatBookingsForPair(eq(1), eq(2), any(LocalDate.class)))
                .thenReturn(List.of());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.send("1", new ChatDtos.SendRequest(2, "Xin chào", "TEXT", null)));

        assertEquals(403, exception.getStatusCode().value());
        verify(messages, never()).save(any());
    }

    @Test
    void imageMessageMustUseUploadedChatUrl() {
        when(bookings.findChatBookingsForPair(eq(1), eq(2), any(LocalDate.class)))
                .thenReturn(List.of(eligibleBooking));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.send("1", new ChatDtos.SendRequest(
                        2, null, "IMAGE", "https://example.com/untrusted.jpg")));

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("upload-image"));
        verify(messages, never()).save(any());
    }

    private User user(int id, String roleName, String fullName) {
        Role role = new Role();
        role.setRoleName(roleName);
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setFullName(fullName);
        user.setStatus("Active");
        return user;
    }
}
