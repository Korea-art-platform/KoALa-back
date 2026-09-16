package com.koala.koalaback.global.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("주문자 찾기용 해시")
class PiiIndexTest {
    private static final String KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private final PiiIndex index = new PiiIndex(KEY);

    @Test
    @DisplayName("같은 값은 같은 해시가 된다 — 그래야 찾을 수 있다")
    void stable() {
        assertThat(index.ofEmail("buyer@koala.test"))
                .isEqualTo(index.ofEmail("buyer@koala.test"));
    }

    @Test
    @DisplayName("이메일은 대소문자와 앞뒤 공백을 무시한다")
    void emailNormalized() {
        assertThat(index.ofEmail("  Buyer@Koala.TEST "))
                .isEqualTo(index.ofEmail("buyer@koala.test"));
    }

    @Test
    @DisplayName("전화번호는 표기 방식이 달라도 같은 해시가 된다")
    void phoneNormalized() {
        String hyphen = index.ofPhone("010-1234-5678");
        assertThat(index.ofPhone("01012345678")).isEqualTo(hyphen);
        assertThat(index.ofPhone("+82 10 1234 5678")).isNotNull();
    }

    @Test
    @DisplayName("이메일과 전화번호는 같은 문자열이어도 다른 해시가 된다")
    void separatedByPurpose() {
        assertThat(index.ofEmail("01012345678")).isNotEqualTo(index.ofPhone("01012345678"));
    }

    @Test
    @DisplayName("해시에서 원래 값을 알아볼 수 없다")
    void notReversible() {
        String hash = index.ofPhone("010-1234-5678");

        assertThat(hash).doesNotContain("1234", "5678").hasSize(43);
    }

    @Test
    @DisplayName("뒷자리 4자리를 뽑는다")
    void last4() {
        assertThat(index.last4Of("010-1234-5678")).isEqualTo("5678");
        assertThat(index.last4Of("123")).isNull();
        assertThat(index.last4Of(null)).isNull();
    }

    @Test
    @DisplayName("키가 없으면 해시를 만들지 않는다 — 로컬에서 조용히 동작한다")
    void withoutKey() {
        PiiIndex noKey = new PiiIndex("");

        assertThat(noKey.isEnabled()).isFalse();
        assertThat(noKey.ofEmail("buyer@koala.test")).isNull();
        assertThat(noKey.ofPhone("01012345678")).isNull();
    }

    @Test
    @DisplayName("빈 값에는 해시를 만들지 않는다")
    void blankInput() {
        assertThat(index.ofEmail("   ")).isNull();
        assertThat(index.ofPhone("")).isNull();
        assertThat(index.ofPhone(null)).isNull();
    }
}
