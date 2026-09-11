package com.chengqu.huzhu.ad.config;

import com.chengqu.huzhu.ad.entity.AdBanner;
import com.chengqu.huzhu.ad.repository.AdBannerRepository;
import com.chengqu.huzhu.ad.support.RandomAdImages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdDataInitializer implements ApplicationRunner {

    private final AdBannerRepository adBannerRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (adBannerRepository.count() > 0) {
            log.info("[广告] 初始化跳过，已有数据 count={}", adBannerRepository.count());
            return;
        }

        save("邻里汇 · 邻里守望", "举手之劳，温暖整座城", "/aids", 1);
        save("社区活动 · 周末市集", "逛市集、交朋友、分享闲置好物", "/community", 2);
        save("互帮互助 · 一键求助", "搬运、代购、陪诊，邻居就在身边", "/aids/create", 3);
        save("暖心社区 · 共建家园", "发现身边的善意与精彩瞬间", "/community", 4);
        log.info("[广告] 初始化完成，写入示例广告 count=4，展示图由前端拉取");
    }

    private void save(String title, String subtitle, String linkUrl, int sort) {
        adBannerRepository.save(AdBanner.builder()
                .title(title)
                .subtitle(subtitle)
                .imageUrl(RandomAdImages.unique())
                .linkUrl(linkUrl)
                .sortOrder(sort)
                .enabled(true)
                .clickCount(0L)
                .build());
    }
}
