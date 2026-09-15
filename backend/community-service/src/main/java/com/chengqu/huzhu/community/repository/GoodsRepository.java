package com.chengqu.huzhu.community.repository;

import com.chengqu.huzhu.community.entity.Goods;
import com.chengqu.huzhu.community.entity.GoodsStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 集市商品仓储。
 *
 * <p>为什么把「状态」「分类」「关键词」拆成 4 个派生查询而不是写一个带
 * {@code (:param is null or ...)} 的动态 JPQL：后者会让 (status, created_at)
 * 索引失效（优化器无法在编译期确定可用的等值条件），而集市首页正是访问量最大的列表。
 */
public interface GoodsRepository extends JpaRepository<Goods, Long> {

    Page<Goods> findByStatus(GoodsStatus status, Pageable pageable);

    Page<Goods> findByStatusAndCategory(GoodsStatus status, String category, Pageable pageable);

    /** 关键词只匹配标题：描述里的词命中率低且会让 LIKE %..% 扫描整表。 */
    Page<Goods> findByStatusAndTitleContaining(GoodsStatus status, String keyword, Pageable pageable);

    Page<Goods> findByStatusAndCategoryAndTitleContaining(GoodsStatus status, String category, String keyword,
                                                          Pageable pageable);

    /** 「我发布的」：不看状态，卖家要能看到自己已售出/已下架的商品。 */
    Page<Goods> findBySellerId(Long sellerId, Pageable pageable);
}
