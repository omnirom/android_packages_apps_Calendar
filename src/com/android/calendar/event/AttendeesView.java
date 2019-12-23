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

package com.android.calendar.event;

import com.android.calendar.CalendarEventModel.Attendee;
import com.android.calendar.ContactsAsyncHelper;
import com.android.calendar.R;
import com.android.calendar.Utils;
import com.android.calendar.event.EditEventHelper.AttendeeItem;
import com.android.common.Rfc822Validator;
import com.android.ex.chips.CircularImageView;
import com.android.colorpicker.ColorStateDrawable;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.CalendarContract.Attendees;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Identity;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.text.TextUtils;
import android.text.util.Rfc822Token;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;

public class AttendeesView extends LinearLayout implements View.OnClickListener {
    private static final String TAG = "AttendeesView";
    private static final boolean DEBUG = false;

    private static final int EMAIL_PROJECTION_CONTACT_ID_INDEX = 0;
    private static final int EMAIL_PROJECTION_CONTACT_LOOKUP_INDEX = 1;
    private static final int EMAIL_PROJECTION_PHOTO_ID_INDEX = 2;
    private static final int EMAIL_PROJECTION_PHOTO_THUMBNAIL_URI = 3; // String

    private static final String[] PROJECTION = new String[] {
        RawContacts.CONTACT_ID,     // 0
        Contacts.LOOKUP_KEY,        // 1
        Contacts.PHOTO_ID,          // 2
        Contacts.PHOTO_THUMBNAIL_URI, // 3
    };

    private static class PhotoQuery {
        public static final String[] PROJECTION = {
            Photo.PHOTO
        };
        public static final int BUFFER_SIZE = 1024*16;
        public static final int PHOTO = 0;
    }

    private final Context mContext;
    private final LayoutInflater mInflater;
    private final PresenceQueryHandler mPresenceQueryHandler;
    private final Drawable mDefaultBadge;

    // TextView shown at the top of each type of attendees
    // e.g.
    // Yes  <-- divider
    // example_for_yes <exampleyes@example.com>
    // No <-- divider
    // example_for_no <exampleno@example.com>
    private final CharSequence[] mEntries;
    private final TextView mDivider;
    private final int mNoResponseOverlay;
    private final int mDeclinedOverlay;
    private final int mAcceptedOverlay;
    private Rfc822Validator mValidator;
    private static TypedArray sColors;

    // Number of attendees responding or not responding.
    private int mYes;
    private int mNo;
    private int mMaybe;
    private int mNoResponse;

    // Cache for loaded photos
    HashMap<String, Drawable> mRecycledPhotos;

    public AttendeesView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPresenceQueryHandler = new PresenceQueryHandler(context.getContentResolver());

        final Resources resources = context.getResources();
        mDefaultBadge = resources.getDrawable(R.drawable.ic_contact_picture);
        mNoResponseOverlay = resources.getColor(R.color.noresponse_attendee_overlay);
        mDeclinedOverlay = resources.getColor(R.color.declined_attendee_overlay);
        mAcceptedOverlay = resources.getColor(R.color.accepted_attendee_overlay);

        // Create dividers between groups of attendees (accepted, declined, etc...)
        mEntries = resources.getTextArray(R.array.response_labels_new);
        mDivider = (TextView) mInflater.inflate(R.layout.event_info_label, this, false);
    }

    public void setRfc822Validator(Rfc822Validator validator) {
        mValidator = validator;
    }

    private void updateDividerViewLabel() {
        StringBuffer buffer = new StringBuffer();
        if (mNoResponse != 0) {
            buffer.append(mNoResponse + " " + mEntries[0] + ", ");
        }
        if (mYes != 0) {
            buffer.append(mYes + " " + mEntries[1] + ", ");
        }
        if (mMaybe != 0) {
            buffer.append(mMaybe + " " + mEntries[2] + ", ");
        }
        if (mNo != 0) {
            buffer.append(mNo + " " + mEntries[3] + ", ");
        }
        if (buffer.length() > 0) {
            buffer.delete(buffer.length() - 2, buffer.length());
            mDivider.setText(buffer.toString());
            mDivider.setVisibility(View.VISIBLE);
        } else {
            mDivider.setVisibility(View.GONE);
        }
    }


    /**
     * Inflates a layout for a given attendee view and set up each element in it, and returns
     * the constructed View object. The object is also stored in {@link AttendeeItem#mView}.
     */
    private View constructAttendeeView(AttendeeItem item) {
        item.mView = mInflater.inflate(R.layout.contact_list_item_view, null);
        return updateAttendeeView(item);
    }

    /**
     * Set up each element in {@link AttendeeItem#mView} using the latest information. View
     * object is reused.
     */
    private View updateAttendeeView(AttendeeItem item) {
        final Attendee attendee = item.mAttendee;
        final View view = item.mView;
        final TextView nameView = (TextView) view.findViewById(android.R.id.title);
        final TextView emailView = (TextView) view.findViewById(android.R.id.text1);
        view.findViewById(R.id.chip_indicator_text).setVisibility(View.GONE);
        view.findViewById(android.R.id.icon1).setVisibility(View.GONE);

        if (TextUtils.isEmpty(attendee.mName)) {
            nameView.setVisibility(View.GONE);
            emailView.setText(attendee.mEmail);
        } else {
            nameView.setText(attendee.mName);
            emailView.setText(attendee.mEmail);
        }

        CircularImageView badgeView = view.findViewById(android.R.id.icon);
        ImageView statusCircle = view.findViewById(R.id.status_color_circle);

        Drawable badge = null;
        // Search for photo in recycled photos
        if (mRecycledPhotos != null) {
            badge = mRecycledPhotos.get(item.mAttendee.mEmail);
        }
        if (badge != null) {
            item.mBadge = badge;
        }
        badgeView.setImageDrawable(item.mBadge);
        statusCircle.setVisibility(View.GONE);
        Drawable[] colorDrawable = new Drawable[] {
                    getContext().getResources().getDrawable(R.drawable.calendar_color_oval) };

        if (item.mAttendee.mStatus == Attendees.ATTENDEE_STATUS_NONE) {
            statusCircle.setVisibility(View.VISIBLE);
            statusCircle.setImageDrawable(new ColorStateDrawable(colorDrawable, mNoResponseOverlay));
        }
        if (item.mAttendee.mStatus == Attendees.ATTENDEE_STATUS_DECLINED) {
            statusCircle.setVisibility(View.VISIBLE);
            statusCircle.setImageDrawable(new ColorStateDrawable(colorDrawable, mDeclinedOverlay));
        }
        if (item.mAttendee.mStatus == Attendees.ATTENDEE_STATUS_ACCEPTED ||
                item.mAttendee.mStatus == Attendees.ATTENDEE_STATUS_TENTATIVE) {
            statusCircle.setVisibility(View.VISIBLE);
            statusCircle.setImageDrawable(new ColorStateDrawable(colorDrawable, mAcceptedOverlay));
        }
        return view;
    }

    public boolean contains(Attendee attendee) {
        final int size = getChildCount();
        for (int i = 0; i < size; i++) {
            final View view = getChildAt(i);
            if (view instanceof TextView) { // divider
                continue;
            }
            AttendeeItem attendeeItem = (AttendeeItem) view.getTag();
            if (TextUtils.equals(attendee.mEmail, attendeeItem.mAttendee.mEmail)) {
                return true;
            }
        }
        return false;
    }

    public void clearAttendees() {

        // Before clearing the views, save all the badges. The updateAtendeeView will use the saved
        // photo instead of the default badge thus prevent switching between the two while the
        // most current photo is loaded in the background.
        mRecycledPhotos = new HashMap<String, Drawable>  ();
        final int size = getChildCount();
        for (int i = 0; i < size; i++) {
            final View view = getChildAt(i);
            if (view instanceof TextView) { // divider
                continue;
            }
            AttendeeItem attendeeItem = (AttendeeItem) view.getTag();
            mRecycledPhotos.put(attendeeItem.mAttendee.mEmail, attendeeItem.mBadge);
        }

        removeAllViews();
        mYes = 0;
        mNo = 0;
        mMaybe = 0;
        mNoResponse = 0;
    }

    private void addOneAttendee(Attendee attendee) {
        if (contains(attendee)) {
            return;
        }
        final AttendeeItem item = new AttendeeItem(attendee, mDefaultBadge);
        final int status = attendee.mStatus;
        switch (status) {
            case Attendees.ATTENDEE_STATUS_ACCEPTED: {
                mYes++;
                break;
            }
            case Attendees.ATTENDEE_STATUS_DECLINED: {
                mNo++;
                break;
            }
            case Attendees.ATTENDEE_STATUS_TENTATIVE: {
                mMaybe++;
                break;
            }
            default: {
                mNoResponse++;
                break;
            }
        }
        if (getChildCount() == 0) {
            addView(mDivider);
        }
        updateDividerViewLabel();

        final View view = constructAttendeeView(item);
        view.setTag(item);
        addView(view);

        Uri uri;
        String selection = null;
        String[] selectionArgs = null;
        if (attendee.mIdentity != null && attendee.mIdNamespace != null) {
            // Query by identity + namespace
            uri = Data.CONTENT_URI;
            selection = Data.MIMETYPE + "=? AND " + Identity.IDENTITY + "=? AND " +
                    Identity.NAMESPACE + "=?";
            selectionArgs = new String[] {Identity.CONTENT_ITEM_TYPE, attendee.mIdentity,
                    attendee.mIdNamespace};
        } else {
            // Query by email
            uri = Uri.withAppendedPath(Email.CONTENT_LOOKUP_URI, Uri.encode(attendee.mEmail));
        }

        mPresenceQueryHandler.startQuery(item.mUpdateCounts + 1, item, uri, PROJECTION, selection,
                selectionArgs, null);
    }

    public void addAttendees(ArrayList<Attendee> attendees) {
        synchronized (this) {
            for (final Attendee attendee : attendees) {
                addOneAttendee(attendee);
            }
        }
    }

    public void addAttendees(HashMap<String, Attendee> attendees) {
        synchronized (this) {
            for (final Attendee attendee : attendees.values()) {
                addOneAttendee(attendee);
            }
        }
    }

    public void addAttendees(String attendees) {
        final LinkedHashSet<Rfc822Token> addresses =
                EditEventHelper.getAddressesFromList(attendees, mValidator);
        synchronized (this) {
            for (final Rfc822Token address : addresses) {
                final Attendee attendee = new Attendee(address.getName(), address.getAddress());
                if (TextUtils.isEmpty(attendee.mName)) {
                    attendee.mName = attendee.mEmail;
                }
                addOneAttendee(attendee);
            }
        }
    }

    // TODO put this into a Loader for auto-requeries
    private class PresenceQueryHandler extends AsyncQueryHandler {
        public PresenceQueryHandler(ContentResolver cr) {
            super(cr);
        }

        @Override
        protected void onQueryComplete(int queryIndex, Object cookie, Cursor cursor) {
            if (cursor == null || cookie == null) {
                if (DEBUG) {
                    Log.d(TAG, "onQueryComplete: cursor=" + cursor + ", cookie=" + cookie);
                }
                return;
            }

            final AttendeeItem item = (AttendeeItem)cookie;
            try {
                if (item.mUpdateCounts < queryIndex) {
                    item.mUpdateCounts = queryIndex;
                    if (cursor.moveToFirst()) {
                        final long contactId = cursor.getLong(EMAIL_PROJECTION_CONTACT_ID_INDEX);
                        final Uri contactUri =
                                ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);

                        final String lookupKey =
                                cursor.getString(EMAIL_PROJECTION_CONTACT_LOOKUP_INDEX);
                        item.mContactLookupUri = Contacts.getLookupUri(contactId, lookupKey);
                        item.mBadge = getResources().getDrawable(R.drawable.ic_contact_picture);
                        final long photoId = cursor.getLong(EMAIL_PROJECTION_PHOTO_ID_INDEX);
                        final String photoThumbnailUriStr = cursor.getString(EMAIL_PROJECTION_PHOTO_THUMBNAIL_URI);
                        if (photoThumbnailUriStr != null) {
                            fetchPhotoAsync(Uri.parse(photoThumbnailUriStr), item,
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            updateAttendeeView(item);
                                        }
                                    });
                        } else if (photoId > 0) {
                            // If we found a picture, start the async loading
                            // Query for this contacts picture
                            ContactsAsyncHelper.retrieveContactPhotoAsync(
                                    mContext, item, new Runnable() {
                                        @Override
                                        public void run() {
                                            updateAttendeeView(item);
                                        }
                                    }, contactUri);
                        } else {
                            String letterString = TextUtils.isEmpty(item.mAttendee.mName) ?
                                    item.mAttendee.mEmail : item.mAttendee.mName;
                            Bitmap b = Utils.renderLetterTile(mContext, letterString, lookupKey);
                            item.mBadge = new BitmapDrawable(getResources(), b);
                            updateAttendeeView(item);
                        }
                    } else {
                        // Contact not found.  For real emails, keep the QuickContactBadge with
                        // its Email address set, so that the user can create a contact by tapping.
                        item.mContactLookupUri = null;
                        if (!Utils.isValidEmail(item.mAttendee.mEmail)) {
                            item.mAttendee.mEmail = null;
                            updateAttendeeView(item);
                        }
                    }
                }
            } finally {
                cursor.close();
            }
        }
    }

    public Attendee getItem(int index) {
        final View view = getChildAt(index);
        if (view instanceof TextView) { // divider
            return null;
        }
        return ((AttendeeItem) view.getTag()).mAttendee;
    }

    @Override
    public void onClick(View view) {
    }

    private void fetchPhotoAsync(final Uri photoThumbnailUri, final AttendeeItem item,
            final Runnable callback) {
        final AsyncTask<Void, Void, byte[]> photoLoadTask = new AsyncTask<Void, Void, byte[]>() {
            @Override
            protected byte[] doInBackground(Void... params) {
                // First try running a query. Images for local contacts are
                // loaded by sending a query to the ContactsProvider.
                final Cursor photoCursor = mContext.getContentResolver().query(
                        photoThumbnailUri, PhotoQuery.PROJECTION, null, null, null);
                if (photoCursor != null) {
                    try {
                        if (photoCursor.moveToFirst()) {
                            return photoCursor.getBlob(PhotoQuery.PHOTO);
                        }
                    } finally {
                        photoCursor.close();
                    }
                } else {
                    // If the query fails, try streaming the URI directly.
                    // For remote directory images, this URI resolves to the
                    // directory provider and the images are loaded by sending
                    // an openFile call to the provider.
                    try {
                        InputStream is = mContext.getContentResolver().openInputStream(
                                photoThumbnailUri);
                        if (is != null) {
                            byte[] buffer = new byte[PhotoQuery.BUFFER_SIZE];
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            try {
                                int size;
                                while ((size = is.read(buffer)) != -1) {
                                    baos.write(buffer, 0, size);
                                }
                            } finally {
                                is.close();
                            }
                            return baos.toByteArray();
                        }
                    } catch (IOException ex) {
                        // ignore
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(final byte[] photoBytes) {
                if (photoBytes != null && photoBytes.length > 0) {
                    final Bitmap photo = BitmapFactory.decodeByteArray(photoBytes, 0,
                            photoBytes.length);
                    item.mBadge = new BitmapDrawable(getResources(), photo);
                    callback.run();
                }
            }
        };
        photoLoadTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }
}
