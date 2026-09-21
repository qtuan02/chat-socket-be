package com.chat_socket.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chat_socket.config.GlobalExceptionHandler;
import com.chat_socket.dto.UploadResponse;
import com.chat_socket.service.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class UploadControllerTest {
    @Mock
    FileStorage fileStorage;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UploadController(fileStorage))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void upload_returns201WithUrl() throws Exception {
        when(fileStorage.store(any()))
                .thenReturn(new UploadResponse("http://host/api/files/x.png", "x.png", 3, "image/png"));

        mockMvc.perform(multipart("/v1/upload")
                        .file(new MockMultipartFile("file", "x.png", "image/png", new byte[] {1, 2, 3})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.url").value("http://host/api/files/x.png"));
    }

    @Test
    void upload_missingFilePart_returns400() throws Exception {
        mockMvc.perform(multipart("/v1/upload")).andExpect(status().isBadRequest());
    }
}
