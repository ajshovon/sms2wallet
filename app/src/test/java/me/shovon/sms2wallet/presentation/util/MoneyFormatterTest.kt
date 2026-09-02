package me.shovon.sms2wallet.presentation.util

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies [MoneyFormatter] produces Bangladeshi (lakh/crore) digit grouping, which differs
 * from the "every three digits" grouping most JVM locales use by default.
 */
class MoneyFormatterTest {

    @Test
    fun `amounts under 1000 have no grouping`() {
        assertEquals("৳0.00", MoneyFormatter.formatBdt(BigDecimal.ZERO))
        assertEquals("৳5.00", MoneyFormatter.formatBdt(BigDecimal("5")))
        assertEquals("৳650.00", MoneyFormatter.formatBdt(BigDecimal("650")))
    }

    @Test
    fun `four digit amount groups once`() {
        assertEquals("৳1,000.00", MoneyFormatter.formatBdt(BigDecimal("1000")))
        assertEquals("৳9,999.00", MoneyFormatter.formatBdt(BigDecimal("9999")))
    }

    @Test
    fun `lakh grouping matches Bangladeshi convention`() {
        assertEquals("৳1,08,650.00", MoneyFormatter.formatBdt(BigDecimal("108650")))
        assertEquals("৳12,34,567.00", MoneyFormatter.formatBdt(BigDecimal("1234567")))
    }

    @Test
    fun `crore grouping matches Bangladeshi convention`() {
        assertEquals("৳1,23,45,678.00", MoneyFormatter.formatBdt(BigDecimal("12345678")))
        assertEquals("৳12,34,56,789.00", MoneyFormatter.formatBdt(BigDecimal("123456789")))
    }

    @Test
    fun `negative amounts keep the minus sign before the symbol`() {
        assertEquals("-৳1,08,650.00", MoneyFormatter.formatBdt(BigDecimal("-108650")))
    }

    @Test
    fun `fractional amounts round to two decimal places`() {
        assertEquals("৳1,08,650.13", MoneyFormatter.formatBdt(BigDecimal("108650.125")))
        assertEquals("৳1,08,650.10", MoneyFormatter.formatBdt(BigDecimal("108650.1")))
    }

    @Test
    fun `double and long overloads match the BigDecimal result`() {
        assertEquals("৳1,08,650.00", MoneyFormatter.formatBdt(108650.0))
        assertEquals("৳1,08,650.00", MoneyFormatter.formatBdt(108650L))
    }

    @Test
    fun `groupIndianStyle groups digit strings correctly`() {
        assertEquals("1", MoneyFormatter.groupIndianStyle("1"))
        assertEquals("123", MoneyFormatter.groupIndianStyle("123"))
        assertEquals("1,234", MoneyFormatter.groupIndianStyle("1234"))
        assertEquals("12,34,567", MoneyFormatter.groupIndianStyle("1234567"))
        assertEquals("1,23,45,678", MoneyFormatter.groupIndianStyle("12345678"))
    }
}
