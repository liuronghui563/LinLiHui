package com.chengqu.huzhu.file.support;

import com.chengqu.huzhu.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageMagicTest {

    @Test
    void detect_pngJpegWebp() {
        byte[] png = new byte[16];
        png[0] = (byte) 0x89;
        png[1] = 0x50;
        png[2] = 0x4E;
        png[3] = 0x47;
        assertEquals("png", ImageMagic.detect(png, "x.bin").extension());

        byte[] jpeg = new byte[16];
        jpeg[0] = (byte) 0xFF;
        jpeg[1] = (byte) 0xD8;
        jpeg[2] = (byte) 0xFF;
        assertEquals("jpg", ImageMagic.detect(jpeg, "x.bin").extension());

        byte[] webp = new byte[16];
        webp[0] = 'R';
        webp[1] = 'I';
        webp[2] = 'F';
        webp[3] = 'F';
        webp[8] = 'W';
        webp[9] = 'E';
        webp[10] = 'B';
        webp[11] = 'P';
        assertEquals("webp", ImageMagic.detect(webp, "x.bin").extension());
    }

    @Test
    void detect_rejectsUnknown() {
        assertThrows(BizException.class, () -> ImageMagic.detect(new byte[16], "note.txt"));
    }
}
