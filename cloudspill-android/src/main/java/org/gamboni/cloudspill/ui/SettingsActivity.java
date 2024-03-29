package org.gamboni.cloudspill.ui;


import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.support.v7.app.ActionBar;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;

import org.gamboni.cloudspill.job.CloudSpillIntentService;
import org.gamboni.cloudspill.R;
import org.gamboni.cloudspill.shared.api.ServerInfo;
import org.gamboni.cloudspill.file.FileBuilder;

import java.io.File;

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

    private static final String PREF_USER_KEY = "pref_user";
    public static String getUser(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_USER_KEY, "");
    }

    private static final String PREF_DOWNLOAD_PATH_KEY = "pref_dl_path";
    public static FileBuilder getDownloadPath(Context context) {
        // TODO use SAF here as well
        return new FileBuilder.FileBased(
                context,
                new File(
                PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_DOWNLOAD_PATH_KEY, "")));
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
    public enum ConnectionType {
        WIFI, MOBILE
    }

    private static String PREF_MOBILE_UPLOAD = "pref_mobile_upload";
    public enum PrefMobileUpload {
        NEVER {
            public boolean shouldRun(CloudSpillIntentService.Trigger trigger) {
                return false;
            }
        }, APP_OPEN {
            public boolean shouldRun(CloudSpillIntentService.Trigger trigger) {
                return trigger == CloudSpillIntentService.Trigger.FOREGROUND ||
                        trigger == CloudSpillIntentService.Trigger.MANUAL;
            }
        }, ANYTIME {
            public boolean shouldRun(CloudSpillIntentService.Trigger trigger) {
                return true;
            }
        };

        public abstract boolean shouldRun(CloudSpillIntentService.Trigger trigger);
    }
    public static PrefMobileUpload getMobileUpload(Context context) {
        return PrefMobileUpload.valueOf(PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_MOBILE_UPLOAD, PrefMobileUpload.NEVER.name()));
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences("values", Context.MODE_PRIVATE);
    }

    private static final String LATEST_UPDATE_KEY = "latest_update";
    public static long getLatestUpdate(Context context) {
        return getSharedPreferences(context).getLong(LATEST_UPDATE_KEY, 0L);
    }
    public static void setLatestUpdate(Context context, long latestUpdate) {
        getSharedPreferences(context).edit()
                .remove("highest_id") // deprecated key
                .putLong(LATEST_UPDATE_KEY, latestUpdate)
                .apply();
    }

    private static final String LAST_SERVER_VERSION_KEY = "last_server_version";
    private static final String LAST_SERVER_URL_KEY = "last_server_url";
    public static ServerInfo getLastServerVersion(Context context) {
        return ServerInfo.online(
                getSharedPreferences(context).getInt(LAST_SERVER_VERSION_KEY, -1),
                getSharedPreferences(context).getString(LAST_SERVER_URL_KEY, null));
    }
    public static void setLastServerVersion(Context context, ServerInfo serverInfo) {
        getSharedPreferences(context).edit()
                .putInt(LAST_SERVER_VERSION_KEY, serverInfo.getVersion())
                .putString(LAST_SERVER_URL_KEY, serverInfo.getApi().getBaseUrl())
                .apply();
    }

    private static final String AUTHENTICATION_TOKEN_KEY = "authentication_token";
    public static String getAuthenticationToken(Context context) {
        return getSharedPreferences(context).getString(AUTHENTICATION_TOKEN_KEY, "");
    }

    public static void setAuthenticationToken(Context context, String token) {
        getSharedPreferences(context).edit()
                .putString(AUTHENTICATION_TOKEN_KEY, token)
                .apply();
    }

    public static class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            // Set summary based on current value
            onSharedPreferenceChanged(PreferenceManager.getDefaultSharedPreferences(getActivity()), PREF_FREESPACE_KEY);
            onSharedPreferenceChanged(PreferenceManager.getDefaultSharedPreferences(getActivity()), PREF_USER_KEY);
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
                case PREF_USER_KEY:
                    String user = sharedPreferences.getString(key, "");
                    if (!user.isEmpty()) { // && !server.equals(connectionPref.))
                        preference.setSummary("Connect to the server as "+ user);
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
