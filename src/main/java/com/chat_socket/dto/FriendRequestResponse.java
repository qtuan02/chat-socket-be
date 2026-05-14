package com.chat_socket.dto;

import java.util.List;

public record FriendRequestResponse(
        List<FriendRequestSentDto> sentRequests, List<FriendRequestReceviedDto> receivedRequests) {}
