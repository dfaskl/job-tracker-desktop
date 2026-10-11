package com.jobtracker.careerflow.database;

import org.junit.jupiter.api.Test;
import jakarta.mail.Part;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class MailInboxServiceTest {
    @Test
    void skipsImagePartsAndAttachmentsWithoutLoadingTheirBodies() throws Exception {
        TrackingMimeBodyPart image = new TrackingMimeBodyPart();
        image.setHeader("Content-Type", "image/png");
        image.setDisposition(Part.INLINE);
        image.setFileName("logo.png");

        assertThat(MailInboxService.extractContent(image).body()).isEmpty();
        assertThat(MailInboxService.htmlPart(image)).isEmpty();
        assertThat(image.contentRead).isFalse();
    }

    private static final class TrackingMimeBodyPart extends MimeBodyPart {
        private boolean contentRead;

        @Override
        public Object getContent() throws IOException, MessagingException {
            contentRead = true;
            return new byte[1024];
        }
    }

    @Test
    void htmlMailTextKeepsParagraphsAndLineBreaksWithoutIncludingScripts() {
        String body = MailInboxService.htmlToText("""
            <html><body>
              <p>第一段<strong>重点</strong></p>
              <p>第二段<br>换行内容</p>
              <script>不要显示脚本</script>
            </body></html>
            """);

        assertThat(body).contains("第一段重点\n\n第二段\n换行内容");
        assertThat(body).doesNotContain("不要显示脚本");
    }

    @Test
    void formattedHtmlKeepsSafeEmailLayoutAndRemovesExecutableOrRemoteContent() {
        String body = MailInboxService.sanitizeEmailHtml("""
            <html><head><style>p{font-size:18px;color:#333}body{background-image:url(https://tracker.example/bg)}</style></head><body style="font-family:Arial;color:#333">
            <table style="border-collapse:collapse"><tr><td><p style="font-weight:bold;color:#333">岗位说明</p>
            <a href="https://example.com" onclick="alert(1)">查看岗位</a><img src="https://tracker.example/pixel" alt="公司标志"></td></tr></table>
            <script>alert(1)</script><iframe src="https://example.com"></iframe></body></html>
            """);

        assertThat(body).contains("<table", "岗位说明", "font-weight:bold", "font-size:18px", "font-family:Arial", "https://example.com", "target=\"_blank\"", "email-original-content", "overflow-x:hidden", "email-remote-image-placeholder");
        assertThat(body).doesNotContain("<script", "<iframe", "onclick", "tracker.example", "src=");
    }
}
