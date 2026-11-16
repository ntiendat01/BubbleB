package com.example.bubbleb.domain.privacy

/**
 * Local best-effort redaction (SECURITY_PRIVACY.md §5). Applied to the preview AND the outgoing
 * payload. It cannot guarantee complete PII removal, so user review stays mandatory. Generic
 * numbers (dates, amounts, times) are intentionally left alone.
 */
object Redactor {
    enum class Category(val token: String) {
        TOKEN("[REDACTED_TOKEN]"),
        CREDENTIAL("[REDACTED_CREDENTIAL]"),
        EMAIL("[REDACTED_EMAIL]"),
        CARD("[REDACTED_CARD]"),
        OTP("[REDACTED_OTP]"),
        PHONE("[REDACTED_PHONE]"),
    }

    data class Result(val text: String, val counts: Map<Category, Int>) {
        val total: Int get() = counts.values.sum()
    }

    private val TOKEN_PATTERNS = listOf(
        Regex("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{8,}"),
        Regex("\\bsk-[A-Za-z0-9_-]{16,}"),
        Regex("\\bAIza[0-9A-Za-z_-]{30,}"),
        Regex("\\bgh[pousr]_[A-Za-z0-9]{30,}"),
        Regex("\\bxox[abprs]-[A-Za-z0-9-]{10,}"),
        Regex("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}"),
    )
    private val CREDENTIAL = Regex(
        "(?iu)(password|passwd|pwd|passcode|mật khẩu|mat khau|api[ _-]?key|secret|token)(\\s*[:=]\\s*)(\\S+)",
    )
    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val CARD_CANDIDATE = Regex("(?<![\\d])(?:\\d[ -]?){12,18}\\d(?![\\d])")
    private const val OTP_ANCHOR =
        "otp|mã xác thực|mã xác nhận|mã xác minh|mã bảo mật|mã kích hoạt|mã đăng nhập|" +
            "verification code|security code|login code|one[- ]time code|passcode|code"
    private val OTP_AFTER_ANCHOR = Regex("(?iu)((?:$OTP_ANCHOR)[^\\d\\n]{0,30}?)(?<!\\d)(\\d{4,8})(?![\\d])")
    private val OTP_BEFORE_ANCHOR = Regex("(?iu)(?<![\\d.,])(\\d{4,8})(?![\\d.,])(\\s*(?:là mã|is your|là otp)\\b)")
    private val PHONE = Regex("(?<![\\d.,+])(?:\\+84|\\+\\d{1,3}|0)(?:[ .-]?\\d){8,10}(?![\\d.,])")

    fun redact(input: String): Result {
        val counts = mutableMapOf<Category, Int>()
        fun bump(c: Category) { counts[c] = (counts[c] ?: 0) + 1 }

        var text = input
        TOKEN_PATTERNS.forEach { re -> text = re.replace(text) { bump(Category.TOKEN); Category.TOKEN.token } }
        text = CREDENTIAL.replace(text) { m ->
            bump(Category.CREDENTIAL)
            m.groupValues[1] + m.groupValues[2] + Category.CREDENTIAL.token
        }
        text = EMAIL.replace(text) { bump(Category.EMAIL); Category.EMAIL.token }
        text = CARD_CANDIDATE.replace(text) { m ->
            val digits = m.value.filter(Char::isDigit)
            if (digits.length in 13..19 && luhnValid(digits)) {
                bump(Category.CARD); Category.CARD.token
            } else {
                m.value
            }
        }
        text = OTP_AFTER_ANCHOR.replace(text) { m -> bump(Category.OTP); m.groupValues[1] + Category.OTP.token }
        text = OTP_BEFORE_ANCHOR.replace(text) { m -> bump(Category.OTP); Category.OTP.token + m.groupValues[2] }
        text = PHONE.replace(text) { bump(Category.PHONE); Category.PHONE.token }
        return Result(text, counts.toMap())
    }

    /** Text left after removing redaction tokens, used to detect "mostly redacted" payloads. */
    fun stripTokens(text: String): String =
        Category.entries.fold(text) { acc, c -> acc.replace(c.token, "") }

    internal fun luhnValid(digits: String): Boolean {
        var sum = 0
        var double = false
        for (i in digits.length - 1 downTo 0) {
            var d = digits[i] - '0'
            if (double) { d *= 2; if (d > 9) d -= 9 }
            sum += d
            double = !double
        }
        return sum % 10 == 0
    }
}
