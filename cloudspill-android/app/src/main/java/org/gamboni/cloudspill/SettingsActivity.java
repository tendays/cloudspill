package org.gamboni.cloudspill;


import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.support.v7.app.ActionBar;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;

import org.gamboni.cloudspill.file.FileBuilder;

import java.io.File;
import java.util.List;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends AppCompatPreferenceActivity {

    private static final String TAG = "CloudSpill.Settings";

    private static final String PREF_SERVER_KEY = "pref_server";
    public static String getServerUrl(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_SERVER_KEY, "");
    }
    private static final String PREF_USER_KEY = "pref_user";
    public static String getUser(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_USER_KEY, "");
    }
    private static final String PREF_FOLDER_KEY = "pref_folder";
    public static String getFolder(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_FOLDER_KEY, "");
    }
    private static final String PREF_FOLDER_PATH_KEY = "pref_folder_path";
    public static FileBuilder getFolderPath(Context context) {
        return new FileBuilder(PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_FOLDER_PATH_KEY, ""));
    }
    private static final String PREF_DOWNLOAD_PATH_KEY = "pref_download_path";
    public static FileBuilder getDownloadPath(Context context) {
        return new FileBuilder(PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_DOWNLOAD_PATH_KEY, ""));
    }
    private static final String PREF_FREESPACE_KEY = "pref_freespace";
    public static long getMinSpaceBytes(Context context) {
        String value = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_FREESPACE_KEY, "");
        if (value.endsWith("K")) {
            return 1024L * Long.parseLong(value.substring(0, value.length() - 1));
        } else if (value.endsWith("M")) {
            return 1024L * 1024 * Long.parseLong(value.substring(0, value.length() - 1));
        } else if (value.endsWith("G")) {
            return 1024L * 1024 * 1024 * Long.parseLong(value.substring(0, value.length() - 1));
        } else {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Free space preference not set or invalid: "+ value, e);
                return 0;
            }
        }
    }

    public static class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            // Set summary based on current value
            onSharedPreferenceChanged(PreferenceManager.getDefaultSharedPreferences(getActivity()), PREF_SERVER_KEY);
            onSharedPreferenceChanged(PreferenceManager.getDefaultSharedPreferences(getActivity()), PREF_FOLDER_KEY);
            onSharedPreferenceChanged(PreferenceManager.getDefaultSharedPreferences(getActivity()), PREF_FREESPACE_KEY);
            onSharedPreferenceChanged(PreferenceManager.getDefaultSharedPreferences(getActivity()), PREF_USER_KEY);
            onSharedPreferenceChanged(PreferenceManager.getDefaultSharedPreferences(getActivity()), PREF_FOLDER_PATH_KEY);
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                              String key) {
            Preference preference = findPreference(key);
            switch (key) {
                case PREF_SERVER_KEY:
                    String server = sharedPreferences.getString(key, "");
                    if (!server.isEmpty()) { // && !server.equals(connectionPref.))
                        preference.setSummary(server);
                        invalidateDatabase();
                    } // TODO else: help message
                    return;
                case PREF_USER_KEY:
                    String user = sharedPreferences.getString(key, "");
                    if (!user.isEmpty()) { // && !server.equals(connectionPref.))
                        preference.setSummary("Connect to the server as "+ user);
                        invalidateDatabase();
                    } // TODO else: help message
                    return;
                case PREF_FOLDER_KEY:
                    String folder = sharedPreferences.getString(key, "");
                    if (!folder.isEmpty()) { // && !server.equals(connectionPref.))
                        preference.setSummary("Local folder description: "+ folder);
                        invalidateDatabase();
                    } // TODO else: help message
                    return;
                case PREF_FOLDER_PATH_KEY:
                    String folderPath = sharedPreferences.getString(key, "");
                    if (!folderPath.isEmpty()) { // && !server.equals(connectionPref.))
                        preference.setSummary("Scan "+ folderPath +" for new media");
                        invalidateDatabase();
                    } // TODO else: help message
                    return;
                case PREF_FREESPACE_KEY:
                    String freeSpace = sharedPreferences.getString(key, "");
                    if (!freeSpace.isEmpty()) { // && !server.equals(connectionPref.))
                        preference.setSummary("Ensure at least "+ freeSpace +" is available on disk.");
                        invalidateDatabase();
                    } // TODO else: help message
                    return;
            }
        }

        private void invalidateDatabase() {
            // TODO invalidate database (but keep access times)
        }
    }

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();


        // Display the fragment as the main content. (from tutorial (?))
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }
}
