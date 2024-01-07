package com.android.calendar;

import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Colors;

public class CalendarQueries {
    public static final String[] CALENDARS_PROJECTION = new String[] {
            Calendars._ID, // 0
            Calendars.CALENDAR_DISPLAY_NAME, // 1
            Calendars.OWNER_ACCOUNT, // 2
            Calendars.CALENDAR_COLOR, // 3
            Calendars.CAN_ORGANIZER_RESPOND, // 4
            Calendars.CALENDAR_ACCESS_LEVEL, // 5
            Calendars.VISIBLE, // 6
            Calendars.MAX_REMINDERS, // 7
            Calendars.ALLOWED_REMINDERS, // 8
            Calendars.ALLOWED_ATTENDEE_TYPES, // 9
            Calendars.ALLOWED_AVAILABILITY, // 10
            Calendars.ACCOUNT_NAME, // 11
            Calendars.ACCOUNT_TYPE, //12
    };
    public static final int CALENDARS_INDEX_ID = 0;
    public static final int CALENDARS_INDEX_DISPLAY_NAME = 1;
    public static final int CALENDARS_INDEX_OWNER_ACCOUNT = 2;
    public static final int CALENDARS_INDEX_COLOR = 3;
    public static final int CALENDARS_INDEX_CAN_ORGANIZER_RESPOND = 4;
    public static final int CALENDARS_INDEX_ACCESS_LEVEL = 5;
    public static final int CALENDARS_INDEX_VISIBLE = 6;
    public static final int CALENDARS_INDEX_MAX_REMINDERS = 7;
    public static final int CALENDARS_INDEX_ALLOWED_REMINDERS = 8;
    public static final int CALENDARS_INDEX_ALLOWED_ATTENDEE_TYPES = 9;
    public static final int CALENDARS_INDEX_ALLOWED_AVAILABILITY = 10;
    public static final int CALENDARS_INDEX_ACCOUNT_NAME = 11;
    public static final int CALENDARS_INDEX_ACCOUNT_TYPE = 12;

    public static final String CALENDARS_WHERE_WRITEABLE_VISIBLE = Calendars.CALENDAR_ACCESS_LEVEL + ">="
            + Calendars.CAL_ACCESS_CONTRIBUTOR + " AND " + Calendars.VISIBLE + "=1";

    public static final String CALENDARS_WHERE = Calendars._ID + "=?";
    public static final String CALENDARS_DUPLICATE_NAME_WHERE = Calendars.CALENDAR_DISPLAY_NAME + "=?";
    public static final String CALENDARS_VISIBLE_WHERE = Calendars.VISIBLE + "=?";
    
    public static final String[] COLORS_PROJECTION = new String[] {
        Colors._ID, // 0
        Colors.ACCOUNT_NAME,
        Colors.ACCOUNT_TYPE,
        Colors.COLOR, // 1
        Colors.COLOR_KEY // 2
    };

    public static final String COLORS_EVENT_WHERE_SIMPLE = Colors.COLOR_TYPE + "=" + Colors.TYPE_EVENT;

    public static final String COLORS_EVENT_WHERE = Colors.ACCOUNT_NAME + "=? AND " + Colors.ACCOUNT_TYPE +
        "=? AND " + Colors.COLOR_TYPE + "=" + Colors.TYPE_EVENT;

    public static final String COLORS_CALENDAR_WHERE = Colors.ACCOUNT_NAME + "=? AND " + Colors.ACCOUNT_TYPE +
            "=? AND " + Colors.COLOR_TYPE + "=" + Colors.TYPE_CALENDAR;
            
    public static final int COLORS_INDEX_ACCOUNT_NAME = 1;
    public static final int COLORS_INDEX_ACCOUNT_TYPE = 2;
    public static final int COLORS_INDEX_COLOR = 3;
    public static final int COLORS_INDEX_COLOR_KEY = 4;
}