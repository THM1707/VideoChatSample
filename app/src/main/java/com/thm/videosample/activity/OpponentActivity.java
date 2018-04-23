package com.thm.videosample.activity;

import android.support.v7.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.quickblox.chat.QBChatService;
import com.quickblox.core.QBEntityCallback;
import com.quickblox.core.exception.QBResponseException;
import com.quickblox.core.request.QBPagedRequestBuilder;
import com.quickblox.users.QBUsers;
import com.quickblox.users.model.QBUser;
import com.quickblox.videochat.webrtc.QBRTCClient;
import com.quickblox.videochat.webrtc.QBRTCSession;
import com.quickblox.videochat.webrtc.QBRTCTypes;
import com.thm.videosample.R;
import com.thm.videosample.db.QbUsersDbManager;
import com.thm.videosample.service.CallService;
import com.thm.videosample.utils.Constants;
import com.thm.videosample.adapter.OpponentAdapter;
import com.thm.videosample.utils.PermissionsChecker;
import com.thm.videosample.utils.SharedPrefsHelper;
import com.thm.videosample.utils.WebRtcSessionManager;

import java.util.ArrayList;

public class OpponentActivity extends BaseActivity {
    private RecyclerView mRecyclerOpponents;
    private OpponentAdapter mOpponentAdapter;
    private ArrayList<QBUser> mOpponentsList;
    private WebRtcSessionManager mWebRtcSessionManager;
    private boolean isRunForCall;
    private PermissionsChecker mChecker;
    private SharedPrefsHelper mSharedPrefsHelper;
    private QbUsersDbManager mDbManager;

    public static void start(Context context, boolean isRunForCall) {
        Intent intent = new Intent(context, OpponentActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.putExtra(Constants.EXTRA_IS_STARTED_FOR_CALL, isRunForCall);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_opponent);
        initField();
        initActionbar();
        initView();
        startLoadOpponents();
        if (isRunForCall && mWebRtcSessionManager.getCurrentSession() != null) {
            CallActivity.start(OpponentActivity.this, true);
        }
        mChecker = new PermissionsChecker(getApplicationContext());
    }

    private void initActionbar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            String name = mSharedPrefsHelper.getQbUser().getFullName();
            Log.d("TryThisOn", "initActionbar: "+ name);

            actionBar.setTitle(name);
        }
    }

    private void initField() {
        mSharedPrefsHelper = SharedPrefsHelper.getInstance(this);
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            isRunForCall = extras.getBoolean(Constants.EXTRA_IS_STARTED_FOR_CALL);
        }
        mDbManager = QbUsersDbManager.getInstance(this);
        mWebRtcSessionManager = WebRtcSessionManager.getInstance(getApplicationContext());
    }

    private void initView() {
        mRecyclerOpponents = findViewById(R.id.recycler_opponent);
    }

    private void startLoadOpponents() {
        mOpponentsList = new ArrayList<>();
        showProgressDialog(R.string.message_load_opponents);
        QBUsers.getUsers(new QBPagedRequestBuilder()).performAsync(
            new QBEntityCallback<ArrayList<QBUser>>() {
                @Override
                public void onSuccess(ArrayList<QBUser> qbUsers, Bundle bundle) {
                    hideProgressDialog();
                    mDbManager.saveAllUsers(qbUsers, true);
                    initOpponentsList(qbUsers);
                }

                @Override
                public void onError(QBResponseException e) {
                    hideProgressDialog();
                    Toast.makeText(OpponentActivity.this,
                        "Error Loading", Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void initOpponentsList(ArrayList<QBUser> qbUsers) {
        mOpponentsList = qbUsers;
        mRecyclerOpponents.
            setLayoutManager(new LinearLayoutManager(OpponentActivity.this));
        mOpponentAdapter = new OpponentAdapter(OpponentActivity.this, mOpponentsList);
        mRecyclerOpponents.setAdapter(mOpponentAdapter);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getExtras() != null) {
            isRunForCall = intent.getExtras().getBoolean(Constants.EXTRA_IS_STARTED_FOR_CALL);
            if (isRunForCall && mWebRtcSessionManager.getCurrentSession() != null) {
                CallActivity.start(OpponentActivity.this, true);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_opponents, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            default:
                return super.onOptionsItemSelected(item);
            case R.id.menu_refresh:
                refreshOpponentList();
                return true;
            case R.id.menu_logout:
                logout();
                return true;
            case R.id.menu_setting:
                showSettings();
                return true;
            case R.id.menu_call:
                if (isLoggedInChat()) {
                    startCall(true);
                }
                if (mChecker.lacksPermissions(Constants.PERMISSIONS)) {
                    startPermissionsActivity(false);
                }
                return true;
        }
    }

    private void showSettings() {
        SettingsActivity.start(this);
    }

    private void startPermissionsActivity(boolean checkOnlyAudio) {
        PermissionsActivity.startActivity(this, checkOnlyAudio, Constants.PERMISSIONS);
    }

    private void startCall(boolean isVideoCall) {
        ArrayList<Integer> opponentsList = mOpponentAdapter.getSelectedOpponentsList();

        if(!opponentsList.isEmpty()){
            QBRTCTypes.QBConferenceType conferenceType = isVideoCall
                ? QBRTCTypes.QBConferenceType.QB_CONFERENCE_TYPE_VIDEO
                : QBRTCTypes.QBConferenceType.QB_CONFERENCE_TYPE_AUDIO;
            QBRTCClient qbrtcClient = QBRTCClient.getInstance(getApplicationContext());
            QBRTCSession newQbRtcSession =
                qbrtcClient.createNewSessionWithOpponents(opponentsList, conferenceType);
            WebRtcSessionManager.getInstance(this).setCurrentSession(newQbRtcSession);
            CallActivity.start(this, false);
        } else {
            Toast.makeText(this, "No user chosen", Toast.LENGTH_SHORT).show();
        }

    }

    private boolean isLoggedInChat() {
        if (!QBChatService.getInstance().isLoggedIn()) {
            Toast.makeText(this, "Connection Error", Toast.LENGTH_SHORT).show();
            tryReLoginToChat();
            return false;
        }
        return true;
    }

    private void tryReLoginToChat() {
        QBUser qbUser = mSharedPrefsHelper.getQbUser();
        CallService.start(this, qbUser);
    }

    private void refreshOpponentList() {
        showProgressDialog(R.string.message_load_opponents);
        QBUsers.getUsers(new QBPagedRequestBuilder()).performAsync(
            new QBEntityCallback<ArrayList<QBUser>>() {
                @Override
                public void onSuccess(ArrayList<QBUser> qbUsers, Bundle bundle) {
                    hideProgressDialog();
                    if (!mOpponentsList.equals(qbUsers)) {
                        mOpponentsList.clear();
                        mOpponentsList.addAll(qbUsers);
                        mOpponentAdapter.notifyDataSetChanged();
                        mDbManager.saveAllUsers(qbUsers, true);
                    } else {
                        Toast.makeText(OpponentActivity.this,
                            "Nothing changed", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onError(QBResponseException e) {
                    hideProgressDialog();
                    Toast.makeText(OpponentActivity.this,
                        "Error Loading", Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void logout() {
        CallService.logout(this);
        mSharedPrefsHelper.clearAllData();
        LoginActivity.start(this);
        finish();
    }
}
