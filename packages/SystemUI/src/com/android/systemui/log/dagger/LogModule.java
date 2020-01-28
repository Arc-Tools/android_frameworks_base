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

package com.android.systemui.log.dagger;

import android.content.ContentResolver;
import android.os.Build;
import android.os.Looper;

import com.android.systemui.DumpController;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.log.LogBuffer;
import com.android.systemui.log.LogcatEchoTracker;
import com.android.systemui.log.LogcatEchoTrackerDebug;
import com.android.systemui.log.LogcatEchoTrackerProd;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * Dagger module for providing instances of {@link LogBuffer}.
 */
@Module
public class LogModule {
    /** Provides a logging buffer for doze-related logs. */
    @Provides
    @Singleton
    @DozeLog
    public static LogBuffer provideDozeLogBuffer(
            LogcatEchoTrackerDebug bufferFilter,
            DumpController dumpController) {
        LogBuffer buffer = new LogBuffer("DozeLog", 100, 10, bufferFilter);
        buffer.attach(dumpController);
        return buffer;
    }

    /** Provides a logging buffer for all logs related to the data layer of notifications. */
    @Provides
    @Singleton
    @NotificationLog
    public static LogBuffer provideNotificationsLogBuffer(
            LogcatEchoTracker bufferFilter,
            DumpController dumpController) {
        LogBuffer buffer = new LogBuffer("NotifLog2", 1000, 10, bufferFilter);
        buffer.attach(dumpController);
        return buffer;
    }

    /** Allows logging buffers to be tweaked via adb on debug builds but not on prod builds. */
    @Provides
    @Singleton
    public static LogcatEchoTracker provideLogcatEchoTracker(
            ContentResolver contentResolver,
            @Main Looper looper) {
        if (Build.IS_DEBUGGABLE) {
            return LogcatEchoTrackerDebug.create(contentResolver, looper);
        } else {
            return new LogcatEchoTrackerProd();
        }
    }
}