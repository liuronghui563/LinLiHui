package com.chengqu.huzhu.common.file;

import com.chengqu.huzhu.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFileUrlsTest {

    @Test
    void sanitizeOptional_keepsLocalAndDropsPicsum() {
        assertEquals("/api/file/objects/avatar/1/20260913/a.png",
                LocalFileUrls.sanitizeOptional("/api/file/objects/avatar/1/20260913/a.png"));
        assertNull(LocalFileUrls.sanitizeOptional("https://picsum.photos/seed/user-1/200/200"));
        assertNull(LocalFileUrls.sanitizeOptional(""));
    }

    @Test
    void normalizeImages_rejectsExternalAndCapsCount() {
        List<String> ok = LocalFileUrls.normalizeImages(List.of("/api/file/objects/post/1/20260913/a.jpg"));
        assertEquals(1, ok.size());
        assertThrows(BizException.class, () -> LocalFileUrls.normalizeImages(List.of("https://evil.example/a.png")));
        List<String> tooMany = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> "/api/file/objects/post/1/20260913/" + i + ".png")
                .toList();
        assertThrows(BizException.class, () -> LocalFileUrls.normalizeImages(tooMany));
    }

    @Test
    void isLocal_rejectsPathTraversal() {
        assertTrue(LocalFileUrls.isLocal("/api/file/objects/post/1/20260913/a.png"));
        assertThrows(BizException.class, () -> LocalFileUrls.requireLocal("/api/file/objects/../secret", "bad"));
    }
}
