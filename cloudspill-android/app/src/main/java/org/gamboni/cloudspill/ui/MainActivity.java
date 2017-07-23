package org.gamboni.cloudspill.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v17.leanback.widget.HorizontalGridView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.gamboni.cloudspill.CloudSpillIntentService;
import org.gamboni.cloudspill.R;
import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.message.StatusReport;

public class MainActivity extends AppCompatActivity implements StatusReport {
    public static final String EXTRA_MESSAGE = "org.gamboni.cloudspill.MESSAGE";
    private static final String TAG = "CloudSpill.Main";

    private Domain domain;

    public enum PermissionRequest {
        READ_EXTERNAL_STORAGE
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.domain = new Domain(MainActivity.this);

        HorizontalGridView gridView = (HorizontalGridView) findViewById(R.id.gridView);
        GridViewAdapter adapter = new GridViewAdapter(this, domain);
        gridView.setAdapter(adapter);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

/*        Domain.Item item = domain.selectItems().get(3);
        GlideApp.with(this).load(Uri.fromFile(new File(new File(localFolder), item.path)))
                .placeholder(R.drawable.lb_ic_in_app_search)
                .into((ImageView)findViewById(R.id.testImageView));
*/
        sync();
    }

    public void sync(View view) {
        sync();
    }

    /** Check if necessary permissions are available, then start the synchronisation service. */
    private void sync() {
        /* Request read access to external storage */
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Read permission granted already");
            startService();
        } else {
            Log.d(TAG, "Read permission denied.");

            // Should we show an explanation?
           if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)) {
                Toast.makeText(this, "I need to read external storage so I can back it up", Toast.LENGTH_LONG).show();
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

            // No explanation needed, we can request the permission.
            Log.d(TAG, "Requesting read permissions...");

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    MainActivity.PermissionRequest.READ_EXTERNAL_STORAGE.ordinal());
           }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        Log.d(TAG, "Permission request result for code "+ requestCode);
        switch (MainActivity.PermissionRequest.values()[requestCode]) {
            case READ_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay!
                    Log.d(TAG, "Read permission was granted by user");

                    startService();

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }
        }
    }

    /** Start the synchronisation service. Don't call this method directly, but use sync() instead
     * so that necessary permissions may be checked first.
     */
    private void startService() {
        startService(new Intent(this, CloudSpillIntentService.class));}

    Severity highestSeverity = Severity.INFO;

    @Override
    public void onStart() {
        super.onStart();
        CloudSpillIntentService.setListener(this);
    }

    @Override
    public void updatePercent(final int percent) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
                progressBar.setProgress(percent);
            }
        });
    }

    @Override
    public void updateMessage(final Severity severity, final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (severity.compareTo(highestSeverity) >= 0) {
                    final TextView messageView = (TextView) findViewById(R.id.statusMessage);
                    messageView.setText(message);
                    if (severity == Severity.ERROR) {
                        messageView.setTextColor(Color.RED);
                    } else {
                        messageView.setTextColor(Color.BLACK);
                    }
                    highestSeverity = severity;
                }
            }
        });
    }

    @Override
    public void onPause() {
        CloudSpillIntentService.unsetListener(this);
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.folders:
                startActivity(new Intent(this, FoldersActivity.class));
                return true;
            case R.id.servers:
                startActivity(new Intent(this, ServersActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /** Called when the user taps the Send imageView */
    /*public void sendMessage(View view) {
        Intent intent = new Intent(this, FoldersActivity.class);
        EditText editText = (EditText) findViewById(R.id.editText);
        String message = editText.getText().toString();
        intent.putExtra(EXTRA_MESSAGE, message);
        startActivity(intent);
    }*/

}
