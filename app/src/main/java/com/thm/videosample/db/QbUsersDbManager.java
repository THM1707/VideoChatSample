package com.thm.videosample.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.quickblox.users.model.QBUser;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by tereha on 17.05.16.
 */
public class QbUsersDbManager {
    private static String TAG = QbUsersDbManager.class.getSimpleName();

    private static QbUsersDbManager instance;
    private Context mContext;

    private QbUsersDbManager(Context context) {
        this.mContext = context;
    }

    public static QbUsersDbManager getInstance(Context context) {
        if (instance == null) {
            instance = new QbUsersDbManager(context);
        }

        return instance;
    }

    public ArrayList<QBUser> getAllUsers() {
        ArrayList<QBUser> allUsers = new ArrayList<>();
        DbHelper dbHelper = new DbHelper(mContext);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor c = db.query(DbHelper.DB_TABLE_NAME, null, null, null, null, null, null);

        if (c.moveToFirst()) {
            int userIdColIndex = c.getColumnIndex(DbHelper.DB_COLUMN_USER_ID);
            int userLoginColIndex = c.getColumnIndex(DbHelper.DB_COLUMN_USER_LOGIN);
            int userFullNameColIndex = c.getColumnIndex(DbHelper.DB_COLUMN_USER_FULL_NAME);

            do {
                QBUser qbUser = new QBUser();

                qbUser.setFullName(c.getString(userFullNameColIndex));
                qbUser.setLogin(c.getString(userLoginColIndex));
                qbUser.setId(c.getInt(userIdColIndex));

                allUsers.add(qbUser);
            } while (c.moveToNext());
        }

        c.close();
        dbHelper.close();

        return allUsers;
    }

    public QBUser getUserById(Integer userId) {
        QBUser qbUser = null;
        DbHelper dbHelper = new DbHelper(mContext);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor c = db.query(DbHelper.DB_TABLE_NAME, null, null, null, null, null, null);

        if (c.moveToFirst()) {
            int userIdColIndex = c.getColumnIndex(DbHelper.DB_COLUMN_USER_ID);
            int userLoginColIndex = c.getColumnIndex(DbHelper.DB_COLUMN_USER_LOGIN);
            int userFullNameColIndex = c.getColumnIndex(DbHelper.DB_COLUMN_USER_FULL_NAME);

            do {
                if (c.getInt(userIdColIndex) == userId) {
                    qbUser = new QBUser();
                    qbUser.setFullName(c.getString(userFullNameColIndex));
                    qbUser.setLogin(c.getString(userLoginColIndex));
                    qbUser.setId(c.getInt(userIdColIndex));
                    break;
                }
            } while (c.moveToNext());
        }

        c.close();
        dbHelper.close();

        return qbUser;
    }

    public void saveAllUsers(ArrayList<QBUser> allUsers, boolean needRemoveOldData) {
        if (needRemoveOldData) {
            clearDB();
        }

        for (QBUser qbUser : allUsers) {
            saveUser(qbUser);
        }
        Log.d(TAG, "saveAllUsers");
    }

    public void saveUser(QBUser qbUser) {
        ContentValues cv = new ContentValues();
        DbHelper dbHelper = new DbHelper(mContext);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        cv.put(DbHelper.DB_COLUMN_USER_FULL_NAME, qbUser.getFullName());
        cv.put(DbHelper.DB_COLUMN_USER_LOGIN, qbUser.getLogin());
        cv.put(DbHelper.DB_COLUMN_USER_ID, qbUser.getId());

        db.insert(DbHelper.DB_TABLE_NAME, null, cv);
        dbHelper.close();
    }

    public void clearDB() {
        DbHelper dbHelper = new DbHelper(mContext);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(DbHelper.DB_TABLE_NAME, null, null);
        dbHelper.close();
    }

    public ArrayList<QBUser> getUsersByIds(List<Integer> usersIds) {
        ArrayList<QBUser> qbUsers = new ArrayList<>();

        for (Integer userId : usersIds) {
            if (getUserById(userId) != null) {
                qbUsers.add(getUserById(userId));
            }
        }

        return qbUsers;
    }
}

