/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.calendar;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.CalendarContract;
import android.provider.CalendarContract.CalendarCache;
import android.provider.SearchRecentSuggestions;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.Time;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.preference.SwitchPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.Preference.OnPreferenceClickListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.android.calendar.alerts.AlertReceiver;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

public class GeneralPreferences extends PreferenceFragment implements OnPreferenceChangeListener {
    // The name of the shared preferences file. This name must be maintained for historical
    // reasons, as it's what PreferenceManager assigned the first time the file was created.
    static final String SHARED_PREFS_NAME = "com.android.calendar_preferences";
    static final String SHARED_PREFS_NAME_NO_BACKUP = "com.android.calendar_preferences_no_backup";

    // Preference keys
    public static final String KEY_HIDE_DECLINED = "preferences_hide_declined";
    public static final String KEY_WEEK_START_DAY = "preferences_week_start_day";
    public static final String KEY_SHOW_WEEK_NUM = "preferences_show_week_num";
    public static final String KEY_DAYS_PER_WEEK = "preferences_days_per_week";
    public static final String KEY_SKIP_SETUP = "preferences_skip_setup";

    public static final String KEY_CLEAR_SEARCH_HISTORY = "preferences_clear_search_history";

    public static final String KEY_ALERTS_CATEGORY = "preferences_alerts_category";
    public static final String KEY_NOTIFICATIONS = "preferences_notifications";
    public static final String KEY_CATEGORY_QUICK_RESPONSE = "preferences_quick_responses";

    public static final String KEY_SHOW_CONTROLS = "preferences_show_controls";

    public static final String KEY_DEFAULT_REMINDER = "preferences_default_reminder";
    public static final int NO_REMINDER = -1;
    public static final String NO_REMINDER_STRING = "-1";
    public static final int REMINDER_DEFAULT_TIME = 10; // in minutes

    public static final String KEY_DEFAULT_CELL_HEIGHT = "preferences_default_cell_height";
    public static final String KEY_VERSION = "preferences_version";

    public static final String KEY_WIDGET_DAYS = "preferences_widget_days";
    public static final String KEY_WIDGET_DAYS_DEFAULT = "14";

    /** Key to SharePreference for default view (CalendarController.ViewType) */
    public static final String KEY_START_VIEW = "preferred_startView";
    /**
     *  Key to SharePreference for default detail view (CalendarController.ViewType)
     *  Typically used by widget
     */
    public static final String KEY_DETAILED_VIEW = "preferred_detailedView";
    public static final String KEY_DEFAULT_CALENDAR = "preference_defaultCalendar";

    public static final String KEY_HOURS_FILTER_START = "preferences_hours_filter_start";
    public static final String KEY_HOURS_FILTER_END = "preferences_hours_filter_end";
    public static final int KEY_HOURS_FILTER_START_DEFAULT = 8;
    public static final int KEY_HOURS_FILTER_END_DEFAULT = 21;
    public static final String KEY_HOURS_FILTER_ENABLED = "preferences_hours_filter_enabled";

    private static final int START_LISTENER = 1;
    private static final int END_LISTENER = 2;
    private static final String format24Hour = "%H:%M";
    private static final String format12Hour = "%I:%M%P";

    // These must be in sync with the array preferences_week_start_day_values
    public static final String WEEK_START_DEFAULT = "-1";
    public static final String WEEK_START_SATURDAY = "7";
    public static final String WEEK_START_SUNDAY = "1";
    public static final String WEEK_START_MONDAY = "2";

    // These keys are kept to enable migrating users from previous versions
    private static final String KEY_ALERTS_TYPE = "preferences_alerts_type";
    private static final String ALERT_TYPE_ALERTS = "0";
    private static final String ALERT_TYPE_STATUS_BAR = "1";
    private static final String ALERT_TYPE_OFF = "2";
    static final String KEY_HOME_TZ_ENABLED = "preferences_home_tz_enabled";
    static final String KEY_HOME_TZ = "preferences_home_tz";

    // Default preference values
    public static final int DEFAULT_START_VIEW = CalendarController.ViewType.WEEK;
    public static final int DEFAULT_DETAILED_VIEW = CalendarController.ViewType.DAY;
    public static final boolean DEFAULT_SHOW_WEEK_NUM = false;
    // This should match the XML file.
    public static final String DEFAULT_RINGTONE = "content://settings/system/notification_sound";
    public static final String SNOOZE_TIME_DEFAULT = "5";
    public static final String KEY_SNOOZE_TIME = "preferences_snooze_time";

    SwitchPreference mUseHomeTZ;
    SwitchPreference mHideDeclined;
    ListPreference mWeekStart;
    ListPreference mDefaultReminder;
    private Preference mStartHour;
    private TimeSetListener mTimePickerListenerStartTime;
    private Preference mEndHour;
    private TimeSetListener mTimePickerListenerEndTime;
    private ListPreference mWidgetDays;
    private ListPreference mSnoozeTime;
    private String[] mResponses;

    /** Return a properly configured SharedPreferences instance */
    public static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Set the default shared preferences in the proper context */
    public static void setDefaultValues(Context context) {
        PreferenceManager.setDefaultValues(context, SHARED_PREFS_NAME, Context.MODE_PRIVATE,
                R.xml.general_preferences, false);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final Activity activity = getActivity();

        // Make sure to always use the same preferences file regardless of the package name
        // we're running under
        final PreferenceManager preferenceManager = getPreferenceManager();
        final SharedPreferences sharedPreferences = getSharedPreferences(activity);
        preferenceManager.setSharedPreferencesName(SHARED_PREFS_NAME);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.general_preferences);

        final PreferenceScreen preferenceScreen = getPreferenceScreen();

        mUseHomeTZ = (SwitchPreference) preferenceScreen.findPreference(KEY_HOME_TZ_ENABLED);
        mHideDeclined = (SwitchPreference) preferenceScreen.findPreference(KEY_HIDE_DECLINED);
        mWeekStart = (ListPreference) preferenceScreen.findPreference(KEY_WEEK_START_DAY);
        mDefaultReminder = (ListPreference) preferenceScreen.findPreference(KEY_DEFAULT_REMINDER);
        mWeekStart.setSummary(mWeekStart.getEntry());
        mDefaultReminder.setSummary(mDefaultReminder.getEntry());
        mWidgetDays = (ListPreference) preferenceScreen.findPreference(KEY_WIDGET_DAYS);
        mWidgetDays.setSummary(mWidgetDays.getEntry());
        mSnoozeTime= (ListPreference) preferenceScreen.findPreference(KEY_SNOOZE_TIME);
        mSnoozeTime.setSummary(mSnoozeTime.getEntry());

        SharedPreferences prefs = CalendarUtils.getSharedPreferences(activity,
                Utils.SHARED_PREFS_NAME);
        int startHour = prefs.getInt(KEY_HOURS_FILTER_START,
                KEY_HOURS_FILTER_START_DEFAULT);
        mStartHour = findPreference(KEY_HOURS_FILTER_START);
        mTimePickerListenerStartTime = new TimeSetListener(START_LISTENER);
        mStartHour.setSummary(formatTime(startHour, 0));

        int endHour = prefs.getInt(KEY_HOURS_FILTER_END,
                KEY_HOURS_FILTER_END_DEFAULT);
        mEndHour = findPreference(KEY_HOURS_FILTER_END);
        mTimePickerListenerEndTime = new TimeSetListener(END_LISTENER);
        mEndHour.setSummary(formatTime(endHour, 0));
        setPreferenceListeners(this);

        PreferenceCategory quickResponses = (PreferenceCategory) preferenceScreen.findPreference(KEY_CATEGORY_QUICK_RESPONSE);
        mResponses = Utils.getQuickResponses(getActivity());

        if (mResponses != null) {
            Arrays.sort(mResponses);
            int i = 0;
            for (String response : mResponses) {
                EditTextPreference et = new EditTextPreference(preferenceScreen.getContext());
                et.setKey("quick_response_" + i);
                et.setPersistent(false);
                et.setTitle(response);
                et.setText(response);
                et.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        EditTextPreference editPreference = (EditTextPreference) preference;
                        String key = editPreference.getKey();
                        int keyNum = Integer.valueOf(key.split("_")[2]);
                        mResponses[keyNum] = (String) newValue;
                        editPreference.setTitle((String) newValue);
                        editPreference.setText((String) newValue);
                        Utils.setSharedPreference(getActivity(), Utils.KEY_QUICK_RESPONSES, mResponses);
                        return true;
                    }
                });
                quickResponses.addPreference(et);
                i++;
            }
        }
    }

    /**
     * Sets up all the preference change listeners to use the specified
     * listener.
     */
    private void setPreferenceListeners(OnPreferenceChangeListener listener) {
        mUseHomeTZ.setOnPreferenceChangeListener(listener);
        mWeekStart.setOnPreferenceChangeListener(listener);
        mDefaultReminder.setOnPreferenceChangeListener(listener);
        mHideDeclined.setOnPreferenceChangeListener(listener);
        mWidgetDays.setOnPreferenceChangeListener(listener);
        mSnoozeTime.setOnPreferenceChangeListener(listener);
    }

    /**
     * Handles time zone preference changes
     */
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String tz;
        final Activity activity = getActivity();
        if (preference == mHideDeclined) {
            mHideDeclined.setChecked((Boolean) newValue);
            Intent intent = new Intent(Utils.getWidgetScheduledUpdateAction(activity));
            intent.setDataAndType(CalendarContract.CONTENT_URI, Utils.APPWIDGET_DATA_TYPE);
            activity.sendBroadcast(intent);
            return true;
        } else if (preference == mWeekStart) {
            mWeekStart.setValue((String) newValue);
            mWeekStart.setSummary(mWeekStart.getEntry());
        } else if (preference == mDefaultReminder) {
            mDefaultReminder.setValue((String) newValue);
            mDefaultReminder.setSummary(mDefaultReminder.getEntry());
        } else if (preference == mWidgetDays) {
            mWidgetDays.setValue((String) newValue);
            mWidgetDays.setSummary(mWidgetDays.getEntry());
            Intent intent = new Intent(Utils.getWidgetScheduledUpdateAction(activity));
            intent.setDataAndType(CalendarContract.CONTENT_URI, Utils.APPWIDGET_DATA_TYPE);
            activity.sendBroadcast(intent);
            return true;
        } else if (preference == mSnoozeTime) {
            mSnoozeTime.setValue((String) newValue);
            mSnoozeTime.setSummary(mSnoozeTime.getEntry());
            return true;
        } else {
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        final String key = preference.getKey();
        SharedPreferences prefs = CalendarUtils.getSharedPreferences(getActivity(),
                Utils.SHARED_PREFS_NAME);
        if (KEY_CLEAR_SEARCH_HISTORY.equals(key)) {
            SearchRecentSuggestions suggestions = new SearchRecentSuggestions(getActivity(),
                    Utils.getSearchAuthority(getActivity()),
                    CalendarRecentSuggestionsProvider.MODE);
            suggestions.clearHistory();
            Toast.makeText(getActivity(), R.string.search_history_cleared,
                    Toast.LENGTH_SHORT).show();
            return true;
        } else if (preference == mStartHour) {
            int startHour = prefs.getInt(KEY_HOURS_FILTER_START,
                KEY_HOURS_FILTER_START_DEFAULT);
            TimePickerDialog timePicker = new TimePickerDialog(
                getActivity(), mTimePickerListenerStartTime,
                startHour, 0, DateFormat.is24HourFormat(getActivity()));
            timePicker.show();
            return true;
        } else if (preference == mEndHour) {
            int endHour = prefs.getInt(KEY_HOURS_FILTER_END,
                KEY_HOURS_FILTER_END_DEFAULT);
            TimePickerDialog timePicker = new TimePickerDialog(
                getActivity(), mTimePickerListenerEndTime,
                endHour, 0, DateFormat.is24HourFormat(getActivity()));
            timePicker.show();
            return true;
        } else if (KEY_NOTIFICATIONS.equals(key)) {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
            startActivity(intent);
            return true;
        /*} else if (KEY_QUICK_RESPONSE.equals(key)) {
            getFragmentManager().beginTransaction().replace(android.R.id.content, new QuickResponseSettings()).commit();
            return true;*/
        } else {
            return super.onPreferenceTreeClick(preference);
        }
    }

    private class TimeSetListener implements TimePickerDialog.OnTimeSetListener {
        private int mListenerId;

        public TimeSetListener(int listenerId) {
            mListenerId = listenerId;
        }

        @Override
        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
            SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
            int startHour = prefs.getInt(KEY_HOURS_FILTER_START,
                KEY_HOURS_FILTER_START_DEFAULT);
            int endHour = prefs.getInt(KEY_HOURS_FILTER_END,
                KEY_HOURS_FILTER_END_DEFAULT);
            String summary = formatTime(hourOfDay, 0);
            switch (mListenerId) {
                case (START_LISTENER):
                    if (hourOfDay >= endHour) {
                        Toast.makeText(getActivity(), R.string.hour_margin_invalid, Toast.LENGTH_LONG).show();
                        return;
                    }
                    mStartHour.setSummary(summary);
                    prefs.edit().putInt(KEY_HOURS_FILTER_START, hourOfDay).commit();
                    break;
                case (END_LISTENER):
                    if (hourOfDay == 0) {
                        hourOfDay = 24;
                    }
                    if (hourOfDay <= startHour) {
                        Toast.makeText(getActivity(), R.string.hour_margin_invalid, Toast.LENGTH_LONG).show();
                        return;
                    }
                    mEndHour.setSummary(summary);
                    prefs.edit().putInt(KEY_HOURS_FILTER_END, hourOfDay).commit();
                    break;
            }
        }
    }

    /**
     * @param hourOfDay the hour of the day (0-24)
     * @param minute
     * @return human-readable string formatted based on 24-hour mode.
     */
    private String formatTime(int hourOfDay, int minute) {
        Time time = new Time();
        time.hour = hourOfDay;
        time.minute = minute;
        String format = DateFormat.is24HourFormat(getActivity())? format24Hour : format12Hour;
        return time.format(format);
    }
}
