package com.chengqu.huzhu.community.entity;

import com.chengqu.huzhu.common.exception.BizException;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 帖子种类（10 种）。
 *
 * <p>取代原来只服务于校园频道的 {@code category} 字符串：那个字段既没有白名单校验
 * （客户端可以塞任意字符串），也无法表达「某个种类属于哪个模块」。
 *
 * <p>种类是业务可扩展的，所以数据库列用 VARCHAR 而不是 MySQL ENUM——
 * ENUM 每加一个值都要 ALTER TABLE，这个坑在 board / channel 上已经踩过。
 */
public enum PostKind {

    // ---------- 发现 ----------
    DAILY("分享日常", "记录此刻的生活片段", Set.of(PostModule.DISCOVER, PostModule.CAMPUS)),
    MARKET("闲置转让", "用不上的东西，转给需要的邻居", Set.of(PostModule.DISCOVER)),
    GROUP_BUY("拼单团购", "凑单更划算，和邻居一起买", Set.of(PostModule.DISCOVER)),
    EVENT("组局活动", "发起或参加一场邻里活动", Set.of(PostModule.DISCOVER)),
    ASK("打听问事", "问问邻居，也许正好有人知道", Set.of(PostModule.DISCOVER)),

    // ---------- 校园 ----------
    CHAT("唠嗑", "随便聊聊，找个说话的人", Set.of(PostModule.CAMPUS)),
    RANT("吐槽", "食堂、课程、宿舍，说出来轻松一点", Set.of(PostModule.CAMPUS)),
    CONFESS("表白", "把想说的话放进这里", Set.of(PostModule.CAMPUS)),
    PARTNER("找搭子", "自习、运动、拼车，找个同行的", Set.of(PostModule.CAMPUS)),
    LOST_FOUND("失物招领", "丢了或捡到东西，都发这里", Set.of(PostModule.CAMPUS));

    private final String label;
    private final String hint;
    private final Set<PostModule> modules;

    PostKind(String label, String hint, Set<PostModule> modules) {
        this.label = label;
        this.hint = hint;
        this.modules = modules;
    }

    public String getLabel() {
        return label;
    }

    public String getHint() {
        return hint;
    }

    public boolean allows(PostModule module) {
        return modules.contains(module);
    }

    /** 某模块下可用的种类，按枚举声明顺序返回，供前端渲染选择器。 */
    public static List<PostKind> of(PostModule module) {
        return Arrays.stream(values()).filter(kind -> kind.allows(module)).toList();
    }

    public static PostKind defaultOf(PostModule module) {
        return of(module).get(0);
    }

    /** 写操作严格解析：非法种类、或种类不属于该模块，都直接拒绝。 */
    public static PostKind require(String code, PostModule module) {
        if (code == null || code.isBlank()) {
            return defaultOf(module);
        }
        PostKind kind = parseOrNull(code);
        if (kind == null) {
            throw new BizException("帖子种类无效");
        }
        if (!kind.allows(module)) {
            throw new BizException("「" + kind.label + "」不属于当前模块");
        }
        return kind;
    }

    /** 筛选条件解析：非法值返回 null，表示不按种类过滤。 */
    public static PostKind parseOrNull(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(kind -> kind.name().equals(normalized))
                .findFirst()
                .orElse(null);
    }
}
