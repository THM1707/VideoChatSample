package com.thm.videosample.activity;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.quickblox.chat.QBChatService;
import com.quickblox.core.QBEntityCallback;
import com.quickblox.core.exception.QBResponseException;
import com.quickblox.users.QBUsers;
import com.quickblox.users.model.QBUser;
import com.quickblox.videochat.webrtc.AppRTCAudioManager;
import com.quickblox.videochat.webrtc.BaseSession;
import com.quickblox.videochat.webrtc.QBRTCCameraVideoCapturer;
import com.quickblox.videochat.webrtc.QBRTCClient;
import com.quickblox.videochat.webrtc.QBRTCConfig;
import com.quickblox.videochat.webrtc.QBRTCScreenCapturer;
import com.quickblox.videochat.webrtc.QBRTCSession;
import com.quickblox.videochat.webrtc.QBRTCTypes;
import com.quickblox.videochat.webrtc.QBSignalingSpec;
import com.quickblox.videochat.webrtc.callbacks.QBRTCClientSessionCallbacks;
import com.quickblox.videochat.webrtc.callbacks.QBRTCSessionEventsCallback;
import com.quickblox.videochat.webrtc.callbacks.QBRTCSessionStateCallback;
import com.quickblox.videochat.webrtc.callbacks.QBRTCSignalingCallback;
import com.quickblox.videochat.webrtc.exception.QBRTCSignalException;
import com.thm.videosample.R;
import com.thm.videosample.db.QbUsersDbManager;
import com.thm.videosample.fragment.BaseConversationFragment;
import com.thm.videosample.fragment.ConversationFragmentCallbackListener;
import com.thm.videosample.fragment.IncomeCallFragment;
import com.thm.videosample.fragment.IncomeCallFragmentCallbackListener;
import com.thm.videosample.fragment.VideoConversationFragment;
import com.thm.videosample.utils.Constants;
import com.thm.videosample.utils.FragmentExecutor;
import com.thm.videosample.utils.NetworkConnectionChecker;
import com.thm.videosample.utils.PermissionsChecker;
import com.thm.videosample.utils.RingtonePlayer;
import com.thm.videosample.utils.SettingsUtil;
import com.thm.videosample.utils.UsersUtils;
import com.thm.videosample.utils.WebRtcSessionManager;

import org.jivesoftware.smack.AbstractConnectionListener;
import org.webrtc.CameraVideoCapturer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CallActivity extends BaseActivity
    implements QBRTCClientSessionCallbacks, QBRTCSessionStateCallback<QBRTCSession>,
    QBRTCSignalingCallback,
    IncomeCallFragmentCallbackListener, ConversationFragmentCallbackListener,
    NetworkConnectionChecker.OnConnectivityChangedListener {
    private static final String TAG = CallActivity.class.getSimpleName();
    public static final String INCOME_CALL_FRAGMENT = "income_call_fragment";
    private boolean isInCommingCall;
    private WebRtcSessionManager mSessionManager;
    private QBRTCSession mCurrentSession;
    private QBRTCClient mRtcClient;
    private List<Integer> mOpponentsIdsList;
    private ConnectionListener mConnectionListener;
    private SharedPreferences mSharedPref;
    private RingtonePlayer mRingtonePlayer;
    private LinearLayout mConnectionView;
    private boolean isCallStarted;
    private long expirationReconnectionTime;
    private int reconnectHangUpTimeMillis;
    private AppRTCAudioManager mAudioManager;
    private boolean isVideoCall;
    private PermissionsChecker mChecker;
    private NetworkConnectionChecker mNetworkConnectionChecker;
    private OnChangeAudioDevice mOnChangeAudioDeviceCallback;
    private Runnable showIncomingCallWindowTask;
    private Handler showIncomingCallWindowTaskHandler;
    private ArrayList<CurrentCallStateCallback> currentCallStateCallbackList = new ArrayList<>();
    private boolean closeByWifiStateAllow = true;
    private QbUsersDbManager dbManager;

    public static void start(Context context,
                             boolean isIncomingCall) {
        Intent intent = new Intent(context, CallActivity.class);
        intent.putExtra(Constants.EXTRA_IS_INCOMING_CALL, isIncomingCall);
        context.startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);
        parseIntentExtras();
        mSessionManager = WebRtcSessionManager.getInstance(this);
        if (!currentSessionExist()) {
            finish();
            Log.d(TAG, "finish CallActivity");
            return;
        }
        initFields();
        initCurrentSession(mCurrentSession);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        initQBRTCClient();
        initAudioManager();
        initWiFiManagerListener();
        mRingtonePlayer = new RingtonePlayer(this, R.raw.beep);
        mConnectionView = (LinearLayout) View.inflate(this, R.layout.connection_popup, null);
        mChecker = new PermissionsChecker(getApplicationContext());
        if (!isInCommingCall) {
            startAudioManager();
            mRingtonePlayer.play(true);
        }
        startSuitableFragment(isInCommingCall);
    }

    private void initFields() {
        mOpponentsIdsList = mCurrentSession.getOpponents();
        dbManager = QbUsersDbManager.getInstance(getApplicationContext());
    }

    private void initCurrentSession(QBRTCSession session) {
        if (session != null) {
            Log.d(TAG, "Init new QBRTCSession");
            mCurrentSession = session;
            mCurrentSession.addSessionCallbacksListener(this);
            mCurrentSession.addSignalingCallback(this);
        }
    }

    private void parseIntentExtras() {
        isInCommingCall = getIntent().getExtras().getBoolean(Constants.EXTRA_IS_INCOMING_CALL);
    }

    private boolean currentSessionExist() {
        mCurrentSession = mSessionManager.getCurrentSession();
        return mCurrentSession != null;
    }

    private void initQBRTCClient() {
        mRtcClient = QBRTCClient.getInstance(this);
        mRtcClient.setCameraErrorHandler(new CameraVideoCapturer.CameraEventsHandler() {
            @Override
            public void onCameraError(final String s) {
                showToast(s);
            }

            @Override
            public void onCameraDisconnected() {
                showToast("Camera onCameraDisconnected: ");
            }

            @Override
            public void onCameraFreezed(String s) {
                showToast("Camera freezed: " + s);
                hangUpCurrentSession();
            }

            @Override
            public void onCameraOpening(String s) {
                showToast("Camera aOpening: " + s);
            }

            @Override
            public void onFirstFrameAvailable() {
                showToast("onFirstFrameAvailable: ");
            }

            @Override
            public void onCameraClosed() {
            }
        });
        QBRTCConfig.setMaxOpponentsCount(Constants.MAX_OPPONENTS_COUNT);
        SettingsUtil.setSettingsStrategy(mOpponentsIdsList, mSharedPref, CallActivity.this);
        SettingsUtil.configRTCTimers(CallActivity.this);
        QBRTCConfig.setDebugEnabled(true);
        // Add activity as callback to RTCClient
        mRtcClient.addSessionCallbacksListener(this);
        // Start mange QBRTCSessions according to VideoCall parser's callbacks
        mRtcClient.prepareToProcessCalls();
        mConnectionListener = new ConnectionListener();
        QBChatService.getInstance().addConnectionListener(mConnectionListener);
    }

    private void initAudioManager() {
        mAudioManager = AppRTCAudioManager.create(this);
        isVideoCall = QBRTCTypes.QBConferenceType.QB_CONFERENCE_TYPE_VIDEO
            .equals(mCurrentSession.getConferenceType());
        if (isVideoCall) {
            mAudioManager.setDefaultAudioDevice(AppRTCAudioManager.AudioDevice.SPEAKER_PHONE);
            Log.d(TAG, "AppRTCAudioManager.AudioDevice.SPEAKER_PHONE");
        } else {
            mAudioManager.setDefaultAudioDevice(AppRTCAudioManager.AudioDevice.EARPIECE);
            mAudioManager.setManageSpeakerPhoneByProximity(
                SettingsUtil.isManageSpeakerPhoneByProximity(this));
            Log.d(TAG, "AppRTCAudioManager.AudioDevice.EARPIECE");
        }
        mAudioManager.setOnWiredHeadsetStateListener((plugged, hasMicrophone) -> {
            if (isCallStarted) {
                Toast.makeText(this, "Headset " + (plugged ? "plugged" : "unplugged"),
                    Toast.LENGTH_SHORT).show();
            }
        });
        mAudioManager.setBluetoothAudioDeviceStateListener(connected -> {
            if (isCallStarted) {
                Toast.makeText(this, "Bluetooth " + (connected ? "connected" : "disconnected"),
                    Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initWiFiManagerListener() {
        mNetworkConnectionChecker = new NetworkConnectionChecker(getApplication());
    }

    private void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(CallActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void connectivityChanged(boolean availableNow) {
        if (isCallStarted) {
            showToast("Internet connection " + (availableNow ? "available" : " unavailable"));
        }
    }

    private void startAudioManager() {
        mAudioManager.start((selectedAudioDevice, availableAudioDevices) -> {
            Toast.makeText(this, "Audio device switched to  " + selectedAudioDevice,
                Toast.LENGTH_SHORT).show();
            if (mOnChangeAudioDeviceCallback != null) {
                mOnChangeAudioDeviceCallback.audioDeviceChanged(selectedAudioDevice);
            }
        });
    }

    private void startSuitableFragment(boolean isInComingCall) {
        if (isInComingCall) {
            initIncomingCallTask();
            startLoadAbsentUsers();
            addIncomeCallFragment();
            checkPermission();
        } else {
            addConversationFragment(isInComingCall);
        }
    }

    private void initIncomingCallTask() {
        showIncomingCallWindowTaskHandler = new Handler(Looper.myLooper());
        showIncomingCallWindowTask = new Runnable() {
            @Override
            public void run() {
                if (mCurrentSession == null) {
                    return;
                }
                QBRTCSession.QBRTCSessionState currentSessionState = mCurrentSession.getState();
                if (QBRTCSession.QBRTCSessionState.QB_RTC_SESSION_NEW.equals(currentSessionState)) {
                    rejectCurrentSession();
                } else {
                    mRingtonePlayer.stop();
                    hangUpCurrentSession();
                }
                Toast.makeText(CallActivity.this, "Call was stopped by timer",
                    Toast.LENGTH_LONG).show();
            }
        };
    }

    private void startLoadAbsentUsers() {
        ArrayList<QBUser> userFromDb = dbManager.getAllUsers();
        ArrayList<Integer> allParticipantsOfCall = new ArrayList<>();
        allParticipantsOfCall.addAll(mOpponentsIdsList);
        if (isInCommingCall) {
            allParticipantsOfCall.add(mCurrentSession.getCallerID());
        }
        ArrayList<Integer> idsUsersNeedLoad = UsersUtils.getIdsNotLoadedUsers(userFromDb,
            allParticipantsOfCall);
        if (!idsUsersNeedLoad.isEmpty()) {
            QBUsers.getUsersByIDs(idsUsersNeedLoad, null).performAsync(
                new QBEntityCallback<ArrayList<QBUser>>() {
                    @Override
                    public void onSuccess(ArrayList<QBUser> qbUsers, Bundle bundle) {
                        dbManager.saveAllUsers(qbUsers, false);
                        needUpdateOpponentsList(qbUsers);
                    }

                    @Override
                    public void onError(QBResponseException e) {
                    }
                });
        }
    }

    private void needUpdateOpponentsList(ArrayList<QBUser> newUsers) {
        notifyCallStateListenersNeedUpdateOpponentsList(newUsers);
    }

    private void addIncomeCallFragment() {
        Log.d(TAG, "QBRTCSession in addIncomeCallFragment is " + mCurrentSession);
        if (mCurrentSession != null) {
            IncomeCallFragment fragment = new IncomeCallFragment();
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,
                fragment, INCOME_CALL_FRAGMENT).commitAllowingStateLoss();
        } else {
            Log.d(TAG, "SKIP addIncomeCallFragment method");
        }
    }

    private void checkPermission() {
        if (mChecker.lacksPermissions(Constants.PERMISSIONS)) {
            PermissionsActivity.startActivity(this, !isVideoCall, Constants.PERMISSIONS);
        }
    }

    public void rejectCurrentSession() {
        if (mCurrentSession != null) {
            mCurrentSession.rejectCall(new HashMap<>());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
    ////////////////////////////// IncomeCallFragmentCallbackListener ////////////////////////////

    @Override
    public void onAcceptCurrentSession() {
        if (mCurrentSession != null) {
            startAudioManager();
            addConversationFragment(true);
        } else {
            Log.d(TAG, "SKIP addConversationFragment method");
        }
    }

    @Override
    public void onRejectCurrentSession() {
        rejectCurrentSession();
    }

    private void addConversationFragment(boolean isIncomingCall) {
        BaseConversationFragment conversationFragment = BaseConversationFragment.newInstance(
            new VideoConversationFragment(), isIncomingCall);
        FragmentExecutor
            .addFragment(getSupportFragmentManager(), R.id.fragment_container, conversationFragment,
                conversationFragment.getClass().getSimpleName());
    }
    //////////////////////////////////////////   end   /////////////////////////////////////////////

    ////////////////////////////// ConversationFragmentCallbackListener ////////////////////////////
    @Override
    public void addTCClientConnectionCallback(QBRTCSessionStateCallback clientConnectionCallbacks) {
        if (mCurrentSession != null) {
            mCurrentSession.addSessionCallbacksListener(clientConnectionCallbacks);
        }
    }

    @Override
    public void removeRTCClientConnectionCallback(
        QBRTCSessionStateCallback clientConnectionCallbacks) {
        if (mCurrentSession != null) {
            mCurrentSession.removeSessionCallbacksListener(clientConnectionCallbacks);
        }
    }

    @Override
    public void addRTCSessionEventsCallback(QBRTCSessionEventsCallback eventsCallback) {
        QBRTCClient.getInstance(this).addSessionCallbacksListener(eventsCallback);
    }

    @Override
    public void removeRTCSessionEventsCallback(QBRTCSessionEventsCallback eventsCallback) {
        QBRTCClient.getInstance(this).removeSessionsCallbacksListener(eventsCallback);
    }

    @Override
    public void addCurrentCallStateCallback(CurrentCallStateCallback currentCallStateCallback) {
        currentCallStateCallbackList.add(currentCallStateCallback);
    }

    @Override
    public void removeCurrentCallStateCallback(CurrentCallStateCallback currentCallStateCallback) {
        currentCallStateCallbackList.remove(currentCallStateCallback);
    }

    @Override
    public void addOnChangeAudioDeviceCallback(OnChangeAudioDevice onChangeDynamicCallback) {
        mOnChangeAudioDeviceCallback = onChangeDynamicCallback;
        notifyAboutCurrentAudioDevice();
    }

    @Override
    public void removeOnChangeAudioDeviceCallback(OnChangeAudioDevice onChangeDynamicCallback) {
        mOnChangeAudioDeviceCallback = null;
    }

    @Override
    public void onSetAudioEnabled(boolean isAudioEnabled) {
        setAudioEnabled(isAudioEnabled);
    }

    @Override
    public void onSetVideoEnabled(boolean isNeedEnableCam) {
        setVideoEnabled(isNeedEnableCam);
    }

    @Override
    public void onSwitchAudio() {
        Log.v(TAG,
            "onSwitchAudio(), SelectedAudioDevice() = " + mAudioManager.getSelectedAudioDevice());
        if (mAudioManager.getSelectedAudioDevice() !=
            AppRTCAudioManager.AudioDevice.SPEAKER_PHONE) {
            mAudioManager.selectAudioDevice(AppRTCAudioManager.AudioDevice.SPEAKER_PHONE);
        } else {
            if (mAudioManager.getAudioDevices()
                .contains(AppRTCAudioManager.AudioDevice.BLUETOOTH)) {
                mAudioManager.selectAudioDevice(AppRTCAudioManager.AudioDevice.BLUETOOTH);
            } else if (mAudioManager.getAudioDevices()
                .contains(AppRTCAudioManager.AudioDevice.WIRED_HEADSET)) {
                mAudioManager.selectAudioDevice(AppRTCAudioManager.AudioDevice.WIRED_HEADSET);
            } else {
                mAudioManager.selectAudioDevice(AppRTCAudioManager.AudioDevice.EARPIECE);
            }
        }
    }

    @Override
    public void onHangUpCurrentSession() {
        hangUpCurrentSession();
    }

    @TargetApi(21)
    @Override
    public void onStartScreenSharing() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        QBRTCScreenCapturer.requestPermissions(CallActivity.this);
    }

    @Override
    public void onSwitchCamera(CameraVideoCapturer.CameraSwitchHandler cameraSwitchHandler) {
        ((QBRTCCameraVideoCapturer) (mCurrentSession.getMediaStreamManager().getVideoCapturer()))
            .switchCamera(cameraSwitchHandler);
    }

    public void notifyAboutCurrentAudioDevice() {
        mOnChangeAudioDeviceCallback.audioDeviceChanged(mAudioManager.getSelectedAudioDevice());
    }

    private void setAudioEnabled(boolean isAudioEnabled) {
        if (mCurrentSession != null && mCurrentSession.getMediaStreamManager() != null) {
            mCurrentSession.getMediaStreamManager().getLocalAudioTrack().setEnabled(isAudioEnabled);
        }
    }

    private void setVideoEnabled(boolean isVideoEnabled) {
        if (mCurrentSession != null && mCurrentSession.getMediaStreamManager() != null) {
            mCurrentSession.getMediaStreamManager().getLocalVideoTrack().setEnabled(isVideoEnabled);
        }
    }
    //Session state callback

    @Override
    public void onStateChanged(QBRTCSession session,
                               BaseSession.QBRTCSessionState qbrtcSessionState) {
    }

    @Override
    public void onConnectedToUser(QBRTCSession session, Integer integer) {
        // when connect to caller or the one who we r calling
        isCallStarted = true;
        notifyCallStateListenersCallStarted();
        if (isInCommingCall) {
            stopIncomeCallTimer();
        }
        Log.d(TAG, "onConnectedToUser() is started");
    }

    @Override
    public void onDisconnectedFromUser(QBRTCSession session, Integer integer) {
    }

    @Override
    public void onConnectionClosedForUser(QBRTCSession session, Integer integer) {
    }
    //////////////////////////////////////////   end   /////////////////////////////////////////////

    private class ConnectionListener extends AbstractConnectionListener {
        @Override
        public void connectionClosedOnError(Exception e) {
            showNotificationPopUp(R.string.connection_was_lost, true);
            setExpirationReconnectionTime();
        }

        @Override
        public void reconnectionSuccessful() {
            showNotificationPopUp(R.string.connection_was_lost, false);
        }

        @Override
        public void reconnectingIn(int seconds) {
            Log.i(TAG, "reconnectingIn " + seconds);
            if (!isCallStarted) {
                hangUpAfterLongReconnection();
            }
        }
    }

    private void setExpirationReconnectionTime() {
        reconnectHangUpTimeMillis = SettingsUtil.getPreferenceInt(mSharedPref, this,
            R.string.pref_disconnect_time_interval_key,
            R.string.pref_disconnect_time_interval_default_value) * 1000;
        expirationReconnectionTime = System.currentTimeMillis() + reconnectHangUpTimeMillis;
    }

    private void hangUpAfterLongReconnection() {
        if (expirationReconnectionTime < System.currentTimeMillis()) {
            hangUpCurrentSession();
        }
    }

    public void hangUpCurrentSession() {
        mRingtonePlayer.stop();
        if (mCurrentSession != null) {
            mCurrentSession.hangUp(new HashMap<>());
        }
    }

    private void showNotificationPopUp(final int text, final boolean show) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (show) {
                    ((TextView) mConnectionView.findViewById(R.id.notification)).setText(text);
                    if (mConnectionView.getParent() == null) {
                        ((ViewGroup) CallActivity.this.findViewById(R.id.fragment_container))
                            .addView(mConnectionView);
                    }
                } else {
                    ((ViewGroup) CallActivity.this.findViewById(R.id.fragment_container))
                        .removeView(mConnectionView);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mNetworkConnectionChecker.registerListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mNetworkConnectionChecker.unregisterListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        QBChatService.getInstance().removeConnectionListener(mConnectionListener);
    }

    //Signal state callback
    @Override
    public void onSuccessSendingPacket(QBSignalingSpec.QBSignalCMD qbSignalCMD, Integer integer) {
    }

    @Override
    public void onErrorSendingPacket(QBSignalingSpec.QBSignalCMD qbSignalCMD, Integer integer,
                                     QBRTCSignalException e) {
        showToast("Disconnected… Please check your Internet connection!");
    }

    //Session even callback
    @Override
    public void onUserNotAnswer(QBRTCSession qbrtcSession, Integer integer) {
        if (!qbrtcSession.equals(mCurrentSession)) {
            return;
        }
        mRingtonePlayer.stop();
    }

    @Override
    public void onCallRejectByUser(QBRTCSession qbrtcSession, Integer integer,
                                   Map<String, String> map) {
        if (!qbrtcSession.equals(mCurrentSession)) {
            return;
        }
        mRingtonePlayer.stop();
    }

    @Override
    public void onCallAcceptByUser(QBRTCSession qbrtcSession, Integer integer,
                                   Map<String, String> map) {
        if (!qbrtcSession.equals(mCurrentSession)) {
            return;
        }
        mRingtonePlayer.stop();
    }

    @Override
    public void onReceiveHangUpFromUser(QBRTCSession qbrtcSession, Integer userId,
                                        Map<String, String> map) {
        if (qbrtcSession.equals(mCurrentSession)) {
            if (userId.equals(qbrtcSession.getCallerID())) {
                hangUpCurrentSession();
                Log.d(TAG, "initiator hung up the call");
            }
            showToast("User ID " + userId + " has left conversation");
        }
    }

    @Override
    public void onSessionClosed(QBRTCSession session) {
        Log.d(TAG, "Session " + session.getSessionID() + " start stop session");
        if (session.equals(mCurrentSession)) {
            Log.d(TAG, "Stop session");
            if (mAudioManager != null) {
                mAudioManager.stop();
            }
            if (mCurrentSession != null) {
                mCurrentSession.removeSessionCallbacksListener(CallActivity.this);
                mCurrentSession.removeSignalingCallback(CallActivity.this);
                mRtcClient.removeSessionsCallbacksListener(CallActivity.this);
                mCurrentSession = null;
            }
            closeByWifiStateAllow = true;
            finish();
        }
    }

    @Override
    public void onReceiveNewSession(QBRTCSession session) {
        Log.d(TAG, "Session " + session.getSessionID() + " are income");
        if (mCurrentSession != null) {
            Log.d(TAG, "Stop new session. Device now is busy");
            session.rejectCall(null);
        }
    }

    @Override
    public void onUserNoActions(QBRTCSession qbrtcSession, Integer integer) {
        startIncomeCallTimer(0); // time set in initQBRTCClient();
    }

    @Override
    public void onSessionStartClose(QBRTCSession session) {
        if (session.equals(mCurrentSession)) {
            session.removeSessionCallbacksListener(CallActivity.this);
            notifyCallStateListenersCallStopped();
        }
    }

    private void startIncomeCallTimer(long time) {
        showIncomingCallWindowTaskHandler.postAtTime(showIncomingCallWindowTask,
            SystemClock.uptimeMillis() + time);
    }

    private void stopIncomeCallTimer() {
        Log.d(TAG, "stopIncomeCallTimer");
        showIncomingCallWindowTaskHandler.removeCallbacks(showIncomingCallWindowTask);
    }

    public interface OnChangeAudioDevice {
        void audioDeviceChanged(AppRTCAudioManager.AudioDevice newAudioDevice);
    }

    public interface CurrentCallStateCallback {
        void onCallStarted();
        void onCallStopped();
        void onOpponentsListUpdated(ArrayList<QBUser> newUsers);
    }

    private void notifyCallStateListenersCallStarted() {
        for (CurrentCallStateCallback callback : currentCallStateCallbackList) {
            callback.onCallStarted();
        }
    }

    private void notifyCallStateListenersCallStopped() {
        for (CurrentCallStateCallback callback : currentCallStateCallbackList) {
            callback.onCallStopped();
        }
    }

    private void notifyCallStateListenersNeedUpdateOpponentsList(final ArrayList<QBUser> newUsers) {
        for (CurrentCallStateCallback callback : currentCallStateCallbackList) {
            callback.onOpponentsListUpdated(newUsers);
        }
    }
}
