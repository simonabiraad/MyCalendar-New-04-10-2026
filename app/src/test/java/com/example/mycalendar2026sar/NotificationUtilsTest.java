package com.example.mycalendar2026sar;

import org.junit.Test;
import java.util.Calendar;
import static org.junit.Assert.*;

public class NotificationUtilsTest {

    @Test
    public void testGetEventCalendar_12HourFormat() {
        NotificationEvent event = new NotificationEvent(
                1, "Test Event", "Notes", "15/10/2026", "02:30 PM", "03:30 PM",
                "Medium", "Pending", "None", "5 minutes before", "Office",
                "Work", "Blue", false, "[]", "", "[]"
        );

        Calendar cal = NotificationUtils.getEventCalendar(event);
        assertNotNull(cal);
        assertEquals(2026, cal.get(Calendar.YEAR));
        assertEquals(Calendar.OCTOBER, cal.get(Calendar.MONTH));
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH));
        assertEquals(14, cal.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, cal.get(Calendar.MINUTE));
    }

    @Test
    public void testGetEventCalendar_24HourFormat() {
        NotificationEvent event = new NotificationEvent(
                2, "24h Event", "Notes", "20/11/2026", "18:45", "19:45",
                "High", "Pending", "Every week", "10 minutes before", "Home",
                "Personal", "Red", false, "[]", "", "[]"
        );

        Calendar cal = NotificationUtils.getEventCalendar(event);
        assertNotNull(cal);
        assertEquals(2026, cal.get(Calendar.YEAR));
        assertEquals(Calendar.NOVEMBER, cal.get(Calendar.MONTH));
        assertEquals(20, cal.get(Calendar.DAY_OF_MONTH));
        assertEquals(18, cal.get(Calendar.HOUR_OF_DAY));
        assertEquals(45, cal.get(Calendar.MINUTE));
    }

    @Test
    public void testGetEventCalendar_AllDay() {
        NotificationEvent event = new NotificationEvent(
                3, "All Day Event", "Notes", "01/12/2026", "", "",
                "Low", "Pending", "None", "1 day before", "Park",
                "Other", "Green", true, "[]", "", "[]"
        );

        Calendar cal = NotificationUtils.getEventCalendar(event);
        assertNotNull(cal);
        assertEquals(2026, cal.get(Calendar.YEAR));
        assertEquals(Calendar.DECEMBER, cal.get(Calendar.MONTH));
        assertEquals(1, cal.get(Calendar.DAY_OF_MONTH));
        assertEquals(9, cal.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, cal.get(Calendar.MINUTE));
    }

    @Test
    public void testIsReminderDisabled() {
        assertTrue(NotificationUtils.isReminderDisabled(null));
        assertTrue(NotificationUtils.isReminderDisabled(""));
        assertTrue(NotificationUtils.isReminderDisabled("None"));
        assertTrue(NotificationUtils.isReminderDisabled("none"));
        assertTrue(NotificationUtils.isReminderDisabled("Disabled"));

        assertFalse(NotificationUtils.isReminderDisabled("At event time"));
        assertFalse(NotificationUtils.isReminderDisabled("5 minutes before"));
        assertFalse(NotificationUtils.isReminderDisabled("1 hour before"));
        assertFalse(NotificationUtils.isReminderDisabled("Custom: 09:30"));
    }

    @Test
    public void testIsRepeating() {
        assertFalse(NotificationUtils.isRepeating(null));
        assertFalse(NotificationUtils.isRepeating(new NotificationEvent(1, "", "", "10/10/2026", "", "", "", "", "None", "", "", "", "", false, "", "", "")));
        assertFalse(NotificationUtils.isRepeating(new NotificationEvent(1, "", "", "10/10/2026", "", "", "", "", "Does not repeat", "", "", "", "", false, "", "", "")));

        assertTrue(NotificationUtils.isRepeating(new NotificationEvent(1, "", "", "10/10/2026", "", "", "", "", "Every day", "", "", "", "", false, "", "", "")));
        assertTrue(NotificationUtils.isRepeating(new NotificationEvent(1, "", "", "10/10/2026", "", "", "", "", "Every week", "", "", "", "", false, "", "", "")));
        assertTrue(NotificationUtils.isRepeating(new NotificationEvent(1, "", "", "10/10/2026", "", "", "", "", "Every month", "", "", "", "", false, "", "", "")));
        assertTrue(NotificationUtils.isRepeating(new NotificationEvent(1, "", "", "10/10/2026", "", "", "", "", "Every year", "", "", "", "", false, "", "", "")));
        assertTrue(NotificationUtils.isRepeating(new NotificationEvent(1, "", "", "10/10/2026", "", "", "", "", "Custom: 15/10/2026, 20/10/2026", "", "", "", "", false, "", "", "")));
    }
}
