package com.thm.videosample.service;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.quickblox.chat.QBChatService;
import com.quickblox.chat.QBSignaling;
import com.quickblox.chat.QBWebRTCSignaling;
import com.quickblox.chat.connections.tcp.QBTcpChatConnectionFabric;
import com.quickblox.chat.connections.tcp.QBTcpConfigurationBuilder;
import com.quickblox.chat.listeners.QBVideoChatSignalingManagerListener;
import com.quickblox.core.QBEntityCallback;
import com.quickblox.core.exception.QBResponseException;
import com.quickblox.users.model.QBUser;
import com.quickblox.videochat.webrtc.QBRTCClient;
import com.quickblox.videochat.webrtc.QBRTCConfig;
import com.thm.videosample.utils.Constants;
import com.thm.videosample.utils.WebRtcSessionManager;

public class CallService extends Service {
    private static final String TAG = CallService.class.getSimpleName();
    private QBChatService mChatService;
    private int mCurrentCommand;
    private QBRTCClient mRtcClient;
    private PendingIntent mPendingIntent;
    private QBUser mQBUser;

    public static void start(Context context, QBUser qbUser, PendingIntent pendingIntent) {
        Intent intent = new Intent(context, CallService.class);
        intent.putExtra(Constants.EXTRA_COMMAND_TO_SERVICE, Constants.COMMAND_LOGIN);
        intent.putExtra(Constants.EXTRA_QB_USER, qbUser);
        intent.putExtra(Constants.EXTRA_PENDING_INTENT, pendingIntent);
        context.startService(intent);
    }

    public static void start(Context context, QBUser qbUser) {
        start(context, qbUser, null);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChatService();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        parseIntentExtras(intent);
        startSuitableActions();
        return START_REDELIVER_INTENT;
    }

    private void createChatService() {
        if (mChatService == null) {
            QBTcpConfigurationBuilder configurationBuilder = new QBTcpConfigurationBuilder();
            configurationBuilder.setSocketTimeout(0);
            QBChatService.setConnectionFabric(new QBTcpChatConnectionFabric(configurationBuilder));
            QBChatService.setDebugEnabled(true);
            mChatService = QBChatService.getInstance();
        }
    }

    private void parseIntentExtras(Intent intent) {
        if (intent != null && intent.getExtras() != null) {
            mCurrentCommand = intent.getIntExtra(Constants.EXTRA_COMMAND_TO_SERVICE,
                Constants.COMMAND_NOT_FOUND);
            mPendingIntent = intent.getParcelableExtra(Constants.EXTRA_PENDING_INTENT);
            mQBUser = (QBUser) intent.getSerializableExtra(Constants.EXTRA_QB_USER);
        }
    }

    private void startSuitableActions() {
        if (mCurrentCommand == Constants.COMMAND_LOGIN) {
            startLoginToChat();
        } else if (mCurrentCommand == Constants.COMMAND_LOGOUT) {
            logout();
        }
    }

    private void startLoginToChat() {
        if (!mChatService.isLoggedIn()) {
            loginToChat(mQBUser);
        } else {
            sendResultToActivity(true, null);
        }
    }

    private void loginToChat(QBUser qbUser) {
        mChatService.login(qbUser, new QBEntityCallback<QBUser>() {
            @Override
            public void onSuccess(QBUser qbUser, Bundle bundle) {
                startActionsOnSuccessLogin();
            }

            @Override
            public void onError(QBResponseException e) {
                sendResultToActivity(false,
                    e.getMessage() != null ? e.getMessage() : "Login error");
            }
        });
    }

    private void startActionsOnSuccessLogin() {
        initQBRTCClient();
        sendResultToActivity(true, null);
    }

    private void initQBRTCClient() {
        mRtcClient = QBRTCClient.getInstance(getApplicationContext());
        // Add signalling manager
        mChatService.getVideoChatWebRTCSignalingManager()
            .addSignalingManagerListener(new QBVideoChatSignalingManagerListener() {
                @Override
                public void signalingCreated(QBSignaling qbSignaling, boolean createdLocally) {
                    if (!createdLocally) {
                        mRtcClient.addSignaling((QBWebRTCSignaling) qbSignaling);
                    }
                }
            });
        QBRTCConfig.setDebugEnabled(true);
        // Add service as callback to RTCClient
        mRtcClient.addSessionCallbacksListener(WebRtcSessionManager.getInstance(this));
        mRtcClient.prepareToProcessCalls();
    }

    private void sendResultToActivity(boolean isSuccess, String errorMessage) {
        if (mPendingIntent != null) {
            try {
                Intent intent = new Intent();
                intent.putExtra(Constants.EXTRA_LOGIN_RESULT, isSuccess);
                intent.putExtra(Constants.EXTRA_LOGIN_ERROR_MESSAGE, errorMessage);

                mPendingIntent.send(CallService.this, Constants.EXTRA_LOGIN_RESULT_CODE, intent);
            } catch (PendingIntent.CanceledException e) {
                String errorMessageSendingResult = e.getMessage();
                Log.d(TAG, errorMessageSendingResult != null
                    ? errorMessageSendingResult
                    : "Error sending result to activity");
            }
        }
    }

    public static void logout(Context context) {
        Intent intent = new Intent(context, CallService.class);
        intent.putExtra(Constants.EXTRA_COMMAND_TO_SERVICE, Constants.COMMAND_LOGOUT);
        context.startService(intent);
    }

    private void logout() {
        destroyRtcClientAndChat();
    }

    private void destroyRtcClientAndChat() {
        if (mRtcClient != null) {
            mRtcClient.destroy();
        }
        if (mChatService != null) {
            mChatService.logout(new QBEntityCallback<Void>() {
                @Override
                public void onSuccess(Void aVoid, Bundle bundle) {
                    mChatService.destroy();
                }

                @Override
                public void onError(QBResponseException e) {
                    mChatService.destroy();
                }
            });
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service onDestroy()");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service onBind)");
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "Service onTaskRemoved()");
        super.onTaskRemoved(rootIntent);
        destroyRtcClientAndChat();
    }
}
