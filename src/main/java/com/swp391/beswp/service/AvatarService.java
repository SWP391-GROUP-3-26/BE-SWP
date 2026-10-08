package com.swp391.beswp.service;

import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
public class AvatarService {

    private static final long MAX_FILE_SIZE_BYTES = 1_048_576L;
    private static final int MAX_IMAGE_DIMENSION = 2048;
    private static final long MAX_IMAGE_PIXELS = 4_194_304L;
    private static final String PUBLIC_AVATAR_PATH = "/uploads/avatars/";

    private final UserRepository userRepository;
    private final Path avatarDirectory;

    public AvatarService(
            UserRepository userRepository,
            @Value("${app.upload.avatar-dir:uploads/avatars}") String avatarDirectory
    ) {
        this.userRepository = userRepository;
        this.avatarDirectory = Paths.get(avatarDirectory).toAbsolutePath().normalize();
    }

    @Transactional
    public String uploadAvatar(String authenticatedUserId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("Vui lòng chọn ảnh đại diện");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw badRequest("Ảnh đại diện không được vượt quá 1 MB");
        }

        String extension = detectImageExtension(file);
        User user = findAuthenticatedUser(authenticatedUserId);
        String filename = UUID.randomUUID() + extension;
        Path target = avatarDirectory.resolve(filename).normalize();
        if (!target.getParent().equals(avatarDirectory)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu ảnh đại diện");
        }

        try {
            Files.createDirectories(avatarDirectory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target);
            }
        } catch (IOException exception) {
            deleteQuietly(target);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu ảnh đại diện");
        }

        String avatarUrl = PUBLIC_AVATAR_PATH + filename;
        user.setAvatarUrl(avatarUrl);
        try {
            userRepository.saveAndFlush(user);
        } catch (RuntimeException exception) {
            deleteQuietly(target);
            throw exception;
        }
        return avatarUrl;
    }

    private String detectImageExtension(MultipartFile file) {
        try (InputStream input = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) {
                throw badRequest("Tệp tải lên không phải ảnh PNG, JPEG hoặc GIF hợp lệ");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw badRequest("Chỉ chấp nhận ảnh PNG, JPEG hoặc GIF");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String extension = switch (format) {
                    case "jpg", "jpeg" -> ".jpg";
                    case "png" -> ".png";
                    case "gif" -> ".gif";
                    default -> throw badRequest("Chỉ chấp nhận ảnh PNG, JPEG hoặc GIF");
                };

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0
                        || width > MAX_IMAGE_DIMENSION
                        || height > MAX_IMAGE_DIMENSION
                        || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw badRequest("Kích thước ảnh đại diện tối đa là 2048 × 2048 pixel");
                }

                if (reader.read(0) == null) {
                    throw badRequest("Tệp tải lên không phải ảnh hợp lệ");
                }
                return extension;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw badRequest("Không thể đọc ảnh đại diện");
        }
    }

    private User findAuthenticatedUser(String authenticatedUserId) {
        final Integer userId;
        try {
            userId = Integer.valueOf(authenticatedUserId);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Danh tính đăng nhập không hợp lệ");
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy hồ sơ người dùng"
                ));
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Keep the original upload or persistence error as the response cause.
        }
    }
}
