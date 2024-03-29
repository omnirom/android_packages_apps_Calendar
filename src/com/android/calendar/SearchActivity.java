/*
 * Copyright (C) 2010 The Android Open Source Project
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

import static android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME;
import static android.provider.CalendarContract.EXTRA_EVENT_END_TIME;

import android.app.Activity;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CalendarContract.Events;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnActionExpandListener;
import android.widget.EditText;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.CalendarController.EventType;
import com.android.calendar.CalendarController.ViewType;
import com.android.calendar.agenda.AgendaFragment;

public class SearchActivity extends AppCompatActivity implements CalendarController.EventHandler,
        SearchView.OnQueryTextListener, OnActionExpandListener {

    private static final String TAG = SearchActivity.class.getSimpleName();

    private static final boolean DEBUG = false;

    private static final int HANDLER_KEY = 0;

    protected static final String BUNDLE_KEY_RESTORE_TIME = "key_restore_time";

    protected static final String BUNDLE_KEY_RESTORE_SEARCH_QUERY =
        "key_restore_search_query";

    private CalendarController mController;

    private String mQuery;

    private SearchView mSearchView;

    private Handler mHandler;
    private ContentResolver mContentResolver;

    private final ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            eventsChanged();
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        // This needs to be created before setContentView
        mController = CalendarController.getInstance(this);
        mHandler = new Handler();

        setContentView(R.layout.search);

        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

        mContentResolver = getContentResolver();

        getSupportActionBar()
                .setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_HOME_AS_UP);

        // Must be the first to register because this activity can modify the
        // list of event handlers in it's handle method. This affects who the
        // rest of the handlers the controller dispatches to are.
        mController.registerEventHandler(HANDLER_KEY, this);

        long millis = 0;
        if (icicle != null) {
            // Returns 0 if key not found
            millis = icicle.getLong(BUNDLE_KEY_RESTORE_TIME);
            if (DEBUG) {
                Log.v(TAG, "Restore value from icicle: " + millis);
            }
        }
        if (millis == 0) {
            // Didn't find a time in the bundle, look in intent or current time
            millis = Utils.timeFromIntentInMillis(getIntent());
        }

        Intent intent = getIntent();
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query;
            if (icicle != null && icicle.containsKey(BUNDLE_KEY_RESTORE_SEARCH_QUERY)) {
                query = icicle.getString(BUNDLE_KEY_RESTORE_SEARCH_QUERY);
            } else {
                query = intent.getStringExtra(SearchManager.QUERY);
            }
            initFragments(millis, query);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mController.deregisterAllEventHandlers();
        CalendarController.removeInstance(this);
    }

    private void initFragments(long timeMillis, String query) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction ft = fragmentManager.beginTransaction();

        AgendaFragment searchResultsFragment = new AgendaFragment(timeMillis, true);
        ft.replace(R.id.search_results, searchResultsFragment);
        mController.registerEventHandler(R.id.search_results, searchResultsFragment);

        ft.commit();
        Time t = new Time();
        t.set(timeMillis);
        search(query, t);
    }

    private void showEventInfo(EventInfo event) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri eventUri = ContentUris.withAppendedId(Events.CONTENT_URI, event.id);
        intent.setData(eventUri);
        intent.setClass(this, EventInfoActivity.class);
        intent.putExtra(EXTRA_EVENT_BEGIN_TIME,
                event.startTime != null ? event.startTime.toMillis(true) : -1);
        intent.putExtra(
                EXTRA_EVENT_END_TIME, event.endTime != null ? event.endTime.toMillis(true) : -1);
        startActivity(intent);
    }

    private void search(String searchQuery, Time goToTime) {
        EventInfo searchEventInfo = new EventInfo();
        searchEventInfo.eventType = EventType.SEARCH;
        searchEventInfo.query = searchQuery;
        searchEventInfo.viewType = ViewType.AGENDA;
        if (goToTime != null) {
            searchEventInfo.startTime = goToTime;
        }
        mController.sendEvent(this, searchEventInfo);
        mQuery = searchQuery;
        if (mSearchView != null) {
            mSearchView.setQuery(mQuery, false);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.search_title_bar, menu);

        MenuItem item = menu.findItem(R.id.action_search);
        item.expandActionView();
        item.setOnActionExpandListener(this);
        mSearchView = (SearchView) MenuItemCompat.getActionView(item);
        EditText searchPlate = (EditText) mSearchView.findViewById(androidx.appcompat.R.id.search_src_text);
        searchPlate.setHint(R.string.search_event_hint);
        mSearchView.setOnQueryTextListener(this);
        mSearchView.setQuery(mQuery, false);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // From the Android Dev Guide: "It's important to note that when
        // onNewIntent(Intent) is called, the Activity has not been restarted,
        // so the getIntent() method will still return the Intent that was first
        // received with onCreate(). This is why setIntent(Intent) is called
        // inside onNewIntent(Intent) (just in case you call getIntent() at a
        // later time)."
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            search(query, null);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(BUNDLE_KEY_RESTORE_TIME, mController.getTime());
        outState.putString(BUNDLE_KEY_RESTORE_SEARCH_QUERY, mQuery);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mContentResolver.registerContentObserver(Events.CONTENT_URI, true, mObserver);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mContentResolver.unregisterContentObserver(mObserver);
    }

    @Override
    public void eventsChanged() {
        // dont update on events - keep search result list static
        //mController.sendEvent(this, EventType.EVENTS_CHANGED, null, null, -1, ViewType.CURRENT);
    }

    @Override
    public long getSupportedEventTypes() {
        return EventType.VIEW_EVENT;
    }

    @Override
    public void handleEvent(EventInfo event) {
        long endTime = (event.endTime == null) ? -1 : event.endTime.toMillis(false);
        if (event.eventType == EventType.VIEW_EVENT) {
            showEventInfo(event);
        }
    }

    @Override
    public boolean onQueryTextChange(String query) {
        mQuery = query;
        mController.sendEvent(this, EventType.SEARCH, null, null, -1, ViewType.CURRENT, 0, query,
                    getComponentName());
        return false;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onMenuItemActionExpand(MenuItem item) {
        return true;
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem item) {
        finish();
        return false;
    }
}
