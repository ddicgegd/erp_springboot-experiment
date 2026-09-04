package com.ddicg.erp.core.common.util;

import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VietnameseNameParserTest {

    @Test
    @DisplayName("Parse standard 3-word Vietnamese name: Họ + Đệm + Tên")
    void testParse_ThreeWordsName() {
        VietnameseNameParser.ParsedName result = VietnameseNameParser.parse("Nguyễn Văn An");

        assertThat(result.firstname()).isEqualTo("An");
        assertThat(result.lastname()).isEqualTo("Nguyễn Văn");
        assertThat(result.fullName()).isEqualTo("Nguyễn Văn An");
    }

    @Test
    @DisplayName("Parse 4-word Vietnamese name: Họ + 2 Đệm + Tên")
    void testParse_FourWordsName() {
        VietnameseNameParser.ParsedName result = VietnameseNameParser.parse("Trần Thị Thu Hà");

        assertThat(result.firstname()).isEqualTo("Hà");
        assertThat(result.lastname()).isEqualTo("Trần Thị Thu");
        assertThat(result.fullName()).isEqualTo("Trần Thị Thu Hà");
    }

    @Test
    @DisplayName("Parse 2-word Vietnamese name: Họ + Tên")
    void testParse_TwoWordsName() {
        VietnameseNameParser.ParsedName result = VietnameseNameParser.parse("Lê Lợi");

        assertThat(result.firstname()).isEqualTo("Lợi");
        assertThat(result.lastname()).isEqualTo("Lê");
        assertThat(result.fullName()).isEqualTo("Lê Lợi");
    }

    @Test
    @DisplayName("Parse single-word name: fallback cả họ và tên vào cùng 1 từ")
    void testParse_SingleWordName() {
        VietnameseNameParser.ParsedName result = VietnameseNameParser.parse("Định");

        assertThat(result.firstname()).isEqualTo("Định");
        assertThat(result.lastname()).isEqualTo("Định");
        assertThat(result.fullName()).isEqualTo("Định");
    }

    @Test
    @DisplayName("Normalize multiple whitespaces and trim leading/trailing spaces")
    void testParse_MessyWhitespaces() {
        VietnameseNameParser.ParsedName result = VietnameseNameParser.parse("   Ngô    Ngọc     Định   ");

        assertThat(result.firstname()).isEqualTo("Định");
        assertThat(result.lastname()).isEqualTo("Ngô Ngọc");
        assertThat(result.fullName()).isEqualTo("Ngô Ngọc Định");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Throw USER_PROFILE_INCOMPLETE on null or blank name (No fake data allowed)")
    void testParse_NullOrBlank_ThrowsException(String input) {
        assertThatThrownBy(() -> VietnameseNameParser.parse(input))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_PROFILE_INCOMPLETE);
    }
}
