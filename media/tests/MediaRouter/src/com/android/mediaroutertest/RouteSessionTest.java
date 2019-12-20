/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.mediaroutertest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.RouteSessionInfo;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class RouteSessionTest {
    private static final String TEST_PACKAGE_NAME = "com.android.mediaroutertest";
    private static final String TEST_CONTROL_CATEGORY = "com.android.mediaroutertest.category";

    private static final String TEST_ROUTE_ID1 = "route_id1";

    @Test
    public void testValidity() {
        RouteSessionInfo emptyPackageSession = new RouteSessionInfo.Builder(1,
                "",
                TEST_CONTROL_CATEGORY)
                .addSelectedRoute(TEST_ROUTE_ID1)
                .build();
        RouteSessionInfo emptyCategorySession = new RouteSessionInfo.Builder(1,
                TEST_PACKAGE_NAME, "")
                .addSelectedRoute(TEST_ROUTE_ID1)
                .build();

        RouteSessionInfo emptySelectedRouteSession = new RouteSessionInfo.Builder(1,
                TEST_PACKAGE_NAME, TEST_CONTROL_CATEGORY)
                .build();

        RouteSessionInfo validSession = new RouteSessionInfo.Builder(emptySelectedRouteSession)
                .addSelectedRoute(TEST_ROUTE_ID1)
                .build();

        assertFalse(emptySelectedRouteSession.isValid());
        assertFalse(emptyPackageSession.isValid());
        assertFalse(emptyCategorySession.isValid());
        assertTrue(validSession.isValid());
    }
}