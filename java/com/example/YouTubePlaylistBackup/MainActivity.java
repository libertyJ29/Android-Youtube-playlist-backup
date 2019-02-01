// YouTube Playlist Backup & Restore
// Allows user to save any public playlists to storage on their device, and to restore the playlist to their logged in YouTube account.

package com.example.YouTubePlaylistBackup;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.common.AccountPicker;
import com.google.api.services.youtube.model.PlaylistItemSnippet;
import com.google.api.services.youtube.model.PlaylistListResponse;
import com.google.api.services.youtube.model.ResourceId;
import com.google.gson.Gson;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTubeScopes;
import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.PlaylistSnippet;
import com.google.api.services.youtube.model.PlaylistStatus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends Activity
        implements Serializable, EasyPermissions.PermissionCallbacks {
    GoogleAccountCredential mCredential;

    private TextView loggedInNameTextView;
    private TextView outputText;
    private Button loginButton;
    private Button mBackupPlaylistButton;
    private Button mRestorePlaylistButton;
    private EditText playlistIdEditText;

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;

    private String playlistId = "PLWgtXOT6doCmuQrz2wICa7J-v_5bbzfGR";  // Predefined test playlist id
    private String access;  // Which button was pressed - backup or restore
    private String gmailAccount = "";
    private String appName = "com.example.YouTubePlaylistBackup";

    private static final String[] SCOPES = { YouTubeScopes.YOUTUBE_FORCE_SSL, YouTubeScopes.YOUTUBE_READONLY, YouTubeScopes.YOUTUBEPARTNER  };

    private Activity activity = this;

    private ArrayList<String> storedPlaylist;

    private int counter;

    private String channelTitle;
    private String playlistTitle;
    private String playlistDescription;

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    // Global instance of a YouTube object, which will be used to make YouTube Data API requests.
    private static YouTube youtube;
    // Global instance of the HTTP transport.
    public static HttpTransport HTTP_TRANSPORT = AndroidHttp.newCompatibleTransport();
    // Global instance of the JSON factory.
    public static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();


    /**
     * Create the main activity.
     * @param savedInstanceState previously saved instance data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.activity_main, null);
        setContentView(view);

        LinearLayout activityLayout = view.findViewById(R.id.layout);
        activityLayout.setPadding(16, 16, 16, 16);

        loginButton = findViewById(R.id.loginButton);
        loginButton.setText("Login with Google Account");
        loginButton.setTransformationMethod(null);   // Lower case text
        loginButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.baseline_person_black_18dp, 0, 0, 0);
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                    getGmailAccount();
            }
        });

        loggedInNameTextView = findViewById(R.id.loggedInNameTextView);
        loggedInNameTextView.setText("Not Logged in!\n");

        mBackupPlaylistButton = findViewById(R.id.mBackupPlaylistButton);
        mBackupPlaylistButton.setText("Backup YouTube Playlist");
        mBackupPlaylistButton.setTransformationMethod(null);  // Lower case text
        mBackupPlaylistButton.setCompoundDrawablesWithIntrinsicBounds(com.example.YouTubePlaylistBackup.R.drawable.baseline_cloud_download_black_18dp, 0, 0, 0);
        mBackupPlaylistButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mCredential != null) {
                    outputText.setText("");
                    access = "backup";

                    getResultsFromApi();
                }else
                {
                    Toast.makeText(activity, "Please login", Toast.LENGTH_SHORT).show();
                }
            }
        });

        mRestorePlaylistButton = findViewById(R.id.mRestorePlaylistButton);
        mRestorePlaylistButton.setText("Restore YouTube Playlist");
        mRestorePlaylistButton.setTransformationMethod(null);  // Lower case text
        mRestorePlaylistButton.setCompoundDrawablesWithIntrinsicBounds(com.example.YouTubePlaylistBackup.R.drawable.baseline_cloud_upload_black_18dp, 0, 0, 0);
        mRestorePlaylistButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mCredential != null) {
                    access = "restore";

                    getResultsFromApi();
                }else
                {
                    Toast.makeText(activity, "Please login", Toast.LENGTH_SHORT).show();
                }
            }
        });

        TextView playlistIdToBackupText = findViewById(R.id.playlistIdToBackupText);
        playlistIdToBackupText.setText("PlayList ID : ");

        playlistIdEditText = findViewById(R.id.playlistIdEditText);
        playlistIdEditText.setPadding(20, 20, 20, 20);
        playlistIdEditText.setHint("Enter playlist id");
        playlistIdEditText.setText(playlistId);
        playlistIdEditText.setTextSize(16);

        outputText = findViewById(R.id.outputText);
        outputText.setPadding(16, 16, 16, 16);
        outputText.setVerticalScrollBarEnabled(true);
        outputText.setMovementMethod(new ScrollingMovementMethod());
        outputText.setText("");

    }


    /**
     * Set the Google access credentials
     */
    private void setCredentials(){
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setSelectedAccount(new Account(gmailAccount, appName))
                .setBackOff(new ExponentialBackOff());

        mCredential.setSelectedAccountName(gmailAccount);

        loggedInNameTextView.setText("Logged in : "+mCredential.getSelectedAccountName()+"\n");
    }


    /**
     * Display Google Account picker - the selected Google account name is returned in onActivityResult
     */
    private void getGmailAccount() {
        startActivityForResult(
                AccountPicker.newChooseAccountIntent(null,
                        null, new String[]{GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE}, true, null, null, null, null),
                REQUEST_ACCOUNT_PICKER);
    }


    /**
     * Build and return an authorized API client service, such as a YouTube
     * Data API client service.
     * @return an authorized API client service
     * @throws IOException
     */
    public YouTube getYouTubeService() throws IOException {
            return new YouTube.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, mCredential)
                .setApplicationName(appName)
                .build();
    }


    /**
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */
    private void getResultsFromApi() {
        if (! isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (! isDeviceOnline()) {
            outputText.setText("No network connection available.");
        } else {
            new MakeRequestTask(mCredential).execute();
        }
    }


    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(appName, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }


    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode code indicating the result of the incoming
     *     activity result.
     * @param data Intent (containing result data) returned by incoming
     *     activity result.
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    outputText.setText(
                            "This app requires Google Play Services. Please install " +
                                    "Google Play Services on your device and relaunch this app.");
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    gmailAccount = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (gmailAccount != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(appName, gmailAccount);
                        editor.apply();

                        setCredentials();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {

                    getResultsFromApi();
                }
                break;
        }
    }


    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     * @param requestCode The request code passed in
     *     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    /**
     * Callback for when a permission is granted using the EasyPermissions
     * library.
     * @param requestCode The request code associated with the requested
     *         permission
     * @param list The requested permission list. Never null.
     */
    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Do nothing
    }


    /**
     * Callback for when a permission is denied using the EasyPermissions
     * library.
     * @param requestCode The request code associated with the requested
     *         permission
     * @param list The requested permission list. Never null.
     */
    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Do nothing
    }


    /**
     * Checks whether the device currently has a network connection.
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }


    /**
     * Check that Google Play services APK is installed and up to date.
     * @return true if Google Play Services is available and up to
     *     date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }


    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }


    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     * @param connectionStatusCode code describing the presence (or lack of)
     *     Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }


    /**
     * An asynchronous task that handles the YouTube Data API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private class MakeRequestTask extends AsyncTask<Void, Void, List<String>> {
        private Exception mLastError = null;

        MakeRequestTask(GoogleAccountCredential credential) {
            try {
                youtube = getYouTubeService();
            }catch (IOException e) {
                e.printStackTrace();
            }
        }


        /**
         * Background task to call YouTube Data API.
         * @param params no parameters needed for this task.
         */
        @Override
        protected List<String> doInBackground(Void... params) {
            try {

                playlistId = playlistIdEditText.getText().toString();  // Get user entered playlist id

                if(access == "restore") {
                    List<aPlaylistItem> ImportedStoredPlaylist = getPlaylistFromFile();  // Add playlist from device storage to a List
                    String playlistId = createNewPlaylist();  // Create a new playlist for the logged in YouTube account
                    List<String> successMessage = addItemsToPlaylist(playlistId, ImportedStoredPlaylist);  // Add the items to the newly created playlist

                    return successMessage;
                }else{
                    List<String> successMessage = loadYoutubePlaylist();
                    savePlaylistToFile();  // Save playlist as a file to device storage

                    return successMessage;
                }

            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }


        /**
         * Add each video item to the specified playlist for the logged in YouTube account
         */
        private List<String> addItemsToPlaylist(String playlistId, List<aPlaylistItem> ImportedStoredPlaylist){

            List<String> successMessage = new ArrayList<>();

            if(!playlistTitle.matches("Can't find a playlist with this Id")) {
                try {
                    for (counter = 0; counter < ImportedStoredPlaylist.size(); counter++) {
                        ResourceId resourceId = new ResourceId();
                        resourceId.setKind("youtube#video");
                        resourceId.setVideoId(ImportedStoredPlaylist.get(counter).getVideoId());

                        // Set fields included in the playlistItem resource's snippet
                        PlaylistItemSnippet playlistItemSnippet = new PlaylistItemSnippet();
                        playlistItemSnippet.setTitle(ImportedStoredPlaylist.get(counter).getTitle());
                        playlistItemSnippet.setPlaylistId(playlistId);
                        playlistItemSnippet.setResourceId(resourceId);

                        playlistItemSnippet.setPosition(Long.parseLong(ImportedStoredPlaylist.get(counter).getPosition()));

                        // Create the playlistItem resource and set it's snippet to the
                        // object created above
                        PlaylistItem playlistItem = new PlaylistItem();
                        playlistItem.setSnippet(playlistItemSnippet);

                        // Call the API to add the playlist item to the specified playlist.
                        // In the API call, the first argument identifies the resource parts
                        // that the API response should contain, and the second argument is
                        // the playlist item being inserted.
                        YouTube.PlaylistItems.Insert playlistItemsInsertCommand =
                                youtube.playlistItems().insert("snippet,contentDetails", playlistItem);

                        playlistItemsInsertCommand.execute();

                        // Display on screen (in the Ui thread) how many items have been added to the new playlist so far
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                outputText.setText("New playlist being created!\n\nAdded "+ counter +" items so far...");
                            }
                        });

                    }
                    successMessage.add("Success!\n\n"+counter + " Items added to your new playlist " + playlistTitle +" ("+channelTitle+")\n\nNew Playlist Id : "+playlistId);
                } catch (GoogleJsonResponseException e) {
                    e.printStackTrace();
                    successMessage.add("There was an error");
                    System.err.println("There was a service error: " + e.getDetails().getCode() + " : " + e.getDetails().getMessage());
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }else{
                successMessage.add("Could not create a playlist using this Playlist Id!");
            }
                return successMessage;
        }


        /**
         * Get the Title and Description of the specified playlist
         */
        private void getSpecifiedPlaylistMetaData(){
            try {
                // Get the title and description of the playlist
                PlaylistListResponse playlistListResponse = youtube.playlists().
                        list("snippet").setId(playlistId).execute();
                List<Playlist> playlistList = playlistListResponse.getItems();

                // If playlist returned is empty
                if (playlistList.isEmpty()) {
                    System.out.println("Can't find a playlist with ID : " + playlistId);
                    channelTitle = "No Channel for this playlist Id";
                    playlistTitle = "Can't find a playlist with this Id";
                }
                Playlist playlist = playlistList.get(0);

                //System.out.println("playlist="+playlist);
                channelTitle = playlist.getSnippet().getChannelTitle();
                playlistTitle = playlist.getSnippet().getTitle();
                playlistDescription = playlist.getSnippet().getDescription();

            }catch (GoogleJsonResponseException e) {
                e.printStackTrace();
                System.err.println("There was a service error: " + e.getDetails().getCode() + " : " + e.getDetails().getMessage());
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }


        /**
         * Create a new playlist in the logged in YouTube account
         */
        private String createNewPlaylist(){

            String playlistId = "";

            getSpecifiedPlaylistMetaData();

            if(!playlistTitle.matches("Can't find a playlist with this Id")) {
                try {
                    HashMap<String, String> parameters = new HashMap<>();
                    parameters.put("onBehalfOfContentOwner", "");

                    Playlist playlist = new Playlist();
                    PlaylistSnippet snippet = new PlaylistSnippet();
                    snippet.setTitle(playlistTitle + " (" + channelTitle + ")");
                    snippet.setDescription(playlistDescription);
                    PlaylistStatus status = new PlaylistStatus();
                    status.setPrivacyStatus("Public");

                    playlist.setSnippet(snippet);
                    playlist.setStatus(status);

                    YouTube.Playlists.Insert playlistsInsertRequest = youtube.playlists().insert("snippet,status", playlist);

                    if (parameters.containsKey("onBehalfOfContentOwner") && parameters.get("onBehalfOfContentOwner") != "") {
                        playlistsInsertRequest.setOnBehalfOfContentOwner(parameters.get("onBehalfOfContentOwner").toString());
                    }

                    Playlist response = playlistsInsertRequest.execute();

                    playlistId = response.getId();
                }catch (GoogleJsonResponseException e) {
                    e.printStackTrace();
                    System.err.println("There was a service error: " + e.getDetails().getCode() + " : " + e.getDetails().getMessage());
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }else{
                playlistId = "Can't find a playlist with this Id";
            }
            return playlistId;  // Id of the newly created playlist
        }


        /**
         *  Get the saved YouTube playlist items from local storage for the specified playlistId
         */
        private List<aPlaylistItem> getPlaylistFromFile() {

            List<String> restoredPlaylist = new ArrayList<String>();
            List<aPlaylistItem> ImportedStoredPlaylist = new ArrayList<>();

            // Ask user for access to device storage
            verifyStoragePermissions(activity);
            String extstoragedir = Environment.getExternalStorageDirectory().toString();
            String fileName = playlistId + "_playlist.txt";

            File folder = new File(extstoragedir, "SavedPlaylists");

            File plfile = new File(folder,  fileName);
            if (plfile.exists()) {

                try {
                    FileInputStream inputStream = new FileInputStream(plfile.getAbsolutePath());
                    ObjectInputStream ois = new ObjectInputStream(inputStream);

                    // Deserialize the JSON from file into a list
                    try {
                        ImportedStoredPlaylist = new ArrayList<>();

                        restoredPlaylist = (ArrayList) ois.readObject();

                        for (int i=0; i < restoredPlaylist.size(); i++) {
                            //System.out.println(restoredPlaylist.get(i));

                            // Convert each JSON entry to aPlaylistItem object and add to the ImportedStoredPlaylist list
                            ImportedStoredPlaylist.add(new Gson().fromJson(restoredPlaylist.get(i), aPlaylistItem.class));
                        }

                    } catch(Exception e) {
                        System.out.println("Error"+e);
                    }
                    ois.close();

                }
                catch (FileNotFoundException e) {
                    Log.e("Restore Playlist", "File not found: " + e.toString());
                } catch (IOException e) {
                    Log.e("Restore Playlist", "Can not read file: " + e.toString());
                }

            }else{
                // Display error on screen (in the Ui thread) with Toast
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(activity, "The specifed playlist is not backed up on this device!", Toast.LENGTH_LONG).show();
                    }
                });
            }

            return ImportedStoredPlaylist;
        }


        /**
         *  Save the specified YouTube playlist to local storage on users device
         */
        private void savePlaylistToFile(){
            try {
                // Verify that user gave permission to access their device storage
                verifyStoragePermissions(activity);
                String extstoragedir = Environment.getExternalStorageDirectory().toString();
                String fileName = playlistId+"_playlist.txt";

                File folder = new File(extstoragedir, "SavedPlaylists");
                if (!folder.exists()) {
                    boolean bool = folder.mkdir();
                }

                File output = new File(folder, fileName);

                if (output.exists()) {
                    try {
                        output.delete();
                    } catch (ActivityNotFoundException e) {
                        System.out.println("Unable to delete the old file!");
                    }
                }

                FileOutputStream fileout = new FileOutputStream(output.getAbsolutePath());
                ObjectOutputStream out = new ObjectOutputStream(fileout);
                out.writeObject(storedPlaylist);
                out.close();
                fileout.close();

            } catch (IOException e) {
                e.printStackTrace();
            }

        }


        /**
         *  Get the YouTube playlist for the user specified playlistId, and store in storedPlaylist
         */
        private List<String> loadYoutubePlaylist(){

            List<String> successMessage = new ArrayList<String>();

            counter = 0;  // Count number of playlist entries processed

            getSpecifiedPlaylistMetaData();
            successMessage.add("Channel : "+channelTitle+"\n");
            successMessage.add("Playlist Title : "+playlistTitle+"\n");

            try {

                HashMap<String, String> parameters = new HashMap<>();
                parameters.put("part", "snippet,contentDetails");
                parameters.put("maxResults", "20");  // Max possible value is 50
                parameters.put("playlistId", playlistId);

                String nextToken = "";

                YouTube.PlaylistItems.List playlistItemsListByPlaylistIdRequest = youtube.playlistItems().list(parameters.get("part").toString());

                if (parameters.containsKey("playlistId") && parameters.get("playlistId") != "") {
                    playlistItemsListByPlaylistIdRequest.setPlaylistId(parameters.get("playlistId").toString());
                }

                storedPlaylist = new ArrayList<>();

                successMessage.add("The following Playlist items have been backed up...\n");

                do {
                    playlistItemsListByPlaylistIdRequest.setPageToken(nextToken);

                    PlaylistItemListResponse response = playlistItemsListByPlaylistIdRequest.execute();

                    for (int i = 0; i < response.getItems().size(); i++ )
                    {
                        // Convert each playlist item into a JSON string and store in storedPlaylist
                        storedPlaylist.add(new aPlaylistItem(
                        response.getItems().get(i).getSnippet().getTitle(),
                        response.getItems().get(i).getSnippet().getDescription(),
                        response.getItems().get(i).getSnippet().getPosition(),
                        response.getItems().get(i).getSnippet().getResourceId().getVideoId()
                        ).toString());

                        // Display the title of each playlist item on screen
                        successMessage.add("("+(counter+1)+".) "+response.getItems().get(i).getSnippet().getTitle().toString()+"\n");

                        counter = counter + 1;
                    }

                    nextToken = response.getNextPageToken();

                } while (nextToken != null);

                successMessage.add("\nNumber of items : "+counter);  // Display number of items in the playlist

                return successMessage;

            } catch (GoogleJsonResponseException e) {
                e.printStackTrace();
                System.err.println("There was a service error: " + e.getDetails().getCode() + " : " + e.getDetails().getMessage());
            } catch (Throwable t) {
                t.printStackTrace();
            }

            successMessage.add("Error");

            return successMessage;
        }


        @Override
        protected void onPreExecute() {
            outputText.setText("");
        }

        @Override
        protected void onPostExecute(List<String> output) {
            if (output == null || output.size() == 0) {
                outputText.setText("No items returned for this playlist\n");
            } else {
                outputText.setText(TextUtils.join("\n", output));
            }
        }


        @Override
        protected void onCancelled() {
            if (mLastError != null) {

                System.out.println("Error!!");
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            MainActivity.REQUEST_AUTHORIZATION);
                } else {
                    outputText.setText("The following error occurred:\n"
                            + mLastError.getMessage());
                }
            } else {
                outputText.setText("Request cancelled.");
            }
        }
    }


    /**
     *  Ask user for permission to access storage on device
     */
    public static void verifyStoragePermissions(Activity activity) {
        // Check if have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // Prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }


}