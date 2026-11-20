package dev.aprax.tmaii.backend

import dev.aprax.tmaii.backend.services.OtpParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OtpParserTest {

    @Test
    fun extractsCodeFromSubjectStyleText() {
        val code = OtpParser.extract("Your verification code is 483920. It expires in 10 minutes.")
        assertEquals("483920", code)
    }

    @Test
    fun extractsCodeAfterLabel() {
        val code = OtpParser.extract("OTP: 552193")
        assertEquals("552193", code)
    }

    @Test
    fun extractsCodeWithCodeWordBeforeDigits() {
        val code = OtpParser.extract("Use code 918273 to confirm your sign in")
        assertEquals("918273", code)
    }

    @Test
    fun doesNotExtractAmounts() {
        val code = OtpParser.extract("Your total amount is 123456 dollars for order 998877")
        assertNull(code)
    }

    @Test
    fun doesNotExtractFromEmpty() {
        assertNull(OtpParser.extract(""))
        assertNull(OtpParser.extract("   "))
    }

    @Test
    fun stripsHtmlBeforeParsing() {
        val html = "<html><body><p>Your code is <b>771204</b></p></body></html>"
        assertEquals("771204", OtpParser.extract(html))
    }

    @Test
    fun doesNotTreatShortOrAllZeroAsOtp() {
        assertNull(OtpParser.extract("Your code is 0000"))
        assertNull(OtpParser.extract("Your code is 12"))
    }

    @Test
    fun findsVerificationLinks() {
        val text = "Confirm here: https://example.com/verify?token=abc123 or visit https://app.example.com/confirm/xyz"
        val links = OtpParser.findVerificationLinks(text)
        assertTrue(links.any { it.contains("token=abc123") })
        assertTrue(links.any { it.contains("confirm/xyz") })
    }

    @Test
    fun sanitizeHtmlRemovesScripts() {
        val dirty = """
            <html><head><script>alert(1)</script></head>
            <body onclick="steal()">
            <p>Hello</p>
            <a href="javascript:evil()">bad</a>
            </body></html>
        """.trimIndent()
        val clean = OtpParser.sanitizeHtml(dirty)
        assertTrue(!clean.contains("<script"))
        assertTrue(!clean.contains("onclick"))
        assertTrue(!clean.contains("javascript:"))
        assertTrue(clean.contains("Hello"))
    }

    @Test
    fun extractAllReturnsDistinctCodes() {
        val codes = OtpParser.extractAll("Code 111222 and code 333444")
        assertTrue(codes.contains("111222"))
        assertTrue(codes.contains("333444"))
    }
}
