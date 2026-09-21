package com.chat_socket.dto;

import java.util.List;

public record FriendRequestResponse(List<FriendRequestDto> sentRequests, List<FriendRequestDto> receivedRequests) {}
