package com.chat_socket.controller;

import com.chat_socket.constant.RouteApi;
import com.chat_socket.dto.AcceptFriendResponse;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.FriendActionRequest;
import com.chat_socket.dto.FriendRequestResponse;
import com.chat_socket.dto.FriendSendRequest;
import com.chat_socket.dto.UserDto;
import com.chat_socket.service.FriendService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RouteApi.FRIEND_API)
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    @GetMapping()
    public ResponseEntity<BaseResponse<List<UserDto>>> getListFriend() {
        BaseResponse<List<UserDto>> body = friendService.getListFriend();
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

    @PostMapping("/accept")
    public ResponseEntity<BaseResponse<AcceptFriendResponse>> acceptFriendRequest(
            @Valid @RequestBody FriendActionRequest request) {
        BaseResponse<AcceptFriendResponse> body = friendService.acceptFriendRequest(request);
        return ResponseEntity.status(body.status()).body(body);
    }

    @PostMapping("/decline")
    public ResponseEntity<BaseResponse<String>> declineFriendRequest(@Valid @RequestBody FriendActionRequest request) {
        BaseResponse<String> body = friendService.declineFriendRequest(request);
        return ResponseEntity.status(body.status()).body(body);
    }
}
