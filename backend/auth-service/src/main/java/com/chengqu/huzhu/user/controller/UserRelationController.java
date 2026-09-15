package com.chengqu.huzhu.user.controller;

import com.chengqu.huzhu.api.dto.UserBrief;
import com.chengqu.huzhu.common.api.ApiResponse;
import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.user.dto.UserRelationResponse;
import com.chengqu.huzhu.user.service.UserRelationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 关注与拉黑。
 *
 * <p>路径挂在 {@code /api/user} 下，与既有的 {@code UserController} 共用前缀。
 * Spring 会优先匹配字面量路径（如 {@code /following}）而不是 {@code /{id}}，
 * 因此这些列表接口不会被当作 userId 解析。
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserRelationController {

    /** 批量关系查询的 id 上限。一页 50 行，留一倍余量即可。 */
    private static final int MAX_BATCH_IDS = 100;

    private final UserRelationService relationService;

    @PostMapping("/{id}/follow")
    public ApiResponse<UserRelationResponse> follow(@PathVariable("id") Long id) {
        return ApiResponse.ok("已关注", relationService.follow(id));
    }

    @DeleteMapping("/{id}/follow")
    public ApiResponse<UserRelationResponse> unfollow(@PathVariable("id") Long id) {
        return ApiResponse.ok("已取消关注", relationService.unfollow(id));
    }

    @PostMapping("/{id}/block")
    public ApiResponse<UserRelationResponse> block(@PathVariable("id") Long id) {
        return ApiResponse.ok("已拉黑，双方互不可见", relationService.block(id));
    }

    @DeleteMapping("/{id}/block")
    public ApiResponse<UserRelationResponse> unblock(@PathVariable("id") Long id) {
        return ApiResponse.ok("已取消拉黑", relationService.unblock(id));
    }

    /** 当前用户与目标用户的关系状态，供个人主页渲染按钮 */
    @GetMapping("/{id}/relation")
    public ApiResponse<UserRelationResponse> relation(@PathVariable("id") Long id) {
        return ApiResponse.ok(relationService.relation(id));
    }

    /**
     * 批量关系状态：一次问清「我与这一批用户分别是什么关系」。
     *
     * <p>存在的理由就是列表页。关注 / 粉丝 / 黑名单一页 50 行，如果前端逐行调
     * {@code /{id}/relation}，就是 51 次 HTTP 与约 300 条关系查询；这个接口把整页
     * 压成一次请求、固定 6 条 SQL，且与页大小无关。
     *
     * <p>只返回**调用者自己**与这些人的关系，不泄露第三方之间的关注/拉黑情况，
     * 因此没有引入新的信息暴露面（同样的数据本来就能逐条查到）。
     *
     * @param ids 目标用户 id，逗号分隔；超过 {@value #MAX_BATCH_IDS} 个直接拒绝——
     *            这个接口的价值在于「一次取一页」，不打算变成全站关系导出工具
     */
    @GetMapping("/relations")
    public ApiResponse<Map<Long, UserRelationResponse>> relations(@RequestParam("ids") List<Long> ids) {
        if (ids.size() > MAX_BATCH_IDS) {
            throw new BizException("一次最多查询 " + MAX_BATCH_IDS + " 个用户的关系");
        }
        return ApiResponse.ok(relationService.relations(ids));
    }

    @GetMapping("/following")
    public ApiResponse<Page<UserBrief>> following(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(relationService.following(pageable));
    }

    @GetMapping("/followers")
    public ApiResponse<Page<UserBrief>> followers(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(relationService.followers(pageable));
    }

    @GetMapping("/blocked")
    public ApiResponse<Page<UserBrief>> blocked(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(relationService.blocked(pageable));
    }
}
