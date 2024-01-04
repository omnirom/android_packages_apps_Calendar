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

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

public class CalendarApplication extends Application {
    private static final String REFRESH_CHANNEL_ID = "refresh";
    public static final String REMINDER_CHANNEL_ID = "reminder";

    @Override
    public void onCreate() {
        super.onCreate();

        /*
         * Ensure the default values are set for any receiver, activity,
         * service, etc. of Calendar
         */
        GeneralPreferences.setDefaultValues(this);

        // Save the version number, for upcoming 'What's new' screen.  This will be later be
        // moved to that implementation.
        Utils.setSharedPreference(this, GeneralPreferences.KEY_VERSION,
                Utils.getVersionCode(this));

        // create notificatioon channels
        makeNotificationChannels(this);
    }

    private void makeNotificationChannels(Context context) {
        final NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(REFRESH_CHANNEL_ID) != null) {
            nm.deleteNotificationChannel(REFRESH_CHANNEL_ID);
        }
        /*final NotificationChannel channelRefresh =
                new NotificationChannel(
                        REFRESH_CHANNEL_ID,
                        context.getString(R.string.notification_channel_refresh),
                        NotificationManager.IMPORTANCE_LOW);*/

        final NotificationChannel channelReminder =
                new NotificationChannel(
                        REMINDER_CHANNEL_ID,
                        context.getString(R.string.notification_channel_reminder),
                        NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(channelReminder);
    }
}
