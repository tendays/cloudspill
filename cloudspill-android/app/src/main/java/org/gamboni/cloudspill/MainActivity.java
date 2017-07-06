package org.gamboni.cloudspill;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.widget.ProgressBar;
import android.widget.Toast;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.server.CloudSpillServerProxy;
import org.gamboni.cloudspill.server.ConnectivityTestRequest;
import org.gamboni.cloudspill.ui.GridViewAdapter;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_MESSAGE = "org.gamboni.cloudspill.MESSAGE";
    private static final String TAG = "CloudSpill.Main";

    private static final String localFolder = "/storage/emulated/0/DCIM/Tnotecamera";

    private Domain domain;

    private enum PermissionRequest {
        READ_EXTERNAL_STORAGE
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.domain = new Domain(MainActivity.this);

        HorizontalGridView gridView = (HorizontalGridView) findViewById(R.id.gridView);
        GridViewAdapter adapter = new GridViewAdapter(this, new File(localFolder), domain);
        gridView.setAdapter(adapter);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

/*        Domain.Item item = domain.selectItems().get(3);
        GlideApp.with(this).load(Uri.fromFile(new File(new File(localFolder), item.path)))
                .placeholder(R.drawable.lb_ic_in_app_search)
                .into((ImageView)findViewById(R.id.testImageView));
*/

        /* Request read access to external storage */
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Read permission granted already");
            runBatch();
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
                        PermissionRequest.READ_EXTERNAL_STORAGE.ordinal());

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        Log.d(TAG, "Permission request result for code "+ requestCode);
        switch (PermissionRequest.values()[requestCode]) {
            case READ_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay!
                    Log.d(TAG, "Read permission was granted by user");
                    runBatch();

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }
        }
    }

    private void runBatch() {
        final ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
        new Thread(new Runnable() {
            public void run() {

/*                Log.d(TAG, Environment.getExternalStorageDirectory().toString());
                Log.d(TAG, Environment.getExternalStorageState());
                Log.d(TAG, Environment.getRootDirectory().toString());
                Log.d(TAG, Environment.getDataDirectory().toString());
                */
                CloudSpillServerProxy server = new CloudSpillServerProxy(MainActivity.this);
                final DirectoryScanner ds = new DirectoryScanner(MainActivity.this, domain, server, new File(localFolder),
                        new DirectoryScanner.StatusReport() {
                            @Override
                            public void updatePercent(final int percent) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        progressBar.setProgress(percent);
                                    }
                                });
                            }
                        });
                ds.run();
                ds.waitForCompletion();
                domain.close();
            }
        }).start();
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
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /** Called when the user taps the Send imageView */
    /*public void sendMessage(View view) {
        Intent intent = new Intent(this, DisplayMessageActivity.class);
        EditText editText = (EditText) findViewById(R.id.editText);
        String message = editText.getText().toString();
        intent.putExtra(EXTRA_MESSAGE, message);
        startActivity(intent);
    }*/

}
