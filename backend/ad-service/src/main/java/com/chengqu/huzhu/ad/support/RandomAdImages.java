package com.chengqu.huzhu.ad.support;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 广告图使用 Picsum 随机图，每次拉取/初始化都会换一张。
 */
public final class RandomAdImages {

    private RandomAdImages() {
    }

    public static String next() {
        int seed = ThreadLocalRandom.current().nextInt(1, 10_000_000);
        return "https://picsum.photos/seed/" + seed + "/1600/720";
    }

    public static String unique() {
        return "https://picsum.photos/seed/" + UUID.randomUUID().toString().replace("-", "") + "/1600/720";
    }
}
