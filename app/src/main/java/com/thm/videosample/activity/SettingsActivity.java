package com.thm.videosample.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.widget.Toast;

import com.thm.videosample.R;
import com.thm.videosample.fragment.SettingsFragment;
import com.thm.videosample.view.SeekBarPreference;

/**
 * QuickBlox team
 */
public class SettingsActivity extends BaseActivity
    implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int MAX_VIDEO_START_BITRATE = 2000;
    private String bitrateStringKey;
    private SettingsFragment settingsFragment;

    public static void start(Context context) {
        Intent intent = new Intent(context, SettingsActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Display the fragment as the main content.
        settingsFragment = new SettingsFragment();
        getFragmentManager().beginTransaction()
            .replace(android.R.id.content, settingsFragment)
            .commit();
        bitrateStringKey = getString(R.string.pref_startbitratevalue_key);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences =
            settingsFragment.getPreferenceScreen().getSharedPreferences();
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences sharedPreferences =
            settingsFragment.getPreferenceScreen().getSharedPreferences();
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(bitrateStringKey)) {
            int bitrateValue = sharedPreferences.getInt(bitrateStringKey, Integer.parseInt(
                getString(R.string.pref_startbitratevalue_default)));
            if (bitrateValue == 0) {
                setDefaultstartingBitrate(sharedPreferences);
                return;
            }
            int startBitrate = bitrateValue;
            if (startBitrate > MAX_VIDEO_START_BITRATE) {
                Toast.makeText(this,
                    "Max value is:" + MAX_VIDEO_START_BITRATE, Toast.LENGTH_SHORT).show();
                setDefaultstartingBitrate(sharedPreferences);
            }
        }
    }

    private void setDefaultstartingBitrate(SharedPreferences sharedPreferences) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(bitrateStringKey,
            Integer.parseInt(getString(R.string.pref_startbitratevalue_default)));
        editor.apply();
        updateSummary(sharedPreferences, bitrateStringKey);
    }

    private void updateSummary(SharedPreferences sharedPreferences, String key) {
        Preference updatedPref = settingsFragment.findPreference(key);
        // Set summary to be the user-description for the selected value
        if (updatedPref instanceof EditTextPreference) {
            ((EditTextPreference) updatedPref).setText(sharedPreferences.getString(key, ""));
        } else if (updatedPref instanceof SeekBarPreference) {
            updatedPref.setSummary(String.valueOf(sharedPreferences.getInt(key, 0)));
        } else {
            updatedPref.setSummary(sharedPreferences.getString(key, ""));
        }
    }
}