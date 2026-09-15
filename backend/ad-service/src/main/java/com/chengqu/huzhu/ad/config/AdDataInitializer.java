package com.chengqu.huzhu.ad.config;

import com.chengqu.huzhu.ad.entity.AdBanner;
import com.chengqu.huzhu.ad.repository.AdBannerRepository;
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

        save("邻里汇", "举手之劳，温暖整座城", "/aids", 1);
        save("社区活动", "逛市集、交朋友、分享闲置好物", "/community", 2);
        save("互帮互助", "搬运、代购、陪诊", "/aids/create", 3);
        save("暖心社区", "发现身边的善意", "/community", 4);
        log.info("[广告] 初始化完成，写入示例广告 count=4（无外链配图，由运营上传本站对象）");
    }

    private void save(String title, String subtitle, String linkUrl, int sort) {
        adBannerRepository.save(AdBanner.builder()
                .title(title)
                .subtitle(subtitle)
                .imageUrl(null)
                .linkUrl(linkUrl)
                .sortOrder(sort)
                .enabled(true)
                .clickCount(0L)
                .build());
    }
}
