package com.jobtracker.careerflow.database;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MailInboxServiceTest {
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
}
