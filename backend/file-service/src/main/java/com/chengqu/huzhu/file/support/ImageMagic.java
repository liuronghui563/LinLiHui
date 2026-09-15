package com.chengqu.huzhu.file.support;

import com.chengqu.huzhu.common.exception.BizException;

import java.util.Locale;
import java.util.Set;

/**
 * 用文件头判断真实类型，避免只看后缀被改成 .png 的可执行文件混进来。
 */
public final class ImageMagic {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXT = Set.of("png", "jpg", "jpeg", "webp");

    private ImageMagic() {
    }

    public record Detected(String extension, String contentType) {
    }

    public static Detected detect(byte[] header, String originalFilename) {
        if (header != null && header.length >= 12) {
            if (isPng(header)) {
                return new Detected("png", "image/png");
            }
            if (isJpeg(header)) {
                return new Detected("jpg", "image/jpeg");
            }
            if (isWebp(header)) {
                return new Detected("webp", "image/webp");
            }
        }
        String ext = extensionOf(originalFilename);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException("只支持 png / jpg / webp 图片");
        }
        throw new BizException("文件内容不是合法图片");
    }

    private static boolean isPng(byte[] header) {
        return header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47;
    }

    private static boolean isJpeg(byte[] header) {
        return header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF;
    }

    private static boolean isWebp(byte[] header) {
        return header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
