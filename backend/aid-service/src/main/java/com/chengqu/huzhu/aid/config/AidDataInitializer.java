package com.chengqu.huzhu.aid.config;

import com.chengqu.huzhu.aid.entity.AidRequest;
import com.chengqu.huzhu.aid.entity.AidStatus;
import com.chengqu.huzhu.aid.repository.AidRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 空表时写入示例求助。
 * <p>
 * publisherId 固定为 2L：与 auth-service 种子用户一致——第二位用户「邻里用户」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AidDataInitializer implements CommandLineRunner {

    /** auth 种子第二位用户 id（邻里用户） */
    private static final long SEED_PUBLISHER_ID = 2L;
    private static final String SEED_PUBLISHER_NAME = "邻里用户";

    private final AidRequestRepository aidRequestRepository;

    @Override
    public void run(String... args) {
        if (aidRequestRepository.count() > 0) {
            log.info("[求助] 表 u_r_aid_request 已有数据 count={}，跳过种子", aidRequestRepository.count());
            return;
        }
        AidRequest a1 = new AidRequest();
        a1.setTitle("帮忙搬一下快递");
        a1.setContent("楼下菜鸟驿站有两箱沉的，麻烦帮忙搬到 3 栋 502。");
        a1.setCategory("搬运");
        a1.setAddress("城南花园 3 栋");
        a1.setStatus(AidStatus.OPEN);
        a1.setPublisherId(SEED_PUBLISHER_ID);
        a1.setPublisherName(SEED_PUBLISHER_NAME);
        aidRequestRepository.save(a1);

        AidRequest a2 = new AidRequest();
        a2.setTitle("代购一袋米");
        a2.setContent("附近超市买一袋 10 斤大米，费用到付即可。");
        a2.setCategory("代购");
        a2.setAddress("阳光里小区东门");
        a2.setStatus(AidStatus.OPEN);
        a2.setPublisherId(SEED_PUBLISHER_ID);
        a2.setPublisherName(SEED_PUBLISHER_NAME);
        aidRequestRepository.save(a2);

        log.info("[求助] 已写入示例求助 2 条 -> 表 u_r_aid_request, publisherId={}, nickname={}",
                SEED_PUBLISHER_ID, SEED_PUBLISHER_NAME);
    }
}
