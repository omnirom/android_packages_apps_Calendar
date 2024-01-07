package com.android.calendar.event;

import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;

public class EventQueries {

    public static final String[] EVENT_PROJECTION = new String[] {
            Events._ID, // 0
            Events.TITLE, // 1
            Events.DESCRIPTION, // 2
            Events.EVENT_LOCATION, // 3
            Events.ALL_DAY, // 4
            Events.HAS_ALARM, // 5
            Events.CALENDAR_ID, // 6
            Events.DTSTART, // 7
            Events.DTEND, // 8
            Events.DURATION, // 9
            Events.EVENT_TIMEZONE, // 10
            Events.RRULE, // 11
            Events._SYNC_ID, // 12
            Events.AVAILABILITY, // 13
            Events.ACCESS_LEVEL, // 14
            Events.OWNER_ACCOUNT, // 15
            Events.HAS_ATTENDEE_DATA, // 16
            Events.ORIGINAL_SYNC_ID, // 17
            Events.ORGANIZER, // 18
            Events.GUESTS_CAN_MODIFY, // 19
            Events.ORIGINAL_ID, // 20
            Events.STATUS, // 21
            Events.CALENDAR_COLOR, // 22
            Events.EVENT_COLOR, // 23
            Events.EVENT_COLOR_KEY, // 24
            Events.CUSTOM_APP_PACKAGE,   // 25
            Events.CUSTOM_APP_URI,       // 26
        };
    public static final int EVENT_INDEX_ID = 0;
    public static final int EVENT_INDEX_TITLE = 1;
    public static final int EVENT_INDEX_DESCRIPTION = 2;
    public static final int EVENT_INDEX_EVENT_LOCATION = 3;
    public static final int EVENT_INDEX_ALL_DAY = 4;
    public static final int EVENT_INDEX_HAS_ALARM = 5;
    public static final int EVENT_INDEX_CALENDAR_ID = 6;
    public static final int EVENT_INDEX_DTSTART = 7;
    public static final int EVENT_INDEX_DTEND = 8;
    public static final int EVENT_INDEX_DURATION = 9;
    public static final int EVENT_INDEX_TIMEZONE = 10;
    public static final int EVENT_INDEX_RRULE = 11;
    public static final int EVENT_INDEX_SYNC_ID = 12;
    public static final int EVENT_INDEX_AVAILABILITY = 13;
    public static final int EVENT_INDEX_ACCESS_LEVEL = 14;
    public static final int EVENT_INDEX_OWNER_ACCOUNT = 15;
    public static final int EVENT_INDEX_HAS_ATTENDEE_DATA = 16;
    public static final int EVENT_INDEX_ORIGINAL_SYNC_ID = 17;
    public static final int EVENT_INDEX_ORGANIZER = 18;
    public static final int EVENT_INDEX_GUESTS_CAN_MODIFY = 19;
    public static final int EVENT_INDEX_ORIGINAL_ID = 20;
    public static final int EVENT_INDEX_EVENT_STATUS = 21;
    public static final int EVENT_INDEX_CALENDAR_COLOR = 22;
    public static final int EVENT_INDEX_EVENT_COLOR = 23;
    public static final int EVENT_INDEX_EVENT_COLOR_KEY = 24;
    public static final int EVENT_INDEX_CUSTOM_APP_PACKAGE = 25;
    public static final int EVENT_INDEX_CUSTOM_APP_URI = 26;

    public static final String[] REMINDERS_PROJECTION = new String[] {
        Reminders._ID,                      // 0
        Reminders.MINUTES,            // 1
        Reminders.METHOD           // 2
    };
    public static final int REMINDERS_INDEX_ID = 0;
    public static final int REMINDERS_INDEX_MINUTES = 1;
    public static final int REMINDERS_INDEX_METHOD = 2;

    public static final String REMINDERS_WHERE = Reminders.EVENT_ID + "=?";
    
    public static final String[] ATTENDEES_PROJECTION = new String[] {
        Attendees._ID,                      // 0
        Attendees.ATTENDEE_NAME,            // 1
        Attendees.ATTENDEE_EMAIL,           // 2
        Attendees.ATTENDEE_RELATIONSHIP,    // 3
        Attendees.ATTENDEE_STATUS,          // 4
        Attendees.ATTENDEE_IDENTITY,        // 5
        Attendees.ATTENDEE_ID_NAMESPACE     // 6
    };
    
    public static final int ATTENDEES_INDEX_ID = 0;
    public static final int ATTENDEES_INDEX_NAME = 1;
    public static final int ATTENDEES_INDEX_EMAIL = 2;
    public static final int ATTENDEES_INDEX_RELATIONSHIP = 3;
    public static final int ATTENDEES_INDEX_STATUS = 4;
    public static final int ATTENDEES_INDEX_IDENTITY = 5;
    public static final int ATTENDEES_INDEX_ID_NAMESPACE = 6;

    public static final String ATTENDEES_WHERE = Attendees.EVENT_ID + "=?";

    public static final String ATTENDEES_SORT_ORDER = Attendees.ATTENDEE_NAME + " ASC, "
            + Attendees.ATTENDEE_EMAIL + " ASC";
    public static final String ATTENDEES_DELETE_PREFIX = Attendees.EVENT_ID + "=? AND "
            + Attendees.ATTENDEE_EMAIL + " IN (";

    public static final int ATTENDEE_ID_NONE = -1;
    public static final int[] ATTENDEE_VALUES = {
        Attendees.ATTENDEE_STATUS_NONE,
        Attendees.ATTENDEE_STATUS_ACCEPTED,
        Attendees.ATTENDEE_STATUS_TENTATIVE,
        Attendees.ATTENDEE_STATUS_DECLINED,
    };
}