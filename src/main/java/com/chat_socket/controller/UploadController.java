package com.chat_socket.controller;

import com.chat_socket.constant.RouteApi;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.UploadResponse;
import com.chat_socket.service.FileStorage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(RouteApi.UPLOAD_API)
public class UploadController {
    private final FileStorage fileStorage;

    public UploadController(FileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<UploadResponse>> upload(@RequestParam("file") MultipartFile file) {
        BaseResponse<UploadResponse> body =
                new BaseResponse<>(fileStorage.store(file), "File uploaded successfully.", HttpStatus.CREATED.value());
        return ResponseEntity.status(body.status()).body(body);
    }
}
