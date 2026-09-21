package com.chat_socket.service;

import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.FriendDto;
import com.chat_socket.dto.FriendRequestResponse;
import com.chat_socket.dto.FriendSendRequest;
import com.chat_socket.dto.PaginationRequest;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.dto.UserSummaryDto;
import java.util.UUID;

public interface FriendService {
    BaseResponse<PaginationResponse<FriendDto>> getListFriend(PaginationRequest request, String search);

    BaseResponse<FriendRequestResponse> getListFriendRequest();

    BaseResponse<String> sendFriendRequest(FriendSendRequest request);

    BaseResponse<UserSummaryDto> acceptFriendRequest(UUID requestId);

    BaseResponse<String> declineFriendRequest(UUID requestId);

    BaseResponse<String> cancelFriendRequest(UUID requestId);

    BaseResponse<String> deleteFriend(UUID friendId);
}
