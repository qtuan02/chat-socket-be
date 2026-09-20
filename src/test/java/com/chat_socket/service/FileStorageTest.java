package com.chat_socket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chat_socket.ApplicationYaml;
import com.chat_socket.dto.UploadResponse;
import com.chat_socket.exception.BadRequestException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class FileStorageTest {
    @TempDir
    Path dir;

    private FileStorage storage() {
        return new FileStorage(new ApplicationYaml("s", 1, 1, List.of(), dir.toString(), "http://host/api"));
    }

    @Test
    void store_emptyFile_throwsBadRequest() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> storage().store(empty))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("File is required.");
    }

    @Test
    void store_writesFileAndReturnsPublicUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.PNG", "image/png", new byte[] {1, 2, 3});

        UploadResponse response = storage().store(file);

        assertThat(response.name()).matches("[0-9a-f-]{36}\\.png");
        assertThat(response.url()).isEqualTo("http://host/api/files/" + response.name());
        assertThat(response.size()).isEqualTo(3);
        assertThat(response.contentType()).isEqualTo("image/png");
        assertThat(Files.readAllBytes(dir.resolve(response.name()))).containsExactly(1, 2, 3);
    }

    @Test
    void store_ignoresClientPathAndKeepsOnlySafeExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "../../evil.sh", "text/plain", new byte[] {1});
        MockMultipartFile noExt = new MockMultipartFile("file", "..", "text/plain", new byte[] {1});

        UploadResponse a = storage().store(file);
        UploadResponse b = storage().store(noExt);

        assertThat(a.name()).matches("[0-9a-f-]{36}\\.sh");
        assertThat(b.name()).matches("[0-9a-f-]{36}");
        assertThat(Files.list(dir)).hasSize(2);
        assertThat(Files.exists(dir.getParent().resolve("evil.sh"))).isFalse();
    }
}
