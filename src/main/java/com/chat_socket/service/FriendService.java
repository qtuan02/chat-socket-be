package com.chat_socket.service;

import com.chat_socket.dto.AcceptFriendResponse;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.FriendActionRequest;
import com.chat_socket.dto.FriendRequestResponse;
import com.chat_socket.dto.FriendSendRequest;
import com.chat_socket.dto.UserDto;
import java.util.List;

public interface FriendService {
    BaseResponse<List<UserDto>> getListFriend();

    BaseResponse<FriendRequestResponse> getListFriendRequest();

    BaseResponse<String> sendFriendRequest(FriendSendRequest request);

    BaseResponse<AcceptFriendResponse> acceptFriendRequest(FriendActionRequest request);

    BaseResponse<String> declineFriendRequest(FriendActionRequest request);
}
