package com.chat_socket.service;

import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.MessageDto;
import com.chat_socket.dto.MessageRequest;

public interface MessageService {
    BaseResponse<MessageDto> sendDirectMessage(MessageRequest request);

    BaseResponse<MessageDto> sendGroupMessage(MessageRequest request);
}
