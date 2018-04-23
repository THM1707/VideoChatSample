package com.thm.videosample.activity;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.quickblox.auth.session.QBSettings;
import com.quickblox.core.QBEntityCallback;
import com.quickblox.core.StoringMechanism;
import com.quickblox.core.exception.QBResponseException;
import com.quickblox.users.QBUsers;
import com.quickblox.users.model.QBUser;
import com.thm.videosample.R;
import com.thm.videosample.service.CallService;
import com.thm.videosample.utils.Constants;
import com.thm.videosample.utils.SharedPrefsHelper;

public class LoginActivity extends BaseActivity implements View.OnClickListener {
    private EditText mEditLogin;
    private EditText mEditPassword;
    private SharedPrefsHelper mSharedPrefsHelper;

    public static void start(Context context) {
        Intent intent = new Intent(context, LoginActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        setField();
        setView();
        initFramework();
    }

    private void setField() {
        mSharedPrefsHelper = SharedPrefsHelper.getInstance(this);
    }

    private void setView() {
        mEditLogin = findViewById(R.id.et_login);
        mEditPassword = findViewById(R.id.et_password);
        findViewById(R.id.bt_login).setOnClickListener(this);
        findViewById(R.id.tv_sign_up).setOnClickListener(this);
    }

    private void initFramework() {
        QBSettings.getInstance().setStoringMehanism(StoringMechanism.UNSECURED);
        QBSettings.getInstance().init(this, Constants.APP_ID, Constants.AUTH_KEY,
            Constants.AUTH_SECRET);
        QBSettings.getInstance().setAccountKey(Constants.ACCOUNT_KEY);
    }


    private void startLoginService(QBUser qbUser) {
        Intent tempIntent = new Intent(this, CallService.class);
        PendingIntent pendingIntent =
            createPendingResult(Constants.EXTRA_LOGIN_RESULT_CODE, tempIntent, 0);
        CallService.start(this, qbUser, pendingIntent);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bt_login:
                String login = mEditLogin.getText().toString();
                String password = mEditPassword.getText().toString();
                showProgressDialog(R.string.message_signing_in);
                signInUser(login, password);
                break;
            case R.id.tv_sign_up:
                break;
            default:
                break;
        }
    }

    private void signInUser(String login, String password) {
        final QBUser user = new QBUser(login, password);
        QBUsers.signIn(user).performAsync(new QBEntityCallback<QBUser>() {
            @Override
            public void onSuccess(QBUser qbUser, Bundle bundle) {
                user.setFullName(qbUser.getFullName());
                mSharedPrefsHelper.saveQbUser(user);
                startLoginService(user);
            }

            @Override
            public void onError(QBResponseException e) {
                Toast.makeText(LoginActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                hideProgressDialog();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Constants.EXTRA_LOGIN_RESULT_CODE) {
            hideProgressDialog();
            boolean isLoginSuccess = data.getBooleanExtra(Constants.EXTRA_LOGIN_RESULT, false);
            String errorMessage = data.getStringExtra(Constants.EXTRA_LOGIN_ERROR_MESSAGE);
            if (isLoginSuccess) {
                OpponentActivity.start(this, false);
                finish();
            } else {
                Toast.makeText(this, "Login to chat error " + errorMessage
                    , Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
