package com.thm.videosample.utils;

import android.Manifest;

public class Constants {
    public static final String APP_ID = "69847";
    public static final String AUTH_KEY = "UymZFzZSADXcpMH";
    public static final String AUTH_SECRET = "HN5CrJSfJrntH5q";
    public static final String ACCOUNT_KEY = "zte5VNHABZyJEcRU2fCN";




    public static final String EXTRA_QB_USER = "qb_user";
    public static final String EXTRA_PENDING_INTENT = "pending_intent";
    public static final String EXTRA_COMMAND_TO_SERVICE = "service_command";
    public static final String EXTRA_LOGIN_RESULT = "login_result";
    public static final String EXTRA_LOGIN_ERROR_MESSAGE = "login_error_message";
    public static final String EXTRA_IS_STARTED_FOR_CALL = "isRunForCall";
    public static final String EXTRA_IS_INCOMING_CALL = "conversation_reason";
    public static final String[] PERMISSIONS =
        {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};



    public static final int COMMAND_LOGIN = 0;
    public static final int COMMAND_NOT_FOUND = 3;
    public static final int COMMAND_LOGOUT = 1;


    public static final int EXTRA_LOGIN_RESULT_CODE = 1000;
    public static final int MAX_OPPONENTS_COUNT = 6;
}
