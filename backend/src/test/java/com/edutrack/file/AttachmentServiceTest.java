package com.edutrack.file;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AttachmentServiceTest {
    @Test
    void detectsSupportedFileSignaturesAndRejectsExecutable() {
        assertThat(AttachmentService.detectMime(new byte[]{(byte)255,(byte)216,(byte)255,1})).isEqualTo("image/jpeg");
        assertThat(AttachmentService.detectMime(new byte[]{(byte)137,80,78,71,13,10,26,10})).isEqualTo("image/png");
        assertThat(AttachmentService.detectMime("RIFFxxxxWEBP".getBytes())).isEqualTo("image/webp");
        assertThat(AttachmentService.detectMime("%PDF-1.7".getBytes())).isEqualTo("application/pdf");
        assertThat(AttachmentService.detectMime(new byte[]{'M','Z',0,1})).isNull();
    }
}
