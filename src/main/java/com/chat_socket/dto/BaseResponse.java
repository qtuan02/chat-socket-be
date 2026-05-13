package com.chat_socket.dto;

public record BaseResponse<T>(T data, String message, int status) {}
