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

import android.app.ActivityManager;
import android.app.AssistContent;
import android.app.AssistStructure;
import android.app.VoiceInteractor;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class MainInteractionSession extends VoiceInteractionSession
        implements View.OnClickListener {
    static final String TAG = "MainInteractionSession";

    Intent mStartIntent;
    View mContentView;
    AssistVisualizer mAssistVisualizer;
    View mTopContent;
    View mBottomContent;
    TextView mText;
    Button mStartButton;
    ImageView mScreenshot;
    Button mConfirmButton;
    Button mCompleteButton;
    Button mAbortButton;

    AssistStructure mAssistStructure;

    static final int STATE_IDLE = 0;
    static final int STATE_LAUNCHING = 1;
    static final int STATE_CONFIRM = 2;
    static final int STATE_PICK_OPTION = 3;
    static final int STATE_COMMAND = 4;
    static final int STATE_ABORT_VOICE = 5;
    static final int STATE_COMPLETE_VOICE = 6;
    static final int STATE_DONE=7;

    int mState = STATE_IDLE;
    VoiceInteractor.PickOptionRequest.Option[] mPendingOptions;
    CharSequence mPendingPrompt;
    Request mPendingRequest;

    MainInteractionSession(Context context) {
        super(context);
    }

    @Override
    public void onCreate(Bundle args, int startFlags) {
        super.onCreate(args);
        ActivityManager am = getContext().getSystemService(ActivityManager.class);
        am.setWatchHeapLimit(40*1024*1024);
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        super.onShow(args, showFlags);
        mState = STATE_IDLE;
        mStartIntent = args.getParcelable("intent");
        if (mAssistVisualizer != null) {
            mAssistVisualizer.clearAssistData();
        }
        onHandleScreenshot(null);
        updateState();
    }

    @Override
    public void onHide() {
        super.onHide();
        if (mAssistVisualizer != null) {
            mAssistVisualizer.clearAssistData();
        }
        mState = STATE_DONE;
        updateState();
    }

    @Override
    public View onCreateContentView() {
        mContentView = getLayoutInflater().inflate(R.layout.voice_interaction_session, null);
        mAssistVisualizer = (AssistVisualizer)mContentView.findViewById(R.id.assist_visualizer);
        if (mAssistStructure != null) {
            mAssistVisualizer.setAssistStructure(mAssistStructure);
        }
        mTopContent = mContentView.findViewById(R.id.top_content);
        mBottomContent = mContentView.findViewById(R.id.bottom_content);
        mText = (TextView)mContentView.findViewById(R.id.text);
        mStartButton = (Button)mContentView.findViewById(R.id.start);
        mStartButton.setOnClickListener(this);
        mScreenshot = (ImageView)mContentView.findViewById(R.id.screenshot);
        mConfirmButton = (Button)mContentView.findViewById(R.id.confirm);
        mConfirmButton.setOnClickListener(this);
        mCompleteButton = (Button)mContentView.findViewById(R.id.complete);
        mCompleteButton.setOnClickListener(this);
        mAbortButton = (Button)mContentView.findViewById(R.id.abort);
        mAbortButton.setOnClickListener(this);
        return mContentView;
    }

    @Override
    public void onHandleAssist(Bundle assistBundle) {
        if (assistBundle != null) {
            parseAssistData(assistBundle);
        } else {
            Log.i(TAG, "onHandleAssist: NO ASSIST BUNDLE");
        }
    }

    @Override
    public void onHandleScreenshot(Bitmap screenshot) {
        if (screenshot != null) {
            mScreenshot.setImageBitmap(screenshot);
            mScreenshot.setAdjustViewBounds(true);
            mScreenshot.setMaxWidth(screenshot.getWidth()/3);
            mScreenshot.setMaxHeight(screenshot.getHeight()/3);
        } else {
            mScreenshot.setImageDrawable(null);
        }
    }

    void parseAssistData(Bundle assistBundle) {
        if (assistBundle != null) {
            Bundle assistContext = assistBundle.getBundle(Intent.EXTRA_ASSIST_CONTEXT);
            if (assistContext != null) {
                mAssistStructure = AssistStructure.getAssistStructure(assistContext);
                if (mAssistStructure != null) {
                    if (mAssistVisualizer != null) {
                        mAssistVisualizer.setAssistStructure(mAssistStructure);
                    }
                }
                AssistContent content = AssistContent.getAssistContent(assistContext);
                if (content != null) {
                    Log.i(TAG, "Assist intent: " + content.getIntent());
                    Log.i(TAG, "Assist clipdata: " + content.getClipData());
                }
                return;
            }
        }
        if (mAssistVisualizer != null) {
            mAssistVisualizer.clearAssistData();
        }
    }

    void updateState() {
        if (mState == STATE_IDLE) {
            mTopContent.setVisibility(View.VISIBLE);
            mBottomContent.setVisibility(View.GONE);
            mAssistVisualizer.setVisibility(View.VISIBLE);
        } else if (mState == STATE_DONE) {
            mTopContent.setVisibility(View.GONE);
            mBottomContent.setVisibility(View.GONE);
            mAssistVisualizer.setVisibility(View.GONE);
        } else {
            mTopContent.setVisibility(View.GONE);
            mBottomContent.setVisibility(View.VISIBLE);
            mAssistVisualizer.setVisibility(View.GONE);
        }
        mStartButton.setEnabled(mState == STATE_IDLE);
        mConfirmButton.setEnabled(mState == STATE_CONFIRM || mState == STATE_PICK_OPTION
                || mState == STATE_COMMAND);
        mAbortButton.setEnabled(mState == STATE_ABORT_VOICE);
        mCompleteButton.setEnabled(mState == STATE_COMPLETE_VOICE);
    }

    public void onClick(View v) {
        if (v == mStartButton) {
            mState = STATE_LAUNCHING;
            updateState();
            startVoiceActivity(mStartIntent);
        } else if (v == mConfirmButton) {
            if (mState == STATE_CONFIRM) {
                mPendingRequest.sendConfirmResult(true, null);
                mPendingRequest = null;
                mState = STATE_LAUNCHING;
            } else if (mState == STATE_PICK_OPTION) {
                int numReturn = mPendingOptions.length/2;
                if (numReturn <= 0) {
                    numReturn = 1;
                }
                VoiceInteractor.PickOptionRequest.Option[] picked
                        = new VoiceInteractor.PickOptionRequest.Option[numReturn];
                for (int i=0; i<picked.length; i++) {
                    picked[i] = mPendingOptions[i*2];
                }
                mPendingOptions = picked;
                if (picked.length <= 1) {
                    mPendingRequest.sendPickOptionResult(true, picked, null);
                    mPendingRequest = null;
                    mState = STATE_LAUNCHING;
                } else {
                    mPendingRequest.sendPickOptionResult(false, picked, null);
                    updatePickText();
                }
            } else if (mPendingRequest != null) {
                mPendingRequest.sendCommandResult(true, null);
                mPendingRequest = null;
                mState = STATE_LAUNCHING;
            }
        } else if (v == mAbortButton) {
            mPendingRequest.sendAbortVoiceResult(null);
            mPendingRequest = null;
        } else if (v== mCompleteButton) {
            mPendingRequest.sendCompleteVoiceResult(null);
            mPendingRequest = null;
        }
        updateState();
    }

    @Override
    public void onComputeInsets(Insets outInsets) {
        super.onComputeInsets(outInsets);
        if (mState != STATE_IDLE) {
            outInsets.contentInsets.top = mBottomContent.getTop();
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT;
        }
    }

    @Override
    public boolean[] onGetSupportedCommands(Caller caller, String[] commands) {
        return new boolean[commands.length];
    }

    @Override
    public void onConfirm(Caller caller, Request request, CharSequence prompt, Bundle extras) {
        Log.i(TAG, "onConfirm: prompt=" + prompt + " extras=" + extras);
        mText.setText(prompt);
        mConfirmButton.setText("Confirm");
        mPendingRequest = request;
        mPendingPrompt = prompt;
        mState = STATE_CONFIRM;
        updateState();
    }

    @Override
    public void onPickOption(Caller caller, Request request, CharSequence prompt,
            VoiceInteractor.PickOptionRequest.Option[] options, Bundle extras) {
        Log.i(TAG, "onPickOption: prompt=" + prompt + " options=" + options + " extras=" + extras);
        mConfirmButton.setText("Pick Option");
        mPendingRequest = request;
        mPendingPrompt = prompt;
        mPendingOptions = options;
        mState = STATE_PICK_OPTION;
        updatePickText();
        updateState();
    }

    void updatePickText() {
        StringBuilder sb = new StringBuilder();
        sb.append(mPendingPrompt);
        sb.append(": ");
        for (int i=0; i<mPendingOptions.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(mPendingOptions[i].getLabel());
        }
        mText.setText(sb.toString());
    }

    @Override
    public void onCompleteVoice(Caller caller, Request request, CharSequence message, Bundle extras) {
        Log.i(TAG, "onCompleteVoice: message=" + message + " extras=" + extras);
        mText.setText(message);
        mPendingRequest = request;
        mState = STATE_COMPLETE_VOICE;
        updateState();
    }

    @Override
    public void onAbortVoice(Caller caller, Request request, CharSequence message, Bundle extras) {
        Log.i(TAG, "onAbortVoice: message=" + message + " extras=" + extras);
        mText.setText(message);
        mPendingRequest = request;
        mState = STATE_ABORT_VOICE;
        updateState();
    }

    @Override
    public void onCommand(Caller caller, Request request, String command, Bundle extras) {
        Log.i(TAG, "onCommand: command=" + command + " extras=" + extras);
        mText.setText("Command: " + command);
        mConfirmButton.setText("Finish Command");
        mPendingRequest = request;
        mState = STATE_COMMAND;
        updateState();
    }

    @Override
    public void onCancel(Request request) {
        Log.i(TAG, "onCancel");
        request.sendCancelResult();
    }
}
