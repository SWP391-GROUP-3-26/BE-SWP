package com.swp391.beswp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Service
public class ChatMediaService {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final int MAX_IMAGE_DIMENSION = 4096;
    private static final long MAX_IMAGE_PIXELS = 16_777_216L;
    private static final String PUBLIC_PATH = "/uploads/chat/";

    private final Path chatDirectory;

    public ChatMediaService(@Value("${app.upload.chat-dir:uploads/chat}") String chatDirectory) {
        this.chatDirectory = Paths.get(chatDirectory).toAbsolutePath().normalize();
    }

    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("Vui lòng chọn ảnh");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw badRequest("Ảnh chat không được vượt quá 5 MB");
        }

        String extension = detectImageExtension(file);
        String filename = UUID.randomUUID() + extension;
        Path target = chatDirectory.resolve(filename).normalize();
        if (!chatDirectory.equals(target.getParent())) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu ảnh chat");
        }

        try {
            Files.createDirectories(chatDirectory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target);
            }
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // Keep the original upload error.
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu ảnh chat");
        }
        return PUBLIC_PATH + filename;
    }

    private String detectImageExtension(MultipartFile file) {
        try (InputStream input = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) {
                throw badRequest("Tệp tải lên không phải ảnh hợp lệ");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw badRequest("Chỉ chấp nhận ảnh PNG, JPEG hoặc GIF");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String extension = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "jpg", "jpeg" -> ".jpg";
                    case "png" -> ".png";
                    case "gif" -> ".gif";
                    default -> throw badRequest("Chỉ chấp nhận ảnh PNG, JPEG hoặc GIF");
                };
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_IMAGE_DIMENSION
                        || height > MAX_IMAGE_DIMENSION || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw badRequest("Kích thước ảnh tối đa là 4096 x 4096 pixel");
                }
                if (reader.read(0) == null) {
                    throw badRequest("Tệp tải lên không phải ảnh hợp lệ");
                }
                return extension;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw badRequest("Không thể đọc ảnh chat");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
