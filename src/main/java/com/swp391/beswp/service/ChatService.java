package com.swp391.beswp.service;

import com.swp391.beswp.dto.ChatDtos;
import com.swp391.beswp.entity.ChatConversation;
import com.swp391.beswp.entity.ChatMessage;
import com.swp391.beswp.entity.ClassBooking;
import com.swp391.beswp.entity.ClassEntity;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.ChatConversationRepository;
import com.swp391.beswp.repository.ChatMessageRepository;
import com.swp391.beswp.repository.ClassBookingRepository;
import com.swp391.beswp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String ROLE_MEMBER = "Member";
    private static final String ROLE_COACH = "Coach";
    private static final String TYPE_TEXT = "TEXT";
    private static final String TYPE_IMAGE = "IMAGE";
    private static final String CHAT_MEDIA_PREFIX = "/uploads/chat/";

    private final UserRepository userRepository;
    private final ClassBookingRepository classBookingRepository;
    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;

    @Transactional(readOnly = true)
    public ChatDtos.ContactListResponse getContacts(String principal, String search) {
        User currentUser = getChatUser(principal);
        boolean memberView = hasRole(currentUser, ROLE_MEMBER);
        List<ClassBooking> bookings = memberView
                ? classBookingRepository.findChatBookingsForMember(currentUser.getId(), LocalDate.now())
                : classBookingRepository.findChatBookingsForCoach(currentUser.getId(), LocalDate.now());

        Map<Integer, ClassBooking> bookingByContact = new LinkedHashMap<>();
        for (ClassBooking booking : bookings) {
            User contact = memberView
                    ? booking.getSession().getClassEntity().getCoach()
                    : booking.getUser();
            bookingByContact.putIfAbsent(contact.getId(), booking);
        }

        Map<Integer, ChatConversation> conversationByContact = new HashMap<>();
        for (ChatConversation conversation : conversationRepository.findAllForParticipant(currentUser.getId())) {
            User contact = memberView ? conversation.getCoach() : conversation.getMember();
            conversationByContact.put(contact.getId(), conversation);
        }

        String normalizedSearch = StringUtils.hasText(search)
                ? search.trim().toLowerCase(Locale.ROOT)
                : null;
        List<ChatDtos.ContactResponse> contacts = new ArrayList<>();
        for (Map.Entry<Integer, ClassBooking> entry : bookingByContact.entrySet()) {
            ClassBooking booking = entry.getValue();
            ClassEntity classEntity = booking.getSession().getClassEntity();
            User contact = memberView ? classEntity.getCoach() : booking.getUser();
            if (normalizedSearch != null
                    && (contact.getFullName() == null
                    || !contact.getFullName().toLowerCase(Locale.ROOT).contains(normalizedSearch))) {
                continue;
            }

            ChatConversation conversation = conversationByContact.get(contact.getId());
            long unread = conversation == null ? 0L
                    : messageRepository.countByConversation_IdAndReceiver_IdAndReadFalse(
                            conversation.getId(), currentUser.getId());
            contacts.add(new ChatDtos.ContactResponse(
                    conversation == null ? null : conversation.getId(),
                    contact.getId(),
                    contact.getFullName(),
                    contact.getAvatarUrl(),
                    roleName(contact),
                    classEntity.getId(),
                    classEntity.getName(),
                    classEntity.getSubject() == null ? null : classEntity.getSubject().getName(),
                    conversation == null ? null : conversation.getLastMessage(),
                    conversation == null ? null : conversation.getLastMessageAt(),
                    unread
            ));
        }

        contacts.sort(Comparator
                .comparing(ChatDtos.ContactResponse::lastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(contact -> contact.fullName() == null ? "" : contact.fullName(),
                        String.CASE_INSENSITIVE_ORDER));
        return new ChatDtos.ContactListResponse(true, "Lấy danh sách liên hệ thành công", contacts);
    }

    @Transactional
    public ChatDtos.MessageResponse send(String principal, ChatDtos.SendRequest request) {
        User sender = getChatUser(principal);
        User receiver = userRepository.findById(request.receiverId())
                .orElseThrow(() -> notFound("Không tìm thấy người nhận"));
        ensureActive(receiver);

        boolean senderIsMember = hasRole(sender, ROLE_MEMBER);
        if (senderIsMember == hasRole(receiver, ROLE_MEMBER)
                || (!senderIsMember && !hasRole(receiver, ROLE_MEMBER))) {
            throw forbidden("Member chỉ có thể nhắn Coach và Coach chỉ có thể nhắn Member");
        }

        Integer memberId = senderIsMember ? sender.getId() : receiver.getId();
        Integer coachId = senderIsMember ? receiver.getId() : sender.getId();
        List<ClassBooking> eligibleBookings = classBookingRepository.findChatBookingsForPair(
                memberId, coachId, LocalDate.now());
        if (eligibleBookings.isEmpty()) {
            throw forbidden("Bạn không có lớp học hợp lệ với người nhận này");
        }

        String messageType = normalizeMessageType(request.messageType());
        String content = normalizeContent(request.content());
        String mediaUrl = normalizeMediaUrl(request.mediaUrl());
        validateMessage(messageType, content, mediaUrl);

        ChatConversation conversation = conversationRepository.findByMember_IdAndCoach_Id(memberId, coachId)
                .orElseGet(() -> createConversation(eligibleBookings.getFirst()));

        LocalDateTime now = LocalDateTime.now();
        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setContent(content);
        message.setMessageType(messageType);
        message.setMediaUrl(mediaUrl);
        message.setRead(false);
        message.setCreatedAt(now);
        message = messageRepository.save(message);

        String preview = TYPE_IMAGE.equals(messageType)
                ? (StringUtils.hasText(content) ? content : "[Hình ảnh]")
                : content;
        conversation.setLastMessage(preview.length() > 1000 ? preview.substring(0, 1000) : preview);
        conversation.setLastMessageAt(now);
        conversationRepository.save(conversation);
        return toMessageResponse(message);
    }

    @Transactional(readOnly = true)
    public ChatDtos.MessagePageResponse getMessages(
            String principal,
            Integer conversationId,
            int page,
            int size
    ) {
        User currentUser = getChatUser(principal);
        ChatConversation conversation = getAuthorizedConversation(conversationId, currentUser.getId());
        if (page < 0 || size < 1 || size > 100) {
            throw badRequest("page phải từ 0 và size phải từ 1 đến 100");
        }

        Page<ChatMessage> result = messageRepository.findByConversation_IdOrderByCreatedAtDescIdDesc(
                conversation.getId(), PageRequest.of(page, size));
        List<ChatDtos.MessageResponse> messages = result.getContent().stream()
                .map(this::toMessageResponse)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        java.util.Collections.reverse(messages);
        return new ChatDtos.MessagePageResponse(
                true,
                "Lấy tin nhắn thành công",
                page,
                size,
                result.getTotalElements(),
                result.hasNext(),
                messages
        );
    }

    @Transactional
    public ChatDtos.ReadResponse markRead(String principal, Integer conversationId) {
        User currentUser = getChatUser(principal);
        ChatConversation conversation = getAuthorizedConversation(conversationId, currentUser.getId());
        int updated = messageRepository.markConversationRead(conversation.getId(), currentUser.getId());
        return new ChatDtos.ReadResponse(true, "Đã đánh dấu tin nhắn là đã đọc", updated);
    }

    private ChatConversation createConversation(ClassBooking booking) {
        ClassEntity classEntity = booking.getSession().getClassEntity();
        ChatConversation conversation = new ChatConversation();
        conversation.setMember(booking.getUser());
        conversation.setCoach(classEntity.getCoach());
        conversation.setClassEntity(classEntity);
        conversation.setCreatedAt(LocalDateTime.now());
        return conversationRepository.saveAndFlush(conversation);
    }

    private ChatConversation getAuthorizedConversation(Integer conversationId, Integer userId) {
        ChatConversation conversation = conversationRepository.findDetailedById(conversationId)
                .orElseThrow(() -> notFound("Không tìm thấy cuộc trò chuyện"));
        if (!conversation.getMember().getId().equals(userId)
                && !conversation.getCoach().getId().equals(userId)) {
            throw forbidden("Bạn không thuộc cuộc trò chuyện này");
        }
        return conversation;
    }

    private User getChatUser(String principal) {
        final Integer userId;
        try {
            userId = Integer.valueOf(principal);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Danh tính đăng nhập không hợp lệ");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Người dùng không tồn tại"));
        if (!hasRole(user, ROLE_MEMBER) && !hasRole(user, ROLE_COACH)) {
            throw forbidden("Chức năng chat chỉ dành cho Member và Coach");
        }
        ensureActive(user);
        return user;
    }

    private void ensureActive(User user) {
        if (user.getStatus() != null && !"Active".equalsIgnoreCase(user.getStatus())) {
            throw forbidden("Tài khoản không hoạt động");
        }
    }

    private String normalizeMessageType(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : TYPE_TEXT;
    }

    private String normalizeContent(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeMediaUrl(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void validateMessage(String type, String content, String mediaUrl) {
        if (!TYPE_TEXT.equals(type) && !TYPE_IMAGE.equals(type)) {
            throw badRequest("messageType chỉ nhận TEXT hoặc IMAGE");
        }
        if (TYPE_TEXT.equals(type) && !StringUtils.hasText(content)) {
            throw badRequest("Tin nhắn chữ không được để trống");
        }
        if (TYPE_IMAGE.equals(type)
                && (!StringUtils.hasText(mediaUrl) || !mediaUrl.startsWith(CHAT_MEDIA_PREFIX))) {
            throw badRequest("Tin nhắn ảnh phải sử dụng ảnh đã tải lên từ /api/chat/upload-image");
        }
    }

    private ChatDtos.MessageResponse toMessageResponse(ChatMessage message) {
        return new ChatDtos.MessageResponse(
                message.getId(),
                message.getConversation().getId(),
                message.getSender().getId(),
                message.getSender().getFullName(),
                message.getSender().getAvatarUrl(),
                message.getReceiver().getId(),
                message.getReceiver().getFullName(),
                message.getReceiver().getAvatarUrl(),
                message.getContent(),
                message.getMessageType(),
                message.getMediaUrl(),
                Boolean.TRUE.equals(message.getRead()),
                message.getCreatedAt()
        );
    }

    private boolean hasRole(User user, String expectedRole) {
        return expectedRole.equalsIgnoreCase(roleName(user));
    }

    private String roleName(User user) {
        return user.getRole() == null ? "" : user.getRole().getRoleName();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
