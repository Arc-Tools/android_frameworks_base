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

package com.android.keyguard;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.AdditionalAnswers.answerVoid;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.admin.IKeyguardCallback;
import android.app.admin.IKeyguardClient;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.RemoteException;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.testing.TestableLooper.RunWithLooper;
import android.testing.ViewUtils;
import android.view.SurfaceControl;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@RunWithLooper
@RunWith(AndroidTestingRunner.class)
@SmallTest
public class AdminSecondaryLockScreenControllerTest extends SysuiTestCase {

    private static final int TARGET_USER_ID = KeyguardUpdateMonitor.getCurrentUser();

    private AdminSecondaryLockScreenController mTestController;
    private ComponentName mComponentName;
    private Intent mServiceIntent;
    private TestableLooper mTestableLooper;
    private ViewGroup mParent;

    @Mock
    private Handler mHandler;
    @Mock
    private IKeyguardClient.Stub mKeyguardClient;
    @Mock
    private KeyguardSecurityCallback mKeyguardCallback;
    @Mock
    private KeyguardUpdateMonitor mUpdateMonitor;
    @Spy
    private StubTransaction mTransaction;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mParent = spy(new FrameLayout(mContext));
        ViewUtils.attachView(mParent);

        mTestableLooper = TestableLooper.get(this);
        mComponentName = new ComponentName(mContext, "FakeKeyguardClient.class");
        mServiceIntent = new Intent().setComponent(mComponentName);

        mContext.addMockService(mComponentName, mKeyguardClient);
        // Have Stub.asInterface return the mocked interface.
        when(mKeyguardClient.queryLocalInterface(anyString())).thenReturn(mKeyguardClient);
        when(mKeyguardClient.asBinder()).thenReturn(mKeyguardClient);

        mTestController = new AdminSecondaryLockScreenController(
                mContext, mParent, mUpdateMonitor, mKeyguardCallback, mHandler, mTransaction);
    }

    @Test
    public void testShow() throws Exception {
        doAnswer(invocation -> {
            IKeyguardCallback callback = (IKeyguardCallback) invocation.getArguments()[1];
            callback.onSurfaceControlCreated(new SurfaceControl());
            return null;
        }).when(mKeyguardClient).onSurfaceReady(any(), any(IKeyguardCallback.class));

        mTestController.show(mServiceIntent);

        verifySurfaceReady();
        verify(mTransaction).reparent(any(), any());
        assertThat(mContext.isBound(mComponentName)).isTrue();
    }

    @Test
    public void testShow_dismissedByCallback() throws Exception {
        doAnswer(invocation -> {
            IKeyguardCallback callback = (IKeyguardCallback) invocation.getArguments()[1];
            callback.onDismiss();
            return null;
        }).when(mKeyguardClient).onSurfaceReady(any(), any(IKeyguardCallback.class));

        mTestController.show(mServiceIntent);

        verifyViewDismissed(verifySurfaceReady());
    }

    @Test
    public void testHide() throws Exception {
        // Show the view first, then hide.
        doAnswer(invocation -> {
            IKeyguardCallback callback = (IKeyguardCallback) invocation.getArguments()[1];
            callback.onSurfaceControlCreated(new SurfaceControl());
            return null;
        }).when(mKeyguardClient).onSurfaceReady(any(), any(IKeyguardCallback.class));

        mTestController.show(mServiceIntent);
        SurfaceView v = verifySurfaceReady();

        mTestController.hide();
        verify(mParent).removeView(v);
        assertThat(mContext.isBound(mComponentName)).isFalse();
    }

    @Test
    public void testHide_notShown() throws Exception {
        mTestController.hide();
        // Nothing should happen if trying to hide when the view isn't attached yet.
        verify(mParent, never()).removeView(any(SurfaceView.class));
    }

    @Test
    public void testDismissed_onSurfaceReady_RemoteException() throws Exception {
        doThrow(new RemoteException()).when(mKeyguardClient)
                .onSurfaceReady(any(), any(IKeyguardCallback.class));

        mTestController.show(mServiceIntent);

        verifyViewDismissed(verifySurfaceReady());
    }

    @Test
    public void testDismissed_onSurfaceReady_timeout() throws Exception {
        // Mocked KeyguardClient never handles the onSurfaceReady, so the operation times out,
        // resulting in the view being dismissed.
        doAnswer(answerVoid(Runnable::run)).when(mHandler)
                .postDelayed(any(Runnable.class), anyLong());

        mTestController.show(mServiceIntent);

        verifyViewDismissed(verifySurfaceReady());
    }

    private SurfaceView verifySurfaceReady() throws Exception {
        mTestableLooper.processAllMessages();
        ArgumentCaptor<SurfaceView> captor = ArgumentCaptor.forClass(SurfaceView.class);
        verify(mParent).addView(captor.capture());

        mTestableLooper.processAllMessages();
        verify(mKeyguardClient).onSurfaceReady(any(), any(IKeyguardCallback.class));
        return captor.getValue();
    }

    private void verifyViewDismissed(SurfaceView v) throws Exception {
        verify(mParent).removeView(v);
        verify(mKeyguardCallback).dismiss(true, TARGET_USER_ID);
        assertThat(mContext.isBound(mComponentName)).isFalse();
    }

    /**
     * Stubbed {@link SurfaceControl.Transaction} class that can be used when unit testing to
     * avoid calls to native code.
     */
    private class StubTransaction extends SurfaceControl.Transaction {
        @Override
        public void apply() {
        }

        @Override
        public SurfaceControl.Transaction reparent(SurfaceControl sc, SurfaceControl newParent) {
            return this;
        }
    }
}