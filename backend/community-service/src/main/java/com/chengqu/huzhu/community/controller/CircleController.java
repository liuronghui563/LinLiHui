package com.chengqu.huzhu.community.controller;

import com.chengqu.huzhu.common.api.ApiResponse;
import com.chengqu.huzhu.community.dto.CircleMemberResponse;
import com.chengqu.huzhu.community.dto.CircleMuteRequest;
import com.chengqu.huzhu.community.dto.CirclePostRequest;
import com.chengqu.huzhu.community.dto.CircleResponse;
import com.chengqu.huzhu.community.dto.CircleRoleRequest;
import com.chengqu.huzhu.community.dto.CreateCircleRequest;
import com.chengqu.huzhu.community.dto.CreatePostRequest;
import com.chengqu.huzhu.community.dto.PostResponse;
import com.chengqu.huzhu.community.service.CircleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 圈子（兴趣小组）接口。全部需要登录。
 */
@RestController
@RequestMapping("/api/circle")
@RequiredArgsConstructor
public class CircleController {

    private final CircleService circleService;

    /** 圈子列表，带 joined 标记当前用户是否已加入。 */
    @GetMapping("/list")
    public ApiResponse<Page<CircleResponse>> list(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(circleService.list(keyword, pageable));
    }

    /** 我加入的圈子（含我创建并作为圈主的）。 */
    @GetMapping("/mine")
    public ApiResponse<Page<CircleResponse>> mine(
            @PageableDefault(size = 10, sort = "joinedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(circleService.mine(pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<CircleResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(circleService.detail(id));
    }

    @PostMapping
    public ApiResponse<CircleResponse> create(@Valid @RequestBody CreateCircleRequest request) {
        return ApiResponse.ok("圈子创建成功", circleService.create(request));
    }

    @PostMapping("/{id}/join")
    public ApiResponse<CircleResponse> join(@PathVariable Long id) {
        return ApiResponse.ok("已加入圈子", circleService.join(id));
    }

    @PostMapping("/{id}/leave")
    public ApiResponse<CircleResponse> leave(@PathVariable Long id) {
        return ApiResponse.ok("已退出圈子", circleService.leave(id));
    }

    /** 关闭圈子：圈主不能退出，只能关闭（仅圈主或管理员）。 */
    @PostMapping("/{id}/close")
    public ApiResponse<CircleResponse> close(@PathVariable Long id) {
        return ApiResponse.ok("圈子已关闭", circleService.close(id));
    }

    /** 圈内帖子分页，按收录时间倒序。 */
    @GetMapping("/{id}/posts")
    public ApiResponse<Page<PostResponse>> listPosts(
            @PathVariable Long id,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(circleService.listPosts(id, pageable));
    }

    /** 在圈子里直接发帖，同时出现在发现流。 */
    @PostMapping("/{id}/compose")
    public ApiResponse<PostResponse> compose(
            @PathVariable Long id,
            @Valid @RequestBody CreatePostRequest request) {
        return ApiResponse.ok("已发布到圈子", circleService.compose(id, request));
    }

    /** 把一条已有动态收录进圈子（仅圈主或管理员）。 */
    @PostMapping("/{id}/posts")
    public ApiResponse<Void> addPost(@PathVariable Long id, @Valid @RequestBody CirclePostRequest request) {
        circleService.addPost(id, request);
        return ApiResponse.okMessage("已加入圈子");
    }

    /** 从圈子下线一条帖子，原动态仍留在发现流。 */
    @DeleteMapping("/{id}/posts/{postId}")
    public ApiResponse<Void> removePost(@PathVariable Long id, @PathVariable Long postId) {
        circleService.removePost(id, postId);
        return ApiResponse.okMessage("已从圈子下线");
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<CircleMemberResponse>> members(@PathVariable Long id) {
        return ApiResponse.ok(circleService.listMembers(id));
    }

    @PostMapping("/{id}/members/{userId}/kick")
    public ApiResponse<Void> kick(@PathVariable Long id, @PathVariable Long userId) {
        circleService.kick(id, userId);
        return ApiResponse.okMessage("已移出圈子");
    }

    @PostMapping("/{id}/members/{userId}/mute")
    public ApiResponse<Void> mute(
            @PathVariable Long id,
            @PathVariable Long userId,
            @Valid @RequestBody CircleMuteRequest request) {
        circleService.mute(id, userId, request);
        return ApiResponse.okMessage(Boolean.TRUE.equals(request.getMuted()) ? "已限制发帖" : "已解除发帖限制");
    }

    @PostMapping("/{id}/members/{userId}/role")
    public ApiResponse<Void> setRole(
            @PathVariable Long id,
            @PathVariable Long userId,
            @Valid @RequestBody CircleRoleRequest request) {
        circleService.setRole(id, userId, request);
        return ApiResponse.okMessage("ADMIN".equalsIgnoreCase(request.getRole()) ? "已设为管理员" : "已取消管理员");
    }
}
