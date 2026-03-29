package com.logilink.emailanalyzer.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailContentNormalizerTest {

    @Test
    void collapsesManyBlankLinesToAtMostThree() {
        String in = "Hello\n\n\n\n\n\n\nWorld";
        assertThat(EmailContentNormalizer.normalize(in)).isEqualTo("Hello\n\n\n\nWorld");
    }

    @Test
    void trimsLineWhitespaceAndOuterBlanks() {
        String in = "\n  hi  \n\n\n\n  there  \n";
        assertThat(EmailContentNormalizer.normalize(in)).isEqualTo("hi\n\n\nthere");
    }

    @Test
    void normalizesCrLf() {
        String in = "a\r\n\r\n\r\n\r\n\r\nb";
        assertThat(EmailContentNormalizer.normalize(in)).isEqualTo("a\n\n\nb");
    }

    @Test
    void nullSafe() {
        assertThat(EmailContentNormalizer.normalize(null)).isNull();
    }
}
