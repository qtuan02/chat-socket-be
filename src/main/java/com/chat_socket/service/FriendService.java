package com.chat_socket.service;

import com.chat_socket.dto.AcceptFriendResponse;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.FriendActionRequest;
import com.chat_socket.dto.FriendDto;
import com.chat_socket.dto.FriendRequestResponse;
import com.chat_socket.dto.FriendSearchDto;
import com.chat_socket.dto.FriendSendRequest;
import com.chat_socket.dto.PaginationRequest;
import com.chat_socket.dto.PaginationResponse;
import java.util.List;
import java.util.UUID;

public interface FriendService {
    BaseResponse<PaginationResponse<FriendDto>> getListFriend(PaginationRequest request, String search);

    BaseResponse<List<FriendSearchDto>> searchByUsername(String username);

    BaseResponse<FriendRequestResponse> getListFriendRequest();

    BaseResponse<String> sendFriendRequest(FriendSendRequest request);

    BaseResponse<AcceptFriendResponse> acceptFriendRequest(FriendActionRequest request);

    BaseResponse<String> declineFriendRequest(FriendActionRequest request);

    BaseResponse<String> cancelFriendRequest(FriendActionRequest request);

    BaseResponse<String> deleteFriend(UUID friendId);
}
