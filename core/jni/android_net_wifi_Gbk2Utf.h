/*
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *    * Neither the name of The Linux Foundation nor
 *      the names of its contributors may be used to endorse or promote
 *      products derived from this software without specific prior written
 *      permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#include <utils/String8.h>
#include <utils/String16.h>
#include <pthread.h>
#include <unicode/ucnv.h>
#include <unicode/ucsdet.h>
#include <net/if.h>
#include <sys/socket.h>
#include <linux/wireless.h>
#include <utils/misc.h>
#include <utils/Log.h>

<<<<<<< HEAD:core/jni/android_net_wifi_Gbk2Utf.h
namespace android {
=======
import android.content.ComponentName;
import android.content.Intent;
>>>>>>> 2e727f0... Base: huge cleanup:core/java/com/android/internal/app/ActivityTrigger.java

struct accessPointObjectItem {
    String8 *ssid_utf8;
    String8 *ssid;
    struct  accessPointObjectItem *pNext;
};

extern void parseScanResults(String16& str, const char *reply);

extern void constructSsid(String16& str, const char *reply);

extern void constructEventSsid(char *eventstr);

extern jboolean setNetworkVariable(char *buf);

} //namespace android
