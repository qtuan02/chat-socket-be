package com.chat_socket.service;

import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.DirectMessageRequest;
import com.chat_socket.dto.GroupMessageRequest;
import com.chat_socket.dto.MessageDto;

public interface MessageService {
    BaseResponse<MessageDto> sendDirectMessage(DirectMessageRequest request);

    BaseResponse<MessageDto> sendGroupMessage(GroupMessageRequest request);
}
