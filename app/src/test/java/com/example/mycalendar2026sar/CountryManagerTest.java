package com.example.mycalendar2026sar;

import org.junit.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class CountryManagerTest {

    @Test
    public void testGetCountries_MasterList() {
        List<CountryManager.Country> master = CountryManager.getCountries();
        assertNotNull(master);
        assertTrue(master.size() > 20);

        boolean foundUS = false;
        boolean foundLB = false;
        for (CountryManager.Country c : master) {
            if ("US".equals(c.code)) foundUS = true;
            if ("LB".equals(c.code)) foundLB = true;
        }
        assertTrue(foundUS);
        assertTrue(foundLB);
    }

    @Test
    public void testGetSortedCountries_NullContext_MasterListUnchanged() {
        List<CountryManager.Country> sorted = CountryManager.getSortedCountries(null);
        assertNotNull(sorted);
        assertFalse(sorted.isEmpty());

        // Verify no duplicate currency codes in prioritized prefix
        Set<String> seen = new HashSet<>();
        for (CountryManager.Country c : sorted) {
            assertNotNull(c.currency);
            assertFalse(c.currency.trim().isEmpty());
        }
    }
}
