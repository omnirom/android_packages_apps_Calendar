/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.calendar.alerts;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.PersistableBundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.CalendarAlerts;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;

import com.android.calendar.GeneralPreferences;
import com.android.calendar.R;
import com.android.calendar.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

/**
 * This service is used to handle calendar event reminders.
 */
public class AlertJobService extends JobService {
    static final boolean DEBUG = true;
    private static final String TAG = "Calendar:AlertJobService";

    private volatile Looper mServiceLooper;
    private volatile ServiceHandler mServiceHandler;
    public static final int ONCE_UPDATE_JOB_ID = 1;

    static final String[] ALERT_PROJECTION = new String[] {
        CalendarAlerts._ID,                     // 0
        CalendarAlerts.EVENT_ID,                // 1
        CalendarAlerts.STATE,                   // 2
        CalendarAlerts.TITLE,                   // 3
        CalendarAlerts.EVENT_LOCATION,          // 4
        CalendarAlerts.SELF_ATTENDEE_STATUS,    // 5
        CalendarAlerts.ALL_DAY,                 // 6
        CalendarAlerts.ALARM_TIME,              // 7
        CalendarAlerts.MINUTES,                 // 8
        CalendarAlerts.BEGIN,                   // 9
        CalendarAlerts.END,                     // 10
        CalendarAlerts.DESCRIPTION,             // 11
    };

    private static final int ALERT_INDEX_ID = 0;
    private static final int ALERT_INDEX_EVENT_ID = 1;
    private static final int ALERT_INDEX_STATE = 2;
    private static final int ALERT_INDEX_TITLE = 3;
    private static final int ALERT_INDEX_EVENT_LOCATION = 4;
    private static final int ALERT_INDEX_SELF_ATTENDEE_STATUS = 5;
    private static final int ALERT_INDEX_ALL_DAY = 6;
    private static final int ALERT_INDEX_ALARM_TIME = 7;
    private static final int ALERT_INDEX_MINUTES = 8;
    private static final int ALERT_INDEX_BEGIN = 9;
    private static final int ALERT_INDEX_END = 10;
    private static final int ALERT_INDEX_DESCRIPTION = 11;

    private static final String ACTIVE_ALERTS_SELECTION = "(" + CalendarAlerts.STATE + "=? OR "
            + CalendarAlerts.STATE + "=?) AND " + CalendarAlerts.ALARM_TIME + "<=";

    private static final String[] ACTIVE_ALERTS_SELECTION_ARGS = new String[] {
            Integer.toString(CalendarAlerts.STATE_FIRED),
            Integer.toString(CalendarAlerts.STATE_SCHEDULED)
    };

    private static final String ACTIVE_ALERTS_SORT = "begin DESC, end DESC";

    private static final String DISMISS_OLD_SELECTION = CalendarAlerts.END + "<? AND "
            + CalendarAlerts.STATE + "=?";

    private static final int MINUTE_MS = 60 * 1000;

    // The grace period before changing a notification's priority bucket.
    private static final int MIN_DEPRIORITIZE_GRACE_PERIOD_MS = 15 * MINUTE_MS;

    // Hard limit to the number of notifications displayed.
    public static final int MAX_NOTIFICATIONS = 20;
    public static final int REFRESH_NOTIFICATION_ID = MAX_NOTIFICATIONS + 1;

    private JobParameters mJobParameters;

    // Added wrapper for testing
    public static class NotificationWrapper {
        Notification mNotification;
        long mEventId;
        long mBegin;
        long mEnd;
        ArrayList<NotificationWrapper> mNw;

        public NotificationWrapper(Notification n, int notificationId, long eventId,
                long startMillis, long endMillis) {
            mNotification = n;
            mEventId = eventId;
            mBegin = startMillis;
            mEnd = endMillis;

            // notification id?
        }

        public NotificationWrapper(Notification n) {
            mNotification = n;
        }

        public void add(NotificationWrapper nw) {
            if (mNw == null) {
                mNw = new ArrayList<NotificationWrapper>();
            }
            mNw.add(nw);
        }
    }

    // Added wrapper for testing
    public static class NotificationMgrWrapper extends NotificationMgr {
        NotificationManager mNm;

        public NotificationMgrWrapper(NotificationManager nm) {
            mNm = nm;
        }

        @Override
        public void cancel(int id) {
            mNm.cancel(id);
        }

        @Override
        public void notify(int id, NotificationWrapper nw) {
            mNm.notify(id, nw.mNotification);
        }
    }

    void processMessage(Message msg) {
        PersistableBundle bundle = (PersistableBundle) msg.obj;

        // On reboot, update the notification bar with the contents of the
        // CalendarAlerts table.
        String action = bundle.getString("action");
        if (DEBUG) {
            Log.d(TAG, bundle.getLong(android.provider.CalendarContract.CalendarAlerts.ALARM_TIME)
                    + " Action = " + action);
        }

        if (action.equals(Intent.ACTION_PROVIDER_CHANGED) ||
                action.equals(android.provider.CalendarContract.ACTION_EVENT_REMINDER) ||
                action.equals(AlertReceiver.EVENT_REMINDER_APP_ACTION) ||
                action.equals(Intent.ACTION_LOCALE_CHANGED)) {

            Utils.refreshCalendars(this);
            updateAlertNotification(this);
        } else if (action.equals(Intent.ACTION_TIME_CHANGED) ||
                action.equals(Intent.ACTION_TIMEZONE_CHANGED)) {
            doTimeChanged();
        } else if (action.equals(AlertReceiver.ACTION_DISMISS_OLD_REMINDERS)) {
            dismissOldAlerts(this);
        } else {
            Log.w(TAG, "Invalid action: " + action);
        }

        // Schedule the alarm for the next upcoming reminder, if not done by the provider.
        Log.d(TAG, "Scheduling next alarm with AlarmScheduler");
        AlarmScheduler.scheduleNextAlarm(this);
    }

    static void dismissOldAlerts(Context context) {
        ContentResolver cr = context.getContentResolver();
        final long currentTime = System.currentTimeMillis();
        ContentValues vals = new ContentValues();
        vals.put(CalendarAlerts.STATE, CalendarAlerts.STATE_DISMISSED);
        cr.update(CalendarAlerts.CONTENT_URI, vals, DISMISS_OLD_SELECTION, new String[] {
                Long.toString(currentTime), Integer.toString(CalendarAlerts.STATE_SCHEDULED)
        });
    }

    static boolean updateAlertNotification(Context context) {
        if (!isPermissionEnabled(context)) {
            Log.e(TAG, "Blocked updateAlertNotification because of missing permissions");
            return false;
        }

        ContentResolver cr = context.getContentResolver();
        NotificationMgr nm = new NotificationMgrWrapper(
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));
        final long currentTime = System.currentTimeMillis();
        SharedPreferences prefs = GeneralPreferences.getSharedPreferences(context);

        if (DEBUG) {
            Log.d(TAG, "Beginning updateAlertNotification currentTime = " + currentTime);
        }

        Cursor alertCursor = cr.query(CalendarAlerts.CONTENT_URI, ALERT_PROJECTION,
                (ACTIVE_ALERTS_SELECTION + currentTime), ACTIVE_ALERTS_SELECTION_ARGS,
                ACTIVE_ALERTS_SORT);

        if (alertCursor == null || alertCursor.getCount() == 0) {
            if (alertCursor != null) {
                alertCursor.close();
            }

            if (DEBUG) Log.d(TAG, "No fired or scheduled alerts");
            //nm.cancelAll();
            return false;
        }

        return generateAlerts(context, nm, AlertUtils.createAlarmManager(context), prefs,
                alertCursor, currentTime, MAX_NOTIFICATIONS);
    }

    public static boolean generateAlerts(Context context, NotificationMgr nm,
            AlarmManagerInterface alarmMgr, SharedPreferences prefs, Cursor alertCursor,
            final long currentTime, final int maxNotifications) {
        if (DEBUG) {
            Log.d(TAG, "alertCursor count:" + alertCursor.getCount());
        }

        // Process the query results and bucketize events.
        ArrayList<NotificationInfo> highPriorityEvents = new ArrayList<NotificationInfo>();
        ArrayList<NotificationInfo> mediumPriorityEvents = new ArrayList<NotificationInfo>();
        ArrayList<NotificationInfo> lowPriorityEvents = new ArrayList<NotificationInfo>();
        int numFired = processQuery(alertCursor, context, currentTime, highPriorityEvents,
                mediumPriorityEvents, lowPriorityEvents);

        if (highPriorityEvents.size() + mediumPriorityEvents.size()
                + lowPriorityEvents.size() == 0) {
            //nm.cancelAll();
            return true;
        }

        long nextRefreshTime = Long.MAX_VALUE;
        int currentNotificationId = 1;

        // If there are more high/medium priority events than we can show, bump some to
        // the low priority digest.
        redistributeBuckets(highPriorityEvents, mediumPriorityEvents, lowPriorityEvents,
                maxNotifications);

        // Post the individual higher priority events (future and recently started
        // concurrent events).  Order these so that earlier start times appear higher in
        // the notification list.
        for (int i = 0; i < highPriorityEvents.size(); i++) {
            NotificationInfo info = highPriorityEvents.get(i);
            String summaryText = AlertUtils.formatTimeLocation(context, info.startMillis,
                    info.allDay, info.location);
            postNotification(info, summaryText, context, true, nm,
                    currentNotificationId++);

            // Keep concurrent events high priority (to appear higher in the notification list)
            // until 15 minutes into the event.
            nextRefreshTime = Math.min(nextRefreshTime, getNextRefreshTime(info, currentTime));
        }

        // Post the medium priority events (concurrent events that started a while ago).
        // Order these so more recent start times appear higher in the notification list.
        //
        // TODO: Post these with the same notification priority level as the higher priority
        // events, so that all notifications will be co-located together.
        for (int i = mediumPriorityEvents.size() - 1; i >= 0; i--) {
            NotificationInfo info = mediumPriorityEvents.get(i);
            // TODO: Change to a relative time description like: "Started 40 minutes ago".
            // This requires constant refreshing to the message as time goes.
            String summaryText = AlertUtils.formatTimeLocation(context, info.startMillis,
                    info.allDay, info.location);
            postNotification(info, summaryText, context, false, nm,
                    currentNotificationId++);

            // Refresh when concurrent event ends so it will drop into the expired digest.
            nextRefreshTime = Math.min(nextRefreshTime, getNextRefreshTime(info, currentTime));
        }

        // Post the low priority events as 1 combined notification.
        int numLowPriority = lowPriorityEvents.size();
        if (numLowPriority > 0) {
            String expiredDigestTitle = getDigestTitle(lowPriorityEvents);
            NotificationWrapper notification;
            if (numLowPriority == 1) {
                // If only 1 expired event, display an "old-style" basic alert.
                NotificationInfo info = lowPriorityEvents.get(0);
                String summaryText = AlertUtils.formatTimeLocation(context, info.startMillis,
                        info.allDay, info.location);
                notification = AlertReceiver.makeBasicNotification(context, info.eventName,
                        summaryText, info.startMillis, info.endMillis, info.eventId,
                        AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID,
                        Notification.PRIORITY_DEFAULT);
            } else {
                // Multiple expired events are listed in a digest.
                notification = AlertReceiver.makeDigestNotification(context,
                    lowPriorityEvents, expiredDigestTitle, false);
            }

            if (DEBUG) {
              Log.d(TAG, "Quietly posting digest alarm notification, numEvents:" + numLowPriority
                      + ", notificationId:" + AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID);
          }

            // Post the new notification for the group.
            nm.notify(AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID, notification);
        } else {
            nm.cancel(AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID);
            if (DEBUG) {
                Log.d(TAG, "No low priority events, canceling the digest notification.");
            }
        }

        // Remove the notifications that are hanging around from the previous refresh.
        if (currentNotificationId <= maxNotifications) {
            nm.cancelAllBetween(currentNotificationId, maxNotifications);
            if (DEBUG) {
                Log.d(TAG, "Canceling leftover notification IDs " + currentNotificationId + "-"
                        + maxNotifications);
            }
        }

        // Schedule the next silent refresh time so notifications will change
        // buckets (eg. drop into expired digest, etc).
        if (nextRefreshTime < Long.MAX_VALUE && nextRefreshTime > currentTime) {
            AlertUtils.scheduleNextNotificationRefresh(context, alarmMgr, nextRefreshTime);
            if (DEBUG) {
                long minutesBeforeRefresh = (nextRefreshTime - currentTime) / MINUTE_MS;
                Time time = new Time();
                time.set(nextRefreshTime);
                String msg = String.format("Scheduling next notification refresh in %d min at: "
                        + "%d:%02d", minutesBeforeRefresh, time.hour, time.minute);
                Log.d(TAG, msg);
            }
        } else if (nextRefreshTime < currentTime) {
            Log.e(TAG, "Illegal state: next notification refresh time found to be in the past.");
        }

        // Flushes old fired alerts from internal storage, if needed.
        AlertUtils.flushOldAlertsFromInternalStorage(context);

        return true;
    }

    /**
     * Redistributes events in the priority lists based on the max # of notifications we
     * can show.
     */
    static void redistributeBuckets(ArrayList<NotificationInfo> highPriorityEvents,
            ArrayList<NotificationInfo> mediumPriorityEvents,
            ArrayList<NotificationInfo> lowPriorityEvents, int maxNotifications) {

        // If too many high priority alerts, shift the remaining high priority and all the
        // medium priority ones to the low priority bucket.  Note that order is important
        // here; these lists are sorted by descending start time.  Maintain that ordering
        // so posted notifications are in the expected order.
        if (highPriorityEvents.size() > maxNotifications) {
            // Move mid-priority to the digest.
            lowPriorityEvents.addAll(0, mediumPriorityEvents);

            // Move the rest of the high priority ones (latest ones) to the digest.
            List<NotificationInfo> itemsToMoveSublist = highPriorityEvents.subList(
                    0, highPriorityEvents.size() - maxNotifications);
            // TODO: What order for high priority in the digest?
            lowPriorityEvents.addAll(0, itemsToMoveSublist);
            if (DEBUG) {
                logEventIdsBumped(mediumPriorityEvents, itemsToMoveSublist);
            }
            mediumPriorityEvents.clear();
            // Clearing the sublist view removes the items from the highPriorityEvents list.
            itemsToMoveSublist.clear();
        }

        // Bump the medium priority events if necessary.
        if (mediumPriorityEvents.size() + highPriorityEvents.size() > maxNotifications) {
            int spaceRemaining = maxNotifications - highPriorityEvents.size();

            // Reached our max, move the rest to the digest.  Since these are concurrent
            // events, we move the ones with the earlier start time first since they are
            // further in the past and less important.
            List<NotificationInfo> itemsToMoveSublist = mediumPriorityEvents.subList(
                    spaceRemaining, mediumPriorityEvents.size());
            lowPriorityEvents.addAll(0, itemsToMoveSublist);
            if (DEBUG) {
                logEventIdsBumped(itemsToMoveSublist, null);
            }

            // Clearing the sublist view removes the items from the mediumPriorityEvents list.
            itemsToMoveSublist.clear();
        }
    }

    private static void logEventIdsBumped(List<NotificationInfo> list1,
            List<NotificationInfo> list2) {
        StringBuilder ids = new StringBuilder();
        if (list1 != null) {
            for (NotificationInfo info : list1) {
                ids.append(info.eventId);
                ids.append(",");
            }
        }
        if (list2 != null) {
            for (NotificationInfo info : list2) {
                ids.append(info.eventId);
                ids.append(",");
            }
        }
        if (ids.length() > 0 && ids.charAt(ids.length() - 1) == ',') {
            ids.setLength(ids.length() - 1);
        }
        if (ids.length() > 0) {
            Log.d(TAG, "Reached max postings, bumping event IDs {" + ids.toString()
                    + "} to digest.");
        }
    }

    private static long getNextRefreshTime(NotificationInfo info, long currentTime) {
        long startAdjustedForAllDay = info.startMillis;
        long endAdjustedForAllDay = info.endMillis;
        if (info.allDay) {
            Time t = new Time();
            startAdjustedForAllDay = Utils.convertAlldayUtcToLocal(t, info.startMillis,
                    Time.getCurrentTimezone());
            endAdjustedForAllDay = Utils.convertAlldayUtcToLocal(t, info.startMillis,
                    Time.getCurrentTimezone());
        }

        // We change an event's priority bucket at 15 minutes into the event or 1/4 event duration.
        long nextRefreshTime = Long.MAX_VALUE;
        long gracePeriodCutoff = startAdjustedForAllDay +
                getGracePeriodMs(startAdjustedForAllDay, endAdjustedForAllDay, info.allDay);
        if (gracePeriodCutoff > currentTime) {
            nextRefreshTime = Math.min(nextRefreshTime, gracePeriodCutoff);
        }

        // ... and at the end (so expiring ones drop into a digest).
        if (endAdjustedForAllDay > currentTime && endAdjustedForAllDay > gracePeriodCutoff) {
            nextRefreshTime = Math.min(nextRefreshTime, endAdjustedForAllDay);
        }
        return nextRefreshTime;
    }

    /**
     * Processes the query results and bucketizes the alerts.
     *
     * @param highPriorityEvents This will contain future events, and concurrent events
     *     that started recently (less than the interval DEPRIORITIZE_GRACE_PERIOD_MS).
     * @param mediumPriorityEvents This will contain concurrent events that started
     *     more than DEPRIORITIZE_GRACE_PERIOD_MS ago.
     * @param lowPriorityEvents Will contain events that have ended.
     * @return Returns the number of new alerts to fire.  If this is 0, it implies
     *     a quiet update.
     */
    static int processQuery(final Cursor alertCursor, final Context context,
            final long currentTime, ArrayList<NotificationInfo> highPriorityEvents,
            ArrayList<NotificationInfo> mediumPriorityEvents,
            ArrayList<NotificationInfo> lowPriorityEvents) {

        Time time = new Time();

        ContentResolver cr = context.getContentResolver();
        HashMap<Long, NotificationInfo> eventIds = new HashMap<Long, NotificationInfo>();
        int numFired = 0;
        try {
            while (alertCursor.moveToNext()) {
                final long alertId = alertCursor.getLong(ALERT_INDEX_ID);
                final long eventId = alertCursor.getLong(ALERT_INDEX_EVENT_ID);
                final int minutes = alertCursor.getInt(ALERT_INDEX_MINUTES);
                final String eventName = alertCursor.getString(ALERT_INDEX_TITLE);
                final String description = alertCursor.getString(ALERT_INDEX_DESCRIPTION);
                final String location = alertCursor.getString(ALERT_INDEX_EVENT_LOCATION);
                final int status = alertCursor.getInt(ALERT_INDEX_SELF_ATTENDEE_STATUS);
                final boolean declined = status == Attendees.ATTENDEE_STATUS_DECLINED;
                final boolean responded = status != Attendees.ATTENDEE_STATUS_NONE
                        && status != Attendees.ATTENDEE_STATUS_INVITED;
                final long beginTime = alertCursor.getLong(ALERT_INDEX_BEGIN);
                final long endTime = alertCursor.getLong(ALERT_INDEX_END);
                final Uri alertUri = ContentUris
                        .withAppendedId(CalendarAlerts.CONTENT_URI, alertId);
                final long alarmTime = alertCursor.getLong(ALERT_INDEX_ALARM_TIME);

                int state = alertCursor.getInt(ALERT_INDEX_STATE);
                final boolean allDay = alertCursor.getInt(ALERT_INDEX_ALL_DAY) != 0;

                // Use app local storage to keep track of fired alerts to fix problem of multiple
                // installed calendar apps potentially causing missed alarms.
                boolean newAlertOverride = false;
                boolean alreadyFired = false;
                if (AlertUtils.BYPASS_DB) {
                    // To avoid re-firing alerts, only fire if alarmTime is very recent.  Otherwise
                    // we can get refires for non-dismissed alerts after app installation, or if the
                    // SharedPrefs was cleared too early.  This means alerts that were timed while
                    // the phone was off may show up silently in the notification bar.
                    alreadyFired = AlertUtils.hasAlertFiredInSharedPrefs(context, eventId,
                            beginTime, alarmTime);
                    if (!alreadyFired) {
                        newAlertOverride = true;
                    }
                }

                if (DEBUG) {
                    StringBuilder msgBuilder = new StringBuilder();
                    msgBuilder.append("alertCursor result: alarmTime:").append(alarmTime)
                            .append(" alertId:").append(alertId)
                            .append(" eventId:").append(eventId)
                            .append(" state: ").append(state)
                            .append(" minutes:").append(minutes)
                            .append(" declined:").append(declined)
                            .append(" responded:").append(responded)
                            .append(" beginTime:").append(beginTime)
                            .append(" endTime:").append(endTime)
                            .append(" allDay:").append(allDay)
                            .append(" alarmTime:").append(alarmTime);
                    if (AlertUtils.BYPASS_DB) {
                        msgBuilder.append(" newAlertOverride: " + newAlertOverride);
                        msgBuilder.append(" alreadyFired: " + alreadyFired);
                    }
                    Log.d(TAG, msgBuilder.toString());
                }

                ContentValues values = new ContentValues();
                int newState = -1;
                boolean newAlert = false;

                // Uncomment for the behavior of clearing out alerts after the
                // events ended. b/1880369
                //
                // if (endTime < currentTime) {
                //     newState = CalendarAlerts.DISMISSED;
                // } else

                // Remove declined events
                boolean sendAlert = !declined;
                if (sendAlert) {
                    if (state == CalendarAlerts.STATE_SCHEDULED || newAlertOverride) {
                        newState = CalendarAlerts.STATE_FIRED;
                        numFired++;

                        // Record the received time in the CalendarAlerts table.
                        // This is useful for finding bugs that cause alarms to be
                        // missed or delayed.
                        values.put(CalendarAlerts.RECEIVED_TIME, currentTime);
                    }
                } else {
                    newState = CalendarAlerts.STATE_DISMISSED;
                }

                // Update row if state changed
                if (newState != -1) {
                    values.put(CalendarAlerts.STATE, newState);
                    state = newState;

                    if (AlertUtils.BYPASS_DB) {
                        AlertUtils.setAlertFiredInSharedPrefs(context, eventId, beginTime,
                                alarmTime);
                    }
                }

                if (state == CalendarAlerts.STATE_FIRED) {
                    // Record the time posting to notification manager.
                    // This is used for debugging missed alarms.
                    values.put(CalendarAlerts.NOTIFY_TIME, currentTime);
                }

                // Write row to if anything changed
                if (values.size() > 0) cr.update(alertUri, values, null, null);

                if (state != CalendarAlerts.STATE_FIRED || alreadyFired) {
                    continue;
                }

                // TODO: Prefer accepted events in case of ties.
                NotificationInfo newInfo = new NotificationInfo(eventName, location,
                        description, beginTime, endTime, eventId, allDay, newAlert);

                // Adjust for all day events to ensure the right bucket.  Don't use the 1/4 event
                // duration grace period for these.
                long beginTimeAdjustedForAllDay = beginTime;
                String tz = null;
                if (allDay) {
                    tz = TimeZone.getDefault().getID();
                    beginTimeAdjustedForAllDay = Utils.convertAlldayUtcToLocal(null, beginTime,
                            tz);
                }

                // Handle multiple alerts for the same event ID.
                if (eventIds.containsKey(eventId)) {
                    NotificationInfo oldInfo = eventIds.get(eventId);
                    long oldBeginTimeAdjustedForAllDay = oldInfo.startMillis;
                    if (allDay) {
                        oldBeginTimeAdjustedForAllDay = Utils.convertAlldayUtcToLocal(null,
                                oldInfo.startMillis, tz);
                    }

                    // Determine whether to replace the previous reminder with this one.
                    // Query results are sorted so this one will always have a lower start time.
                    long oldStartInterval = oldBeginTimeAdjustedForAllDay - currentTime;
                    long newStartInterval = beginTimeAdjustedForAllDay - currentTime;
                    boolean dropOld;
                    if (newStartInterval < 0 && oldStartInterval > 0) {
                        // Use this reminder if this event started recently
                        dropOld = Math.abs(newStartInterval) < MIN_DEPRIORITIZE_GRACE_PERIOD_MS;
                    } else {
                        // ... or if this one has a closer start time.
                        dropOld = Math.abs(newStartInterval) < Math.abs(oldStartInterval);
                    }

                    if (dropOld) {
                        // This is a recurring event that has a more relevant start time,
                        // drop other reminder in favor of this one.
                        //
                        // It will only be present in 1 of these buckets; just remove from
                        // multiple buckets since this occurrence is rare enough that the
                        // inefficiency of multiple removals shouldn't be a big deal to
                        // justify a more complicated data structure.  Expired events don't
                        // have individual notifications so we don't need to clean that up.
                        highPriorityEvents.remove(oldInfo);
                        mediumPriorityEvents.remove(oldInfo);
                        if (DEBUG) {
                            Log.d(TAG, "Dropping alert for recurring event ID:" + oldInfo.eventId
                                    + ", startTime:" + oldInfo.startMillis
                                    + " in favor of startTime:" + newInfo.startMillis);
                        }
                    } else {
                        // Skip duplicate reminders for the same event instance.
                        continue;
                    }
                }

                // TODO: Prioritize by "primary" calendar
                eventIds.put(eventId, newInfo);
                long highPriorityCutoff = currentTime -
                        getGracePeriodMs(beginTime, endTime, allDay);

                if (beginTimeAdjustedForAllDay > highPriorityCutoff) {
                    // High priority = future events or events that just started
                    highPriorityEvents.add(newInfo);
                } else if (allDay && tz != null && DateUtils.isToday(beginTimeAdjustedForAllDay)) {
                    // Medium priority = in progress all day events
                    mediumPriorityEvents.add(newInfo);
                } else {
                    lowPriorityEvents.add(newInfo);
                }
            }
        } finally {
            if (alertCursor != null) {
                alertCursor.close();
            }
        }
        return numFired;
    }

    /**
     * High priority cutoff should be 1/4 event duration or 15 min, whichever is longer.
     */
    private static long getGracePeriodMs(long beginTime, long endTime, boolean allDay) {
        if (allDay) {
            // We don't want all day events to be high priority for hours, so automatically
            // demote these after 15 min.
            return MIN_DEPRIORITIZE_GRACE_PERIOD_MS;
        } else {
            return Math.max(MIN_DEPRIORITIZE_GRACE_PERIOD_MS, ((endTime - beginTime) / 4));
        }
    }

    private static String getDigestTitle(ArrayList<NotificationInfo> events) {
        StringBuilder digestTitle = new StringBuilder();
        for (NotificationInfo eventInfo : events) {
            if (!TextUtils.isEmpty(eventInfo.eventName)) {
                if (digestTitle.length() > 0) {
                    digestTitle.append(", ");
                }
                digestTitle.append(eventInfo.eventName);
            }
        }
        return digestTitle.toString();
    }

    private static void postNotification(NotificationInfo info, String summaryText,
            Context context, boolean highPriority,
            NotificationMgr notificationMgr, int notificationId) {
        int priorityVal = Notification.PRIORITY_DEFAULT;
        if (highPriority) {
            priorityVal = Notification.PRIORITY_HIGH;
        }

        NotificationWrapper notification = AlertReceiver.makeExpandingNotification(context,
                info.eventName, summaryText, info.description, info.startMillis,
                info.endMillis, info.eventId, notificationId, priorityVal);

        // Post the notification.
        notificationMgr.notify(notificationId, notification);

        if (DEBUG) {
            Log.d(TAG, "Posting individual alarm notification, eventId:" + info.eventId
                    + ", notificationId:" + notificationId + ", newAlert: " + info.newAlert 
                    + (highPriority ? ", high-priority" : ""));
        }
    }

    static class NotificationInfo {
        String eventName;
        String location;
        String description;
        long startMillis;
        long endMillis;
        long eventId;
        boolean allDay;
        boolean newAlert;

        NotificationInfo(String eventName, String location, String description, long startMillis,
                long endMillis, long eventId, boolean allDay, boolean newAlert) {
            this.eventName = eventName;
            this.location = location;
            this.description = description;
            this.startMillis = startMillis;
            this.endMillis = endMillis;
            this.eventId = eventId;
            this.newAlert = newAlert;
            this.allDay = allDay;
        }
    }

    private void doTimeChanged() {
        ContentResolver cr = getContentResolver();
        // TODO Move this into Provider
        rescheduleMissedAlarms(cr, this, AlertUtils.createAlarmManager(this));
        updateAlertNotification(this);
    }

    private static final String SORT_ORDER_ALARMTIME_ASC =
            CalendarContract.CalendarAlerts.ALARM_TIME + " ASC";

    private static final String WHERE_RESCHEDULE_MISSED_ALARMS =
            CalendarContract.CalendarAlerts.STATE
            + "="
            + CalendarContract.CalendarAlerts.STATE_SCHEDULED
            + " AND "
            + CalendarContract.CalendarAlerts.ALARM_TIME
            + "<?"
            + " AND "
            + CalendarContract.CalendarAlerts.ALARM_TIME
            + ">?"
            + " AND "
            + CalendarContract.CalendarAlerts.END + ">=?";

    /**
     * Searches the CalendarAlerts table for alarms that should have fired but
     * have not and then reschedules them. This method can be called at boot
     * time to restore alarms that may have been lost due to a phone reboot.
     *
     * @param cr the ContentResolver
     * @param context the Context
     * @param manager the AlarmManager
     */
    private static final void rescheduleMissedAlarms(ContentResolver cr, Context context,
            AlarmManagerInterface manager) {
        // Get all the alerts that have been scheduled but have not fired
        // and should have fired by now and are not too old.
        long now = System.currentTimeMillis();
        long ancient = now - DateUtils.DAY_IN_MILLIS;
        String[] projection = new String[] {
            CalendarContract.CalendarAlerts.ALARM_TIME,
        };

        // TODO: construct an explicit SQL query so that we can add
        // "GROUPBY" instead of doing a sort and de-dup
        Cursor cursor = cr.query(CalendarAlerts.CONTENT_URI, projection,
                WHERE_RESCHEDULE_MISSED_ALARMS, (new String[] {
                        Long.toString(now), Long.toString(ancient), Long.toString(now)
                }), SORT_ORDER_ALARMTIME_ASC);
        if (cursor == null) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "missed alarms found: " + cursor.getCount());
        }

        try {
            long alarmTime = -1;

            while (cursor.moveToNext()) {
                long newAlarmTime = cursor.getLong(0);
                if (alarmTime != newAlarmTime) {
                    if (DEBUG) {
                        Log.w(TAG, "rescheduling missed alarm. alarmTime: " + newAlarmTime);
                    }
                    AlertUtils.scheduleAlarm(context, manager, newAlarmTime);
                    alarmTime = newAlarmTime;
                }
            }
        } finally {
            cursor.close();
        }
    }

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            processMessage(msg);
            jobFinished(mJobParameters, false);
        }
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (DEBUG) Log.d(TAG, "onStopJob " + params.getJobId());
        mServiceLooper.quit();
        return true;
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        if (DEBUG) Log.d(TAG, "onStartJob " + params.getJobId());
        mJobParameters = params;
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = params.getJobId();
        msg.obj = params.getExtras();
        mServiceHandler.sendMessage(msg);
        return true;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.d(TAG, "onCreate");
        HandlerThread thread = new HandlerThread("AlertJobService",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);

        // Flushes old fired alerts from internal storage, if needed.
        AlertUtils.flushOldAlertsFromInternalStorage(getApplication());
    }

    public static boolean scheduleUpdateNow(Context context, PersistableBundle extras) {
        if (DEBUG) Log.d(TAG, "scheduleUpdateNow");

        if (isPermissionEnabled(context)) {
            ComponentName component = new ComponentName(context, AlertJobService.class);
            JobInfo job = new JobInfo.Builder(ONCE_UPDATE_JOB_ID, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(1)
                .setOverrideDeadline(1)
                .setExtras(extras)
                .build();
            JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            jobScheduler.schedule(job);
            return true;
        } else {
            Log.e(TAG, "Blocked onStartCommand because of missing permissions");
            return false;
        }
    }

    private static boolean isPermissionEnabled(Context context) {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                    != PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.READ_CALENDAR)
                    != PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
        }
        return true;
    }
}
