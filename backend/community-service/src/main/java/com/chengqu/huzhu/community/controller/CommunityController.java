package com.chengqu.huzhu.community.controller;

import com.chengqu.huzhu.common.api.ApiResponse;
import com.chengqu.huzhu.community.dto.CommentResponse;
import com.chengqu.huzhu.community.dto.CommunityStatsResponse;
import com.chengqu.huzhu.community.dto.CreateCommentRequest;
import com.chengqu.huzhu.community.dto.CreatePostRequest;
import com.chengqu.huzhu.community.dto.PlazaHotResponse;
import com.chengqu.huzhu.community.dto.PostResponse;
import com.chengqu.huzhu.community.dto.UpdatePostRequest;
import com.chengqu.huzhu.community.service.CommunityService;
import com.chengqu.huzhu.community.service.PlazaHotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;
    private final PlazaHotService plazaHotService;

    @GetMapping("/posts")
    public ApiResponse<Page<PostResponse>> listPosts(
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String category,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(communityService.listPosts(channel, category, pageable));
    }

    @GetMapping("/plaza/hot")
    public ApiResponse<PlazaHotResponse> plazaHot() {
        PlazaHotResponse board = plazaHotService.currentBoard();
        if (board.getItems() == null || board.getItems().isEmpty()) {
            board = plazaHotService.refresh(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")));
        }
        return ApiResponse.ok(board);
    }

    @GetMapping("/posts/{id}")
    public ApiResponse<PostResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(communityService.detail(id));
    }

    @GetMapping("/posts/author/{userId}")
    public ApiResponse<Page<PostResponse>> listByAuthor(
            @PathVariable Long userId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(communityService.listByAuthor(userId, pageable));
    }

    @PostMapping("/posts")
    public ApiResponse<PostResponse> createPost(@Valid @RequestBody CreatePostRequest request) {
        return ApiResponse.ok("发布成功", communityService.createPost(request));
    }

    @PutMapping("/posts/{id}")
    public ApiResponse<PostResponse> updatePost(@PathVariable Long id, @Valid @RequestBody UpdatePostRequest request) {
        return ApiResponse.ok("更新成功", communityService.updatePost(id, request));
    }

    @DeleteMapping("/posts/{id}")
    public ApiResponse<Void> deletePost(@PathVariable Long id) {
        communityService.deletePost(id);
        return ApiResponse.okMessage("删除成功");
    }

    @PostMapping("/posts/{id}/like")
    public ApiResponse<PostResponse> toggleLike(@PathVariable Long id) {
        return ApiResponse.ok(communityService.toggleLike(id));
    }

    @PostMapping("/posts/{id}/view")
    public ApiResponse<PostResponse> recordView(@PathVariable Long id) {
        return ApiResponse.ok(communityService.recordView(id));
    }

    @GetMapping("/posts/{id}/comments")
    public ApiResponse<java.util.List<CommentResponse>> listComments(@PathVariable Long id) {
        return ApiResponse.ok(communityService.listComments(id));
    }

    @PostMapping("/posts/{id}/comments")
    public ApiResponse<CommentResponse> addComment(@PathVariable Long id, @Valid @RequestBody CreateCommentRequest request) {
        return ApiResponse.ok("评论成功", communityService.addComment(id, request));
    }

    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> deleteComment(@PathVariable Long commentId) {
        communityService.deleteComment(commentId);
        return ApiResponse.okMessage("评论已删除");
    }

    @GetMapping("/stats")
    public ApiResponse<CommunityStatsResponse> stats() {
        return ApiResponse.ok(communityService.stats());
    }
}
