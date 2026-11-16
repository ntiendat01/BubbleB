package com.example.bubbleb.privacy

import com.example.bubbleb.domain.privacy.Redactor
import com.example.bubbleb.domain.privacy.Redactor.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactionTest {
    private fun r(s: String) = Redactor.redact(s)

    @Test fun email() {
        val out = r("Liên hệ lan.nguyen+work@example.com.vn nhé")
        assertEquals("Liên hệ [REDACTED_EMAIL] nhé", out.text)
        assertEquals(1, out.counts[Category.EMAIL])
    }

    @Test fun vietnamesePhones() {
        assertEquals("Gọi [REDACTED_PHONE] nhé", r("Gọi 0912 345 678 nhé").text)
        assertEquals("SĐT [REDACTED_PHONE]", r("SĐT +84 912.345.678").text)
        assertEquals("[REDACTED_PHONE]", r("0283-123-4567").text)
    }

    @Test fun luhnValidCardOnly() {
        assertEquals("Thẻ [REDACTED_CARD]", r("Thẻ 4111 1111 1111 1111").text)
        val invalid = "Mã đơn 4111 1111 1111 1112"
        assertFalse(r(invalid).text.contains("REDACTED_CARD"))
    }

    @Test fun otpVietnameseAndEnglish() {
        assertTrue(r("Mã xác thực của bạn: 482913").text.contains("[REDACTED_OTP]"))
        assertTrue(r("Your verification code is 1234").text.contains("[REDACTED_OTP]"))
        assertEquals("[REDACTED_OTP] là mã OTP của bạn", r("482913 là mã OTP của bạn").text)
        assertFalse(r("Mã xác thực của bạn: 482913").text.contains("482913"))
    }

    @Test fun tokensAndCredentials() {
        // Fake values assembled at runtime so the repository secret audit never sees a key literal.
        val fakeKey = "sk" + "-" + "x".repeat(20)
        val bearer = "Bear" + "er abcDEF123456789xyz"
        val pwd = "pass" + "word=hunter2"
        val out = r("Authorization: $bearer và key $fakeKey và $pwd")
        assertFalse(out.text.contains("abcDEF123456789xyz"))
        assertFalse(out.text.contains(fakeKey))
        assertFalse(out.text.contains("hunter2"))
        assertTrue(out.text.contains("password=[REDACTED_CREDENTIAL]"))
        assertEquals(2, out.counts[Category.TOKEN])
    }

    @Test fun jwtAndGoogleKey() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U"
        assertEquals("[REDACTED_TOKEN]", r(jwt).text)
        assertEquals("[REDACTED_TOKEN]", r("AIza" + "S".repeat(35)).text)
    }

    @Test fun mãKhẩuVietnamese() {
        assertEquals("mật khẩu: [REDACTED_CREDENTIAL]", r("mật khẩu: Abc@12345").text)
    }

    @Test fun falsePositives_datesAmountsTimesRooms() {
        val samples = listOf(
            "Hẹn ngày 16/11/2026 lúc 19:30",
            "Tổng tiền 1.250.000 đ, giảm 12,5%",
            "Phòng 1205, tầng 12, năm 2026",
            "Chuyến bay VN 245 cất cánh 07:45",
            "Version 0.2.0 build 20261116",
        )
        samples.forEach { s ->
            val out = r(s)
            assertEquals(s, out.text)
            assertEquals(0, out.total)
        }
    }

    @Test fun nothingSensitive_unchanged() = assertEquals("Xin chào bạn", r("Xin chào bạn").text)

    @Test fun luhnHelper() {
        assertTrue(Redactor.luhnValid("4111111111111111"))
        assertFalse(Redactor.luhnValid("4111111111111112"))
    }

    @Test fun stripTokensLeavesOnlyRealText() =
        assertEquals("a  b", Redactor.stripTokens("a [REDACTED_EMAIL] b"))
}
