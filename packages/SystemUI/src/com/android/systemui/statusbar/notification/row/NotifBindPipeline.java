/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.row;

import android.util.ArrayMap;
import android.util.ArraySet;
import android.widget.FrameLayout;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.CancellationSignal;

import com.android.internal.statusbar.NotificationVisibility;
import com.android.systemui.statusbar.notification.NotificationEntryListener;
import com.android.systemui.statusbar.notification.NotificationEntryManager;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;
import com.android.systemui.statusbar.notification.collection.inflation.NotificationRowBinder;
import com.android.systemui.statusbar.notification.row.NotificationRowContentBinder.InflationFlag;

import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * {@link NotifBindPipeline} is responsible for converting notifications from their data form to
 * their actual inflated views. It is essentially a control class that composes notification view
 * binding logic (i.e. {@link BindStage}) in response to explicit bind requests. At the end of the
 * pipeline, the notification's bound views are guaranteed to be correct and up-to-date, and any
 * registered callbacks will be called.
 *
 * The pipeline ensures that a notification's top-level view and its content views are bound.
 * Currently, a notification's top-level view, the {@link ExpandableNotificationRow} is essentially
 * just a {@link FrameLayout} for various different content views that are switched in and out as
 * appropriate. These include a contracted view, expanded view, heads up view, and sensitive view on
 * keyguard. See {@link InflationFlag}. These content views themselves can have child views added
 * on depending on different factors. For example, notification actions and smart replies are views
 * that are dynamically added to these content views after they're inflated. Finally, aside from
 * the app provided content views, System UI itself also provides some content views that are shown
 * occasionally (e.g. {@link NotificationGuts}). Many of these are business logic specific views
 * and the requirements surrounding them may change over time, so the pipeline must handle
 * composing the logic as necessary.
 *
 * Note that bind requests do not only occur from add/updates from updates from the app. For
 * example, the user may make changes to device settings (e.g. sensitive notifications on lock
 * screen) or we may want to make certain optimizations for the sake of memory or performance (e.g
 * freeing views when not visible). Oftentimes, we also need to wait for these changes to complete
 * before doing something else (e.g. moving a notification to the top of the screen to heads up).
 * The pipeline thus handles bind requests from across the system and provides a way for
 * requesters to know when the change is propagated to the view.
 *
 * Right now, we only support one attached {@link BindStage} which just does all the binding but we
 * should eventually support multiple stages once content inflation is made more modular.
 * In particular, row inflation/binding, which is handled by {@link NotificationRowBinder} should
 * probably be moved here in the future as a stage. Right now, the pipeline just manages content
 * views and assumes that a row is given to it when it's inflated.
 */
@MainThread
@Singleton
public final class NotifBindPipeline {
    private final Map<NotificationEntry, BindEntry> mBindEntries = new ArrayMap<>();
    private BindStage mStage;

    @Inject
    NotifBindPipeline(NotificationEntryManager entryManager) {
        entryManager.addNotificationEntryListener(mEntryListener);
    }

    /**
     * Set the bind stage for binding notification row content.
     */
    public void setStage(
            BindStage stage) {
        mStage = stage;
        mStage.setBindRequestListener(this::onBindRequested);
    }

    /**
     * Start managing the row's content for a given notification.
     */
    public void manageRow(
            @NonNull NotificationEntry entry,
            @NonNull ExpandableNotificationRow row) {
        final BindEntry bindEntry = getBindEntry(entry);
        bindEntry.row = row;
        if (bindEntry.invalidated) {
            startPipeline(entry);
        }
    }

    private void onBindRequested(
            @NonNull NotificationEntry entry,
            @NonNull CancellationSignal signal,
            @Nullable BindCallback callback) {
        final BindEntry bindEntry = getBindEntry(entry);
        if (bindEntry == null) {
            // Invalidating views for a notification that is not active.
            return;
        }

        bindEntry.invalidated = true;

        // Put in new callback.
        if (callback != null) {
            final Set<BindCallback> callbacks = bindEntry.callbacks;
            callbacks.add(callback);
            signal.setOnCancelListener(() -> callbacks.remove(callback));
        }

        startPipeline(entry);
    }

    /**
     * Run the pipeline for the notification, ensuring all views are bound when finished. Call all
     * callbacks when the run finishes. If a run is already in progress, it is restarted.
     */
    private void startPipeline(NotificationEntry entry) {
        if (mStage == null) {
            throw new IllegalStateException("No stage was ever set on the pipeline");
        }

        final BindEntry bindEntry = mBindEntries.get(entry);
        final ExpandableNotificationRow row = bindEntry.row;
        if (row == null) {
            // Row is not managed yet but may be soon. Stop for now.
            return;
        }

        mStage.abortStage(entry, row);
        mStage.executeStage(entry, row, (en) -> onPipelineComplete(en));
    }

    private void onPipelineComplete(NotificationEntry entry) {
        final BindEntry bindEntry = getBindEntry(entry);

        bindEntry.invalidated = false;

        final Set<BindCallback> callbacks = bindEntry.callbacks;
        for (BindCallback cb : callbacks) {
            cb.onBindFinished(entry);
        }
        callbacks.clear();
    }

    //TODO: Move this to onManageEntry hook when we split that from add/remove
    private final NotificationEntryListener mEntryListener = new NotificationEntryListener() {
        @Override
        public void onPendingEntryAdded(NotificationEntry entry) {
            mBindEntries.put(entry, new BindEntry());
            mStage.createStageParams(entry);
        }

        @Override
        public void onEntryRemoved(NotificationEntry entry,
                @Nullable NotificationVisibility visibility,
                boolean removedByUser) {
            BindEntry bindEntry = mBindEntries.remove(entry);
            ExpandableNotificationRow row = bindEntry.row;
            if (row != null) {
                mStage.abortStage(entry, row);
            }
            mStage.deleteStageParams(entry);
        }
    };

    private @NonNull BindEntry getBindEntry(NotificationEntry entry) {
        final BindEntry bindEntry = mBindEntries.get(entry);
        if (bindEntry == null) {
            throw new IllegalStateException(
                    String.format("Attempting bind on an inactive notification. key: %s",
                            entry.getKey()));
        }
        return bindEntry;
    }

    /**
     * Interface for bind callback.
     */
    public interface BindCallback {
        /**
         * Called when all views are fully bound on the notification.
         */
        void onBindFinished(NotificationEntry entry);
    }

    private class BindEntry {
        public ExpandableNotificationRow row;
        public final Set<BindCallback> callbacks = new ArraySet<>();
        public boolean invalidated;
    }
}