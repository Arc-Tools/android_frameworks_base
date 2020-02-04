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

#define LOG_TAG "StatsPullAtomService"

#include <jni.h>
#include <log/log.h>
#include <nativehelper/JNIHelp.h>
#include <stats_event.h>
#include <stats_pull_atom_callback.h>
#include <statslog.h>

#include "stats/PowerStatsPuller.h"
#include "stats/SubsystemSleepStatePuller.h"

namespace android {

static server::stats::PowerStatsPuller gPowerStatsPuller;
static server::stats::SubsystemSleepStatePuller gSubsystemSleepStatePuller;

static status_pull_atom_return_t onDevicePowerMeasurementCallback(int32_t atom_tag,
                                                                  pulled_stats_event_list* data,
                                                                  void* cookie) {
    return gPowerStatsPuller.Pull(atom_tag, data);
}

static status_pull_atom_return_t subsystemSleepStateCallback(int32_t atom_tag,
                                                             pulled_stats_event_list* data,
                                                             void* cookie) {
    return gSubsystemSleepStatePuller.Pull(atom_tag, data);
}

static void nativeInit(JNIEnv* env, jobject javaObject) {
    // on device power measurement
    gPowerStatsPuller = server::stats::PowerStatsPuller();
    register_stats_pull_atom_callback(android::util::ON_DEVICE_POWER_MEASUREMENT,
                                      onDevicePowerMeasurementCallback,
                                      /* metadata= */ nullptr,
                                      /* cookie= */ nullptr);

    // subsystem sleep state
    gSubsystemSleepStatePuller = server::stats::SubsystemSleepStatePuller();
    register_stats_pull_atom_callback(android::util::SUBSYSTEM_SLEEP_STATE,
                                      subsystemSleepStateCallback,
                                      /* metadata= */ nullptr,
                                      /* cookie= */ nullptr);
}

static const JNINativeMethod sMethods[] = {{"nativeInit", "()V", (void*)nativeInit}};

int register_android_server_stats_pull_StatsPullAtomService(JNIEnv* env) {
    int res = jniRegisterNativeMethods(env, "com/android/server/stats/pull/StatsPullAtomService",
                                       sMethods, NELEM(sMethods));
    if (res < 0) {
        ALOGE("failed to register native methods");
    }
    return res;
}

} // namespace android