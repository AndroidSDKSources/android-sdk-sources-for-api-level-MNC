/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.test.voiceinteraction;

import android.app.Activity;
import android.app.VoiceInteractor;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionService;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

public class TestInteractionActivity extends Activity implements View.OnClickListener {
    static final String TAG = "TestInteractionActivity";

    VoiceInteractor mInteractor;
    VoiceInteractor.Request mCurrentRequest = null;
    TextView mLog;
    Button mAbortButton;
    Button mCompleteButton;
    Button mPickButton;
    Button mJumpOutButton;
    Button mCancelButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!isVoiceInteraction()) {
            Log.w(TAG, "Not running as a voice interaction!");
            finish();
            return;
        }

        if (!VoiceInteractionService.isActiveService(this,
                new ComponentName(this, MainInteractionService.class))) {
            Log.w(TAG, "Not current voice interactor!");
            finish();
            return;
        }

        setContentView(R.layout.test_interaction);
        mLog = (TextView)findViewById(R.id.log);
        mAbortButton = (Button)findViewById(R.id.abort);
        mAbortButton.setOnClickListener(this);
        mCompleteButton = (Button)findViewById(R.id.complete);
        mCompleteButton.setOnClickListener(this);
        mPickButton = (Button)findViewById(R.id.pick);
        mPickButton.setOnClickListener(this);
        mJumpOutButton = (Button)findViewById(R.id.jump);
        mJumpOutButton.setOnClickListener(this);
        mCancelButton = (Button)findViewById(R.id.cancel);
        mCancelButton.setOnClickListener(this);

        mInteractor = getVoiceInteractor();
        mCurrentRequest = new VoiceInteractor.ConfirmationRequest(
                "This is a confirmation", null) {
            @Override
            public void onCancel() {
                Log.i(TAG, "Canceled!");
                getActivity().finish();
            }

            @Override
            public void onConfirmationResult(boolean confirmed, Bundle result) {
                Log.i(TAG, "Confirmation result: confirmed=" + confirmed + " result=" + result);
                getActivity().finish();
            }
        };
        mInteractor.submitRequest(mCurrentRequest);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onClick(View v) {
        if (v == mAbortButton) {
            VoiceInteractor.AbortVoiceRequest req = new VoiceInteractor.AbortVoiceRequest(
                    "Dammit, we suck :(", null) {
                @Override
                public void onCancel() {
                    Log.i(TAG, "Canceled!");
                    mLog.append("Canceled abort\n");
                }

                @Override
                public void onAbortResult(Bundle result) {
                    Log.i(TAG, "Abort result: result=" + result);
                    mLog.append("Abort: result=" + result + "\n");
                    getActivity().finish();
                }
            };
            mInteractor.submitRequest(req);
        } else if (v == mCompleteButton) {
            VoiceInteractor.CompleteVoiceRequest req = new VoiceInteractor.CompleteVoiceRequest(
                    "Woohoo, completed!", null) {
                @Override
                public void onCancel() {
                    Log.i(TAG, "Canceled!");
                    mLog.append("Canceled complete\n");
                }

                @Override
                public void onCompleteResult(Bundle result) {
                    Log.i(TAG, "Complete result: result=" + result);
                    mLog.append("Complete: result=" + result + "\n");
                    getActivity().finish();
                }
            };
            mInteractor.submitRequest(req);
        } else if (v == mPickButton) {
            VoiceInteractor.PickOptionRequest.Option[] options =
                    new VoiceInteractor.PickOptionRequest.Option[5];
            options[0] = new VoiceInteractor.PickOptionRequest.Option("One");
            options[1] = new VoiceInteractor.PickOptionRequest.Option("Two");
            options[2] = new VoiceInteractor.PickOptionRequest.Option("Three");
            options[3] = new VoiceInteractor.PickOptionRequest.Option("Four");
            options[4] = new VoiceInteractor.PickOptionRequest.Option("Five");
            VoiceInteractor.PickOptionRequest req = new VoiceInteractor.PickOptionRequest(
                    "Need to pick something", options, null) {
                @Override
                public void onCancel() {
                    Log.i(TAG, "Canceled!");
                    mLog.append("Canceled pick\n");
                }

                @Override
                public void onPickOptionResult(boolean finished, Option[] selections, Bundle result) {
                    Log.i(TAG, "Pick result: finished=" + finished + " selections=" + selections
                            + " result=" + result);
                    StringBuilder sb = new StringBuilder();
                    if (finished) {
                        sb.append("Pick final result: ");
                    } else {
                        sb.append("Pick intermediate result: ");
                    }
                    for (int i=0; i<selections.length; i++) {
                        if (i >= 1) {
                            sb.append(", ");
                        }
                        sb.append(selections[i].getLabel());
                    }
                    mLog.append(sb.toString());
                    if (finished) {
                        getActivity().finish();
                    }
                }
            };
            mInteractor.submitRequest(req);
        } else if (v == mJumpOutButton) {
            Log.i(TAG, "Jump out");
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(new ComponentName(this, VoiceInteractionMain.class));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else if (v == mCancelButton && mCurrentRequest != null) {
            Log.i(TAG, "Cancel request");
            mCurrentRequest.cancel();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
