package com.example.mycalendar2026sar;

import org.junit.Test;
import static org.junit.Assert.*;

public class CurrencyFormatterTest {

    @Test
    public void testFormatRawAmount_ThousandsSeparatorPeriod() {
        assertEquals("1.000", CurrencyFormatter.formatRawAmount(1000));
        assertEquals("10.000", CurrencyFormatter.formatRawAmount(10000));
        assertEquals("1.000.000", CurrencyFormatter.formatRawAmount(1000000));
        assertEquals("250.000.000", CurrencyFormatter.formatRawAmount(250000000));
    }

    @Test
    public void testFormatAmount_WithCurrency() {
        assertEquals("10.000 USD", CurrencyFormatter.formatAmount(10000, "USD"));
        assertEquals("250.000.000 LBP", CurrencyFormatter.formatAmount(250000000, "LBP"));
    }

    @Test
    public void testParseAmount_PeriodThousandsSeparator() {
        assertEquals(1000.0, CurrencyFormatter.parseAmount("1.000"), 0.001);
        assertEquals(10000.0, CurrencyFormatter.parseAmount("10.000"), 0.001);
        assertEquals(1000000.0, CurrencyFormatter.parseAmount("1.000.000"), 0.001);
        assertEquals(250000000.0, CurrencyFormatter.parseAmount("250.000.000"), 0.001);
        assertEquals(10000.50, CurrencyFormatter.parseAmount("10.000,50"), 0.001);
    }
}
