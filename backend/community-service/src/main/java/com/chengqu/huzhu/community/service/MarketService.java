package com.chengqu.huzhu.community.service;

import com.chengqu.huzhu.api.dto.UserBrief;
import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.common.file.LocalFileUrls;
import com.chengqu.huzhu.common.security.SecurityUtils;
import com.chengqu.huzhu.common.security.UserPrincipal;
import com.chengqu.huzhu.community.dto.CreateGoodsRequest;
import com.chengqu.huzhu.community.dto.GoodsResponse;
import com.chengqu.huzhu.community.dto.GoodsStatusRequest;
import com.chengqu.huzhu.community.dto.UpdateGoodsRequest;
import com.chengqu.huzhu.community.entity.Goods;
import com.chengqu.huzhu.community.entity.GoodsContactType;
import com.chengqu.huzhu.community.entity.GoodsStatus;
import com.chengqu.huzhu.community.repository.GoodsRepository;
import com.chengqu.huzhu.community.support.UserLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 集市（二手闲置）业务逻辑。
 *
 * <p>刻意与 {@link CommunityService} 完全解耦：集市是独立的业务域，
 * 混进动态服务会让「动态」这条主链路承担不属于它的复杂度，
 * 也让并行开发时的改动范围失控。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketService {

    private final GoodsRepository goodsRepository;
    private final UserLookup userLookup;

    /**
     * 商品列表。
     *
     * <p>刻意不返回联系方式（GoodsResponse.from 不带 contact 字段）：
     * 列表一页 24 条、可翻页可筛选，是最容易被脚本批量抓取的地方，
     * 联系方式只从详情接口出（见 {@link #detail(Long)}）。
     */
    @Transactional(readOnly = true)
    public Page<GoodsResponse> list(String status, String category, String keyword, Pageable pageable) {
        // 不传 status 时默认只看在售，这是集市首页应有的默认视角
        GoodsStatus parsedStatus = GoodsStatus.requireFilter(status);
        String parsedCategory = trimToNull(category);
        String parsedKeyword = trimToNull(keyword);

        Page<Goods> page;
        if (parsedCategory != null && parsedKeyword != null) {
            page = goodsRepository.findByStatusAndCategoryAndTitleContaining(
                    parsedStatus, parsedCategory, parsedKeyword, pageable);
        } else if (parsedCategory != null) {
            page = goodsRepository.findByStatusAndCategory(parsedStatus, parsedCategory, pageable);
        } else if (parsedKeyword != null) {
            page = goodsRepository.findByStatusAndTitleContaining(parsedStatus, parsedKeyword, pageable);
        } else {
            page = goodsRepository.findByStatus(parsedStatus, pageable);
        }
        Page<GoodsResponse> result = toPage(page);
        log.info("[集市] 查询商品列表 status={}, category={}, keyword={}, page={}, size={}, total={}",
                parsedStatus, parsedCategory, parsedKeyword, pageable.getPageNumber(),
                result.getNumberOfElements(), result.getTotalElements());
        return result;
    }

    /**
     * 商品详情。每次打开都累加 view_count，用于给卖家反馈关注度。
     *
     * <p>这是联系方式唯一的出口：列表、我发布的、创建/编辑的返回体都不带它。
     */
    @Transactional
    public GoodsResponse detail(Long id) {
        Goods goods = requireGoods(id);
        goods.setViewCount((goods.getViewCount() == null ? 0 : goods.getViewCount()) + 1);
        Goods saved = goodsRepository.save(goods);
        log.info("[集市] 商品详情 id={}, sellerId={}, viewCount={}", id, saved.getSellerId(), saved.getViewCount());
        return toDetailResponse(saved);
    }

    @Transactional
    public GoodsResponse create(CreateGoodsRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        Goods goods = new Goods();
        goods.setTitle(request.getTitle().trim());
        goods.setDescription(trimToNull(request.getDescription()));
        goods.setPrice(request.getPrice());
        goods.setOriginalPrice(request.getOriginalPrice());
        goods.setCategory(trimToNull(request.getCategory()));
        // 配图必须是本站已上传的对象地址：外链图床失效后商品会变成空白卡片
        goods.setImages(LocalFileUrls.normalizeImages(request.getImages()));
        goods.setSellerId(user.getId());
        goods.setSellerName(displayName(user));
        applyContact(goods, request.getContact(), request.getContactType());
        goods.setStatus(GoodsStatus.ON_SALE);
        goods.setViewCount(0);
        Goods saved = goodsRepository.save(goods);
        log.info("[集市] 发布商品成功 id={}, sellerId={}, title={}, price={}, hasContact={}",
                saved.getId(), user.getId(), saved.getTitle(), saved.getPrice(), saved.getContact() != null);
        return toResponse(saved);
    }

    @Transactional
    public GoodsResponse update(Long id, UpdateGoodsRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        Goods goods = requireGoods(id);
        if (!goods.getSellerId().equals(user.getId())) {
            throw new BizException(403, "仅卖家可编辑商品");
        }
        goods.setTitle(request.getTitle().trim());
        goods.setDescription(trimToNull(request.getDescription()));
        goods.setPrice(request.getPrice());
        goods.setOriginalPrice(request.getOriginalPrice());
        goods.setCategory(trimToNull(request.getCategory()));
        // 传 null 表示「本次不改图」，传空数组表示「清空配图」
        if (request.getImages() != null) {
            goods.setImages(LocalFileUrls.normalizeImages(request.getImages()));
        }
        // 联系方式沿用同一约定：null = 本次不改，空串 = 清空。
        // 让「只改价格」的调用方不必先读一遍当前值再回写，避免把联系方式误清空
        if (request.getContact() != null) {
            applyContact(goods, request.getContact(), request.getContactType());
        }
        Goods saved = goodsRepository.save(goods);
        log.info("[集市] 更新商品 id={}, sellerId={}, title={}", id, user.getId(), saved.getTitle());
        return toResponse(saved);
    }

    /** 改状态。SOLD 表示已售出：集市里最常见的动作就是「卖出去了」。 */
    @Transactional
    public GoodsResponse changeStatus(Long id, GoodsStatusRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        Goods goods = requireGoods(id);
        if (!goods.getSellerId().equals(user.getId())) {
            throw new BizException(403, "仅卖家可修改商品状态");
        }
        GoodsStatus target = GoodsStatus.require(request.getStatus());
        GoodsStatus previous = goods.getStatus();
        goods.setStatus(target);
        Goods saved = goodsRepository.save(goods);
        log.info("[集市] 修改商品状态 id={}, sellerId={}, {} -> {}", id, user.getId(), previous, target);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        UserPrincipal user = SecurityUtils.currentUser();
        Goods goods = requireGoods(id);
        boolean isSeller = goods.getSellerId().equals(user.getId());
        boolean isAdmin = isAdmin(user);
        if (!isSeller && !isAdmin) {
            throw new BizException(403, "仅卖家或管理员可删除商品");
        }
        goodsRepository.delete(goods);
        log.info("[集市] 删除商品 id={}, operatorId={}, isAdmin={}", id, user.getId(), isAdmin);
    }

    /**
     * 我发布的商品（含已售出、已下架）。
     *
     * <p>与列表同样不带联系方式：这里虽然只返回自己的商品，但接口形态仍是「列表」，
     * 卖家要看自己的联系方式，打开详情（或发布框）即可。
     */
    @Transactional(readOnly = true)
    public Page<GoodsResponse> mine(Pageable pageable) {
        Long userId = SecurityUtils.currentUser().getId();
        Page<GoodsResponse> page = toPage(goodsRepository.findBySellerId(userId, pageable));
        log.info("[集市] 我发布的商品 userId={}, total={}", userId, page.getTotalElements());
        return page;
    }

    // ------------------------------------------------------------------
    // 装配层
    // ------------------------------------------------------------------

    private Page<GoodsResponse> toPage(Page<Goods> page) {
        return new PageImpl<>(toResponses(page.getContent()), page.getPageable(), page.getTotalElements());
    }

    /** 列表 / 卡片 / 写操作返回体：不带联系方式。 */
    private GoodsResponse toResponse(Goods goods) {
        return toResponses(List.of(goods), false).get(0);
    }

    /** 详情返回体：唯一带联系方式的装配出口。 */
    private GoodsResponse toDetailResponse(Goods goods) {
        return toResponses(List.of(goods), true).get(0);
    }

    private List<GoodsResponse> toResponses(List<Goods> goodsList) {
        return toResponses(goodsList, false);
    }

    /**
     * 批量装配。
     *
     * @param withContact 是否带上联系方式；只有详情接口传 true，
     *                    见 GoodsResponse.contact 关于「为什么不放列表」的说明
     */
    private List<GoodsResponse> toResponses(List<Goods> goodsList, boolean withContact) {
        if (goodsList.isEmpty()) {
            return List.of();
        }
        List<GoodsResponse> result = new ArrayList<>(goodsList.size());
        for (Goods goods : goodsList) {
            result.add(withContact ? GoodsResponse.fromDetail(goods) : GoodsResponse.from(goods));
        }
        fillSellerInfo(result);
        return result;
    }

    /**
     * 通过 Feign 批量补全卖家昵称与头像。
     *
     * <p>无论一页多少条商品，只发起 1 次跨服务调用；
     * 调用失败或卖家已注销时保留实体上的冗余昵称、头像留空，由前端兜底。
     */
    private void fillSellerInfo(List<GoodsResponse> responses) {
        Set<Long> sellerIds = new HashSet<>();
        for (GoodsResponse response : responses) {
            if (response.getSellerId() != null) {
                sellerIds.add(response.getSellerId());
            }
        }
        Map<Long, UserBrief> users = userLookup.byIds(sellerIds);
        if (users.isEmpty()) {
            return;
        }
        for (GoodsResponse response : responses) {
            UserBrief seller = users.get(response.getSellerId());
            if (seller == null) {
                continue;
            }
            if (StringUtils.hasText(seller.nickname())) {
                response.setSellerName(seller.nickname());
            }
            response.setSellerAvatar(seller.avatar());
        }
    }

    private Goods requireGoods(Long id) {
        return goodsRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "商品不存在"));
    }

    private String displayName(UserPrincipal user) {
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname();
        }
        if (StringUtils.hasText(user.getPhone())) {
            return user.getPhone();
        }
        return "用户" + user.getId();
    }

    private boolean isAdmin(UserPrincipal user) {
        String role = user.getRole();
        if (role == null) {
            return false;
        }
        if (role.startsWith("ROLE_")) {
            role = role.substring(5);
        }
        return "ADMIN".equals(role);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 把请求里的联系方式写到实体上。
     *
     * <p>规则与「为什么」：
     * <ul>
     *   <li>联系方式为空 → 类型一并清空。库里不该留下「有类型没号码」的半截数据，
     *       前端渲染出「微信：」这种空标签比不填更让人困惑。</li>
     *   <li>写了联系方式但没选类型 → 按「其他」保存。联系方式是买家唯一能触达卖家的途径，
     *       不能因为少选一个下拉项就把它整条丢掉；类型只影响前端的渲染方式。</li>
     *   <li>类型编码非法 → 直接报错。这种情况只可能是前端或调用方写错了，
     *       静默当成「其他」会让人以为已经存对，等买家看不到按钮才发现。</li>
     * </ul>
     */
    private void applyContact(Goods goods, String rawContact, String rawType) {
        String contact = trimToNull(rawContact);
        if (contact == null) {
            goods.setContact(null);
            goods.setContactType(null);
            return;
        }
        GoodsContactType type = GoodsContactType.parseOrNull(rawType);
        if (type == null && StringUtils.hasText(rawType)) {
            throw new BizException("联系方式类型无效");
        }
        goods.setContact(contact);
        goods.setContactType(type == null ? GoodsContactType.OTHER : type);
    }
}
