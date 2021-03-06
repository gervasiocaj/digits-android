/*
 * Copyright (C) 2015 Twitter, Inc.
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
 *
 */

package com.digits.sdk.android;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.widget.TextView;

import com.twitter.sdk.android.core.Result;

import org.mockito.ArgumentCaptor;

import java.net.HttpURLConnection;
import java.util.ArrayList;

import retrofit.client.Header;
import retrofit.client.Response;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConfirmationCodeControllerTests extends
        DigitsControllerTests<ConfirmationCodeController> {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        controller = new DummyConfirmationCodeController(resultReceiver, sendButton,
                resendButton, callMeButton, phoneEditText, PHONE_WITH_COUNTRY_CODE, sessionManager,
                digitsClient, errors, new ActivityClassManagerImp(), scribeService, false,
                timerTextView);
    }

    public void testExecuteRequest_successAndMailRequestDisabled() throws Exception {
        final DigitsCallback callback = executeRequest();
        final Response response = new Response(TWITTER_URL, HttpURLConnection.HTTP_ACCEPTED, "",
                new ArrayList<Header>(), null);
        final DigitsUser user = new DigitsUser(USER_ID, "");
        callback.success(user, response);
        verify(scribeService).success();
        verify(sessionManager).setActiveSession(any(DigitsSession.class));
        verify(sendButton).showFinish();
        final ArgumentCaptor<Runnable> runnableArgumentCaptor = ArgumentCaptor.forClass
                (Runnable.class);
        verify(phoneEditText).postDelayed(runnableArgumentCaptor.capture(),
                eq(DigitsControllerImpl.POST_DELAY_MS));
        final Runnable runnable = runnableArgumentCaptor.getValue();
        runnable.run();

        final ArgumentCaptor<Bundle> bundleArgumentCaptor = ArgumentCaptor.forClass(Bundle.class);
        verify(resultReceiver).send(eq(LoginResultReceiver.RESULT_OK),
                bundleArgumentCaptor.capture());
        assertEquals(PHONE_WITH_COUNTRY_CODE, bundleArgumentCaptor.getValue().getString
                (DigitsClient.EXTRA_PHONE));
    }

    public void testExecuteRequest_successAndMailRequestEnabled() throws Exception {
        controller = new DummyConfirmationCodeController(resultReceiver, sendButton, resendButton,
                callMeButton, phoneEditText, PHONE_WITH_COUNTRY_CODE, sessionManager,
                digitsClient, errors, new ActivityClassManagerImp(), scribeService, true,
                timerTextView);

        final Response response = new Response(TWITTER_URL, HttpURLConnection.HTTP_OK, "",
                new ArrayList<Header>(), null);
        final DigitsUser user = new DigitsUser(USER_ID, "");

        final DigitsCallback callback = executeRequest();
        callback.success(user, response);

        verify(scribeService).success();
        verify(sessionManager).setActiveSession(any(DigitsSession.class));
        verify(sendButton).showFinish();
        verify(context).startActivityForResult(intentCaptor.capture(),
                eq(DigitsActivity.REQUEST_CODE));
        final Intent intent = intentCaptor.getValue();
        assertEquals(resultReceiver,
                intent.getParcelableExtra(DigitsClient.EXTRA_RESULT_RECEIVER));
        assertEquals(PHONE_WITH_COUNTRY_CODE, intent.getStringExtra(DigitsClient.EXTRA_PHONE));
    }

    public void testExecuteRequest_failure() throws Exception {
        controller = new DummyConfirmationCodeController(resultReceiver, sendButton, resendButton,
                callMeButton, phoneEditText, PHONE_WITH_COUNTRY_CODE, sessionManager,
                digitsClient, errors, new ActivityClassManagerImp(), scribeService, true,
                timerTextView);

        final Response response = new Response(TWITTER_URL, HttpURLConnection.HTTP_OK, "",
                new ArrayList<Header>(), null);
        final DigitsUser user = new DigitsUser(USER_ID, "");

        final DigitsCallback callback = executeRequest();
        callback.failure(TestConstants.ANY_EXCEPTION);
        verify(callMeButton).showError();
        verify(resendButton).showError();
        verify(sendButton).showError();
    }

    public void testResendCode_success() throws Exception {
        final ArgumentCaptor<Runnable> runnableArgumentCaptor = ArgumentCaptor.forClass
                (Runnable.class);

        final DummyConfirmationCodeController dcc =
                new DummyConfirmationCodeController(resultReceiver, sendButton, resendButton,
                callMeButton, phoneEditText, PHONE_WITH_COUNTRY_CODE, sessionManager,
                digitsClient, errors, new ActivityClassManagerImp(), scribeService, true,
                        timerTextView);
        controller = dcc;

        final CountDownTimer timer = dcc.getCountDownTimer();

        controller.resendCode(context, resendButton, Verification.sms);
        verify(resendButton).showProgress();
        verify(digitsClient).registerDevice(eq(PHONE_WITH_COUNTRY_CODE), eq(Verification.sms),
                callbackCaptor.capture());

        final DigitsCallback<DeviceRegistrationResponse> callback = callbackCaptor.getValue();
        assertNotNull(callback);

        final DeviceRegistrationResponse data = new DeviceRegistrationResponse();
        final Result<DeviceRegistrationResponse> deviceResponse = new Result<>(data, null);

        callback.success(deviceResponse);
        verify(resendButton).showFinish();
        verify(resendButton).postDelayed(runnableArgumentCaptor.capture(),
                eq(PhoneNumberController.POST_DELAY_MS));

        //test UI side effects
        final Runnable runnable = runnableArgumentCaptor.getValue();
        runnable.run();
        verify(resendButton).showStart();
        verify(timerTextView).setText(
                String.valueOf(DigitsConstants.RESEND_TIMER_DURATION_MILLIS / 1000),
                TextView.BufferType.NORMAL);
        verify(resendButton).setEnabled(false);
        verify(callMeButton).setEnabled(false);

        //verify countdown started
        verify(timer).start();
    }


    DigitsCallback executeRequest() {
        when(phoneEditText.getText()).thenReturn(Editable.Factory.getInstance().newEditable
                (CODE));
        controller.executeRequest(context);
        verify(scribeService).click(DigitsScribeConstants.Element.SUBMIT);
        verify(sendButton).showProgress();
        final ArgumentCaptor<DigitsCallback> argumentCaptor = ArgumentCaptor.forClass
                (DigitsCallback.class);
        verify(digitsClient).createAccount(eq(CODE), eq(PHONE_WITH_COUNTRY_CODE),
                argumentCaptor.capture());
        assertNotNull(argumentCaptor.getValue());
        return argumentCaptor.getValue();
    }

}
