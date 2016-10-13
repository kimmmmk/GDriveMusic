package com.lgcns.gdrivemusic;

import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.util.Log;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.OpenFileActivityBuilder;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;
import com.google.android.gms.drive.DriveApi.MetadataBufferResult;


public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private DriveId mSelectedFileDriveId;

    private static final String TAG = MainActivity.class.getName() ;
    private static final int REQUEST_CODE_RESOLUTION = 1;
    private static final int REQUEST_CODE_OPENER = 2;

    private GoogleApiClient mGoogleApiClient;

    private ListView mResultsListView;
    private ResultsAdapter mResultsAdapter;
    private Button mActionButton;
    private ProgressBar mProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //setContentView(R.layout.content_main);
        setContentView(R.layout.activity_main);
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }

        mResultsListView = (ListView) findViewById(R.id.listViewResults);
        mResultsAdapter = new ResultsAdapter(this);
        mResultsListView.setAdapter(mResultsAdapter);

        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mProgressBar.setMax(100);

        mActionButton = (Button) findViewById(R.id.buttonDownload);
        mActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mp3select();
            }
        });

    }

    private void mp3select() {
        IntentSender intentSender = Drive.DriveApi
                .newOpenFileActivityBuilder()
                .setMimeType(new String[]{ "audio/mpeg"  })
                .build(mGoogleApiClient);
        try {
            startIntentSenderForResult(intentSender, REQUEST_CODE_OPENER, null, 0, 0, 0);
        } catch (IntentSender.SendIntentException e) {
            Log.w(TAG, "Unable to send intent", e);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mResultsAdapter.clear();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .addScope(Drive.SCOPE_APPFOLDER) // required for App Folder sample
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
        mGoogleApiClient.connect();
    }


    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(TAG, "GoogleApiClient connection failed: " + connectionResult.toString());
        if (!connectionResult.hasResolution()) {
            // show the localized error dialog.
            GoogleApiAvailability.getInstance().getErrorDialog(this, connectionResult.getErrorCode(), 0).show();
            return;
        }
        try {
            connectionResult.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Exception while starting resolution activity", e);
        }
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_RESOLUTION:
                if(resultCode == RESULT_OK) {
                    mGoogleApiClient.connect();
                }
                break;
            case REQUEST_CODE_OPENER:
                if(resultCode == RESULT_OK) {
                    mSelectedFileDriveId = (DriveId) data.getParcelableExtra(
                            OpenFileActivityBuilder.EXTRA_RESPONSE_DRIVE_ID);                }
                break;
        }
    }


    @Override
    protected void onPause() {
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
        super.onPause();
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "API client connected.");

        refresh();

        if (mSelectedFileDriveId != null) {
            open();
            return;
        }
    }

    private void refresh() {
        Query query = new Query.Builder()
                .addFilter(Filters.eq(SearchableField.MIME_TYPE, "audio/mpeg"))
                .build();
        Drive.DriveApi.query(mGoogleApiClient, query)
                .setResultCallback(metadataCallback);
    }

    private void open() {

        mProgressBar.setProgress(0);
        DriveFile.DownloadProgressListener listener = new DriveFile.DownloadProgressListener() {
            @Override
            public void onProgress(long bytesDownloaded, long bytesExpected) {
                // Update progress dialog with the latest progress.
                int progress = (int)(bytesDownloaded*100/bytesExpected);
                Log.d(TAG, String.format("Loading progress: %d percent", progress));
                mProgressBar.setProgress(progress);
            }
        };
        DriveFile driveFile =  mSelectedFileDriveId.asDriveFile();
        driveFile.open(mGoogleApiClient, DriveFile.MODE_READ_ONLY, listener)
                .setResultCallback(driveContentsCallback);
        mSelectedFileDriveId = null;
    }

    public void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }


    @Override
    public void onConnectionSuspended(int cause) {
        Log.i(TAG, "GoogleApiClient connection suspended");
    }

    final private ResultCallback<MetadataBufferResult> metadataCallback = new
            ResultCallback<MetadataBufferResult>() {
                @Override
                public void onResult(MetadataBufferResult result) {
                    if (!result.getStatus().isSuccess()) {
                        Log.i(TAG, "Problem while retrieving results");
                        return;
                    }
                    Log.i(TAG, "get item");
                    mResultsAdapter.clear();
                    mResultsAdapter.append(result.getMetadataBuffer());
                }
            };

    private ResultCallback<DriveApi.DriveContentsResult> driveContentsCallback =
            new ResultCallback<DriveApi.DriveContentsResult>() {
                @Override
                public void onResult(DriveApi.DriveContentsResult result) {
                    if (!result.getStatus().isSuccess()) {
                        showMessage("Error while opening the file contents");
                        return;
                    }
                    showMessage("File contents opened");
                    refresh(); //refresh
                }
            };

}
