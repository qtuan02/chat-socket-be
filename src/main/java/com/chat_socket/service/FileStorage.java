package com.chat_socket.service;

import com.chat_socket.ApplicationYaml;
import com.chat_socket.constant.RouteApi;
import com.chat_socket.dto.UploadResponse;
import com.chat_socket.exception.BadRequestException;
import com.github.f4b6a3.uuid.UuidCreator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** ponytail: local disk; swap for S3/Cloudinary when running more than one instance. */
@Component
public class FileStorage {
    private static final Pattern SAFE_EXTENSION = Pattern.compile("[A-Za-z0-9]{1,10}");

    private final Path directory;
    private final String publicUrl;

    public FileStorage(ApplicationYaml config) {
        this.directory = Path.of(config.uploadDir()).toAbsolutePath().normalize();
        this.publicUrl = config.publicUrl();
    }

    public UploadResponse store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("File is required.");

        String extension = safeExtension(file.getOriginalFilename());
        String name = UuidCreator.getTimeOrderedEpoch() + (extension.isEmpty() ? "" : "." + extension);
        try {
            Files.createDirectories(directory);
            file.transferTo(directory.resolve(name));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        return new UploadResponse(publicUrl + RouteApi.FILES + "/" + name, name, file.getSize(), file.getContentType());
    }

    /** Only the part after the last dot, only if it is plain alphanumerics; the client name is never used as a path. */
    private static String safeExtension(String originalName) {
        if (originalName == null) return "";
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) return "";
        String extension = originalName.substring(dot + 1).toLowerCase();
        return SAFE_EXTENSION.matcher(extension).matches() ? extension : "";
    }
}
