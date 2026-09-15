package com.chengqu.huzhu.community.config;

import com.chengqu.huzhu.community.entity.Goods;
import com.chengqu.huzhu.community.entity.GoodsStatus;
import com.chengqu.huzhu.community.repository.GoodsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 表为空时写入几件示例闲置。
 *
 * <p>与 {@link CommunityDataInitializer} 同样的取舍：示例数据放在 Java 里而不是
 * 迁移脚本里。迁移脚本只描述结构，任何环境跑完 Flyway 得到的是同一套表；
 * 演示数据属于「本地想看到点东西」，用 IF NOT EXISTS 之外的判空逻辑更可控。
 *
 * <p>卖家 id 与 auth-service 种子一致：1=系统管理员，2=邻里用户。
 * 示例商品不带配图：图片必须先经 file-service 上传，硬编码任何地址都会在
 * 换环境后变成 404 的空白卡（picsum 那次就是这么翻车的）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataInitializer implements CommandLineRunner {

    private static final long SEED_ADMIN_ID = 1L;
    private static final String SEED_ADMIN_NAME = "系统管理员";
    private static final long SEED_USER_ID = 2L;
    private static final String SEED_USER_NAME = "邻里用户";

    private final GoodsRepository goodsRepository;

    @Override
    public void run(String... args) {
        if (goodsRepository.count() > 0) {
            log.info("[集市] 表 u_r_goods 已有数据 count={}，跳过种子", goodsRepository.count());
            return;
        }

        saveGoods("搬家出九成新折叠桌", "城南花园自提，桌面无划痕，可折叠收纳。", "58.00", "129.00", "家具",
                SEED_USER_ID, SEED_USER_NAME);
        saveGoods("闲置儿童绘本一套 12 本", "适合 3-6 岁，无涂画，整套一起出。", "30.00", "96.00", "母婴",
                SEED_USER_ID, SEED_USER_NAME);
        saveGoods("小区活动室借用的小推车", "邻里共用的手推车，谁需要谁拿走，用完请放回活动室。", "0.00", null, "其他",
                SEED_ADMIN_ID, SEED_ADMIN_NAME);

        log.info("[集市] 已写入示例闲置 3 件 -> 表 u_r_goods");
    }

    private void saveGoods(String title, String description, String price, String originalPrice,
                           String category, long sellerId, String sellerName) {
        Goods goods = new Goods();
        goods.setTitle(title);
        goods.setDescription(description);
        goods.setPrice(new BigDecimal(price));
        goods.setOriginalPrice(originalPrice == null ? null : new BigDecimal(originalPrice));
        goods.setCategory(category);
        goods.setSellerId(sellerId);
        goods.setSellerName(sellerName);
        goods.setStatus(GoodsStatus.ON_SALE);
        goods.setViewCount(0);
        goodsRepository.save(goods);
    }
}
