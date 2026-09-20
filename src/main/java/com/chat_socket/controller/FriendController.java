package com.chat_socket.controller;

import com.chat_socket.constant.RouteApi;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.FriendDto;
import com.chat_socket.dto.FriendRequestResponse;
import com.chat_socket.dto.FriendSendRequest;
import com.chat_socket.dto.PaginationRequest;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.dto.UserSummaryDto;
import com.chat_socket.service.FriendService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RouteApi.FRIEND_API)
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    @GetMapping
    public ResponseEntity<BaseResponse<PaginationResponse<FriendDto>>> getListFriend(
            @ModelAttribute PaginationRequest request, @RequestParam(required = false) String search) {
        BaseResponse<PaginationResponse<FriendDto>> body = friendService.getListFriend(request, search);
        return ResponseEntity.status(body.status()).body(body);
    }

    @GetMapping("/request")
    public ResponseEntity<BaseResponse<FriendRequestResponse>> getListFriendRequest() {
        BaseResponse<FriendRequestResponse> body = friendService.getListFriendRequest();
        return ResponseEntity.status(body.status()).body(body);
    }

    @PostMapping("/request")
    public ResponseEntity<BaseResponse<String>> sendFriendRequest(@Valid @RequestBody FriendSendRequest request) {
        BaseResponse<String> body = friendService.sendFriendRequest(request);
        return ResponseEntity.status(body.status()).body(body);
    }

    @PostMapping("/request/{requestId}/accept")
    public ResponseEntity<BaseResponse<UserSummaryDto>> acceptFriendRequest(@PathVariable UUID requestId) {
        BaseResponse<UserSummaryDto> body = friendService.acceptFriendRequest(requestId);
        return ResponseEntity.status(body.status()).body(body);
    }

    @PostMapping("/request/{requestId}/decline")
    public ResponseEntity<BaseResponse<String>> declineFriendRequest(@PathVariable UUID requestId) {
        BaseResponse<String> body = friendService.declineFriendRequest(requestId);
        return ResponseEntity.status(body.status()).body(body);
    }

    @DeleteMapping("/request/{requestId}")
    public ResponseEntity<BaseResponse<String>> cancelFriendRequest(@PathVariable UUID requestId) {
        BaseResponse<String> body = friendService.cancelFriendRequest(requestId);
        return ResponseEntity.status(body.status()).body(body);
    }

    @DeleteMapping("/{friendId}")
    public ResponseEntity<BaseResponse<String>> deleteFriend(@PathVariable UUID friendId) {
        BaseResponse<String> body = friendService.deleteFriend(friendId);
        return ResponseEntity.status(body.status()).body(body);
    }
}
