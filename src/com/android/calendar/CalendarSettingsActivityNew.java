/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.util.Log;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.Preference.OnPreferenceClickListener;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.calendar.selectcalendars.SelectCalendarsSyncFragment;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CalendarSettingsActivityNew extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE);
        actionBar.setTitle(R.string.preferences_title);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.settings_title_bar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected (MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (getFragmentManager().getBackStackEntryCount() > 0) {
                    getFragmentManager().popBackStack();
                } else {
                    finish();
                }
                return true;
            case R.id.action_add_account:
                Intent nextIntent = new Intent(Settings.ACTION_ADD_ACCOUNT);
                final String[] array = { "com.android.calendar" };
                nextIntent.putExtra(Settings.EXTRA_AUTHORITIES, array);
                nextIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(nextIntent);
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(R.id.content, new SettingsFragment()).commit();
    }


    public static class SettingsFragment extends PreferenceFragment implements OnPreferenceChangeListener {
        public static final String KEY_GENERAL_PREFERENCES = "menu_general_preferences";

        private Account[] mAccounts;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.calendar_settings);

            final PreferenceScreen preferenceScreen = getPreferenceScreen();
            mAccounts = AccountManager.get(getActivity()).getAccounts();
            if (mAccounts != null) {
                int length = mAccounts.length;
                for (int i = 0; i < length; i++) {
                    Account acct = mAccounts[i];
                    if (ContentResolver.getIsSyncable(acct, CalendarContract.AUTHORITY) > 0) {
                        Preference accountPreference = new Preference(preferenceScreen.getContext());
                        accountPreference.setPersistent(false);
                        accountPreference.setTitle(acct.name);
                        accountPreference.setKey("account_" + i);
                        accountPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(Preference preference) {
                                String key = preference.getKey();
                                int keyNum = Integer.valueOf(key.split("_")[1]);
                                Account acct = mAccounts[keyNum];
                                Bundle args = new Bundle();
                                args.putString(Calendars.ACCOUNT_NAME, acct.name);
                                args.putString(Calendars.ACCOUNT_TYPE, acct.type);
                                getActivity().getFragmentManager().beginTransaction().replace(R.id.content, new SelectCalendarsSyncFragment(args)).addToBackStack(null).commit();
                                return true;
                            }
                        });
                        preferenceScreen.addPreference(accountPreference);
                    }
                }
            }
        }
        
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            return true;
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            final String key = preference.getKey();
            if (KEY_GENERAL_PREFERENCES.equals(key)) {
                getActivity().getFragmentManager().beginTransaction().replace(R.id.content, new GeneralPreferences()).addToBackStack(null).commit();
                return true;
            } else {
                return super.onPreferenceTreeClick(preference);
            }
        }
    }
}
