package com.chengqu.huzhu.common.file;

import com.chengqu.huzhu.common.exception.BizException;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 本站对象地址约定：业务表只存相对路径 {@code /api/file/objects/...}，
 * 不接受外链，避免 picsum 等第三方图床再次混入。
 */
public final class LocalFileUrls {

    public static final String PREFIX = "/api/file/objects/";
    public static final int MAX_IMAGES = 9;

    private LocalFileUrls() {
    }

    public static boolean isLocal(String url) {
        return StringUtils.hasText(url) && url.trim().startsWith(PREFIX) && !url.contains("..");
    }

    /** 空或非本站地址一律视为未设置（旧 picsum 数据也会被清掉）。 */
    public static String sanitizeOptional(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        String trimmed = url.trim();
        return isLocal(trimmed) ? trimmed : null;
    }

    public static String requireLocal(String url, String message) {
        String sanitized = sanitizeOptional(url);
        if (sanitized == null) {
            throw new BizException(message);
        }
        return sanitized;
    }

    public static List<String> normalizeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String raw : images) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            result.add(requireLocal(raw.trim(), "配图必须先上传到本站"));
            if (result.size() > MAX_IMAGES) {
                throw new BizException("最多上传 " + MAX_IMAGES + " 张图片");
            }
        }
        return List.copyOf(result);
    }
}
