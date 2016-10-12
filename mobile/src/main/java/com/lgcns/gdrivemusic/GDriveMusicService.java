package com.lgcns.gdrivemusic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.browse.MediaBrowser;
import android.media.browse.MediaBrowser.MediaItem;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.service.media.MediaBrowserService;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GDriveMusicService extends MediaBrowserService implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    public static  final  String TAG = GDriveMusicService.class.getName() ;
    private GoogleApiClient mGoogleApiClient;

    private static final String BROWSEABLE_ROOT = "root";
    private static final String BROWSEABLE_ROCK = "Rock";
    private static final String BROWSEABLE_JAZZ = "Jazz";
    private static final String BROWSEABLE_CAJUN = "Cajun";
    private static final String CURRENT_MEDIA_POSITION = "current_media_position";

    private MediaSession mMediaSession;
    private MediaSession.Token mMediaSessionToken;

    private List<Song> mSongs;

    private MediaPlayer mMediaPlayer;

    private MediaSession.Callback mMediaSessionCallback = new MediaSession.Callback() {
        @Override
        public void onPlay() {
            super.onPlay();

            toggleMediaPlaybackState( true );
            playMedia( PreferenceManager.getDefaultSharedPreferences( getApplicationContext() ).getInt( CURRENT_MEDIA_POSITION, 0 ), null );
        }

        //This is called when the pause button is pressed, or when onPlayFromMediaId is called in
        //order to pause any currently playing media
        @Override
        public void onPause() {
            super.onPause();

            toggleMediaPlaybackState( false );
            pauseMedia();
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            super.onPlayFromMediaId(mediaId, extras);
            Log.d ( TAG, "media ID:"+mediaId) ;

            initMediaMetaData( mediaId );
            toggleMediaPlaybackState( true );
            playMedia( 0, mediaId );
        }

        @Override
        public void onCustomAction(String action, Bundle extras) {
            super.onCustomAction(action, extras);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        if(mSongs == null) {
            Log.d(TAG, "create ArrayList");
            mSongs = new ArrayList<>();
        }

        if (mGoogleApiClient == null) {
            Log.d(TAG, "create google api client");

            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }

        mGoogleApiClient.connect();

        initMediaSession();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(TAG, "GoogleApiClient connection failed: " + connectionResult.toString());
//        if (!connectionResult.hasResolution()) {
//            // show the localized error dialog.
//            GoogleApiAvailability.getInstance().getErrorDialog(this, connectionResult.getErrorCode(), 0).show();
//            return;
//        }
//        try {
//            connectionResult.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
//        } catch (IntentSender.SendIntentException e) {
//            Log.e(TAG, "Exception while starting resolution activity", e);
//        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "API client connected.");

        Query query = new Query.Builder()
                .addFilter(Filters.eq(SearchableField.MIME_TYPE, "audio/mpeg"))
                .build();
        Drive.DriveApi.query(mGoogleApiClient, query)
                .setResultCallback(metadataCallback);
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.i(TAG, "GoogleApiClient connection suspended");
    }

    final private ResultCallback<DriveApi.MetadataBufferResult> metadataCallback = new
            ResultCallback<DriveApi.MetadataBufferResult>() {
                @Override
                public void onResult(DriveApi.MetadataBufferResult result) {
                    if (!result.getStatus().isSuccess()) {
                        Log.i(TAG, "Problem while retrieving results");
                        return;
                    }
                    if(mSongs != null) {
                        Log.i(TAG, "not null");

                        mSongs.clear();

                        MetadataBuffer buffer = result.getMetadataBuffer();
                        for (Metadata metadata : buffer) {
                            mSongs.add(new Song(metadata.getDriveId().encodeToString(), metadata.getTitle(), metadata.getTitle(), "GD", "Jazz", "http://", "http://"));
                        }
                    }


                }
            };



    private void initMediaSession() {
        mMediaSession = new MediaSession( this, "Android Auto Audio Demo" );
        mMediaSession.setActive( true );
        mMediaSession.setCallback( mMediaSessionCallback );

        mMediaSessionToken = mMediaSession.getSessionToken();
        setSessionToken( mMediaSessionToken );
    }

    private void initMediaMetaData( String id ) {

        for( Song song : mSongs ) {
            if( !TextUtils.isEmpty( song.getuId() ) && song.getuId().equalsIgnoreCase( id ) ) {
                MediaMetadata.Builder builder = new MediaMetadata.Builder();

                if( !TextUtils.isEmpty( song.getTitle() ) )
                    builder.putText( MediaMetadata.METADATA_KEY_TITLE, song.getTitle() );

                if( !TextUtils.isEmpty( song.getArtist() ) )
                    builder.putText( MediaMetadata.METADATA_KEY_ARTIST, song.getArtist() );

                if( !TextUtils.isEmpty( song.getGenre() ) )
                    builder.putText( MediaMetadata.METADATA_KEY_GENRE, song.getGenre() );

                if( !TextUtils.isEmpty( song.getAlbum() ) )
                    builder.putText( MediaMetadata.METADATA_KEY_ALBUM, song.getAlbum() );

                if( !TextUtils.isEmpty( song.getAlbumUrl() ) )
                    builder.putText( MediaMetadata.METADATA_KEY_ALBUM_ART_URI, song.getAlbumUrl() );

                mMediaSession.setMetadata( builder.build() );
            }
        }
    }

    private void toggleMediaPlaybackState( boolean playing ) {
        PlaybackState playbackState;
        if( playing ) {
            playbackState = new PlaybackState.Builder()
                    .setActions( PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS )
                    .setState( PlaybackState.STATE_PLAYING, 0, 1 )
                    .build();
        } else {
            playbackState = new PlaybackState.Builder()
                    .setActions( PlaybackState.ACTION_PLAY_PAUSE )
                    .setState(PlaybackState.STATE_PAUSED, 0, 1)
                    .build();
        }

        mMediaSession.setPlaybackState( playbackState );
    }

    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        //Can do package and other validation to determine if calling app has access to media items
        //in this service. If not, return null.
        return new BrowserRoot(BROWSEABLE_ROOT, null);
    }

    @Override
    public void onLoadChildren(String parentId, Result<List<MediaBrowser.MediaItem>> result) {

        List<MediaBrowser.MediaItem> items = getMediaItemsById( parentId );
        if( items != null ) {
            result.sendResult( items );
        }

    }

    private List<MediaBrowser.MediaItem> getMediaItemsById( String id ) {
        List<MediaBrowser.MediaItem> mediaItems = new ArrayList<MediaBrowser.MediaItem>();
        if( BROWSEABLE_ROOT.equalsIgnoreCase( id ) ) {
            mediaItems.add( generateBrowseableMediaItemByGenre(BROWSEABLE_JAZZ) );
        } else if( !TextUtils.isEmpty( id ) ) {
            return getPlayableMediaItemsByGenre( id );
        }

        return mediaItems;
    }

    private List<MediaBrowser.MediaItem> getPlayableMediaItemsByGenre( String genre ) {

        if( TextUtils.isEmpty( genre ) )
            return null;

        List<MediaBrowser.MediaItem> mediaItems = new ArrayList();

        for( Song song : mSongs ) {
            if( !TextUtils.isEmpty( song.getGenre() ) && genre.equalsIgnoreCase( song.getGenre() ) ) {
                mediaItems.add( generatePlayableMediaItem( song ) );
            }
        }
        return mediaItems;
    }


    private MediaBrowser.MediaItem generateBrowseableMediaItemByGenre( String genre ) {
        MediaDescription.Builder mediaDescriptionBuilder = new MediaDescription.Builder();
        mediaDescriptionBuilder.setMediaId( genre );
        mediaDescriptionBuilder.setTitle( genre );
        //mediaDescriptionBuilder.setIconBitmap( BitmapFactory.decodeResource( getResources(), R.drawable.folder ) );

        return new MediaBrowser.MediaItem( mediaDescriptionBuilder.build(), MediaBrowser.MediaItem.FLAG_BROWSABLE );
    }

    private MediaBrowser.MediaItem generatePlayableMediaItem( Song song ) {
        if( song == null )
            return null;

        MediaDescription.Builder mediaDescriptionBuilder = new MediaDescription.Builder();
        mediaDescriptionBuilder.setMediaId( song.getuId() );

        if( !TextUtils.isEmpty( song.getTitle() ) )
            mediaDescriptionBuilder.setTitle( song.getTitle() );

        if( !TextUtils.isEmpty( song.getArtist() ) )
            mediaDescriptionBuilder.setSubtitle( song.getArtist() );

        if( !TextUtils.isEmpty( song.getThumbnailUrl() ) )
            mediaDescriptionBuilder.setIconUri( Uri.parse( song.getThumbnailUrl() ) );

        return new MediaBrowser.MediaItem( mediaDescriptionBuilder.build(), MediaBrowser.MediaItem.FLAG_PLAYABLE );
    }

    private void playMedia( int position, String id ) {
        Log.i(TAG, "play id:"+id);

        if( mMediaPlayer != null )
            mMediaPlayer.reset();

        //Should check id to determine what to play in a real app
//        int songId = getApplicationContext().getResources().getIdentifier("geoff_ledak_dust_array_preview", "raw", getApplicationContext().getPackageName());
//        mMediaPlayer = MediaPlayer.create(getApplicationContext(), songId);

        if (mMediaPlayer == null) {
            Log.i(TAG, "create mediaplayer");

            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);

            mMediaPlayer.setOnCompletionListener(
                    new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            Log.d(TAG, "onCompletion() mediaPlayer=" + mp);
                            mp.stop();
                        }
                    }

            );
        }

        if(id != null) {
            DriveId fileId = DriveId.decodeFromString(id);
            DriveFile file = fileId.asDriveFile();
            Log.i(TAG, "ret id:" + file.getDriveId().encodeToString());


            file.open(mGoogleApiClient, DriveFile.MODE_READ_WRITE, null).setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {
                @Override
                public void onResult(DriveApi.DriveContentsResult result) {
                    if (!result.getStatus().isSuccess()) {
                        Log.i(TAG, "file open error");
                        return;
                    }
                    DriveContents driveContents = result.getDriveContents();
                    FileDescriptor fd = driveContents.getParcelFileDescriptor().getFileDescriptor();

                    try {
                        Log.i(TAG, "try to play");

                        mMediaPlayer.setDataSource(fd);
                        mMediaPlayer.prepare();
                        mMediaPlayer.start();

                    } catch (IOException e) {
                        Log.e(TAG, "play() exception e=" + e);
                    }

                }
            });
        }

//        if( position > 0 )
//            mMediaPlayer.seekTo( position );
    }

    private void pauseMedia() {
        if( mMediaPlayer != null ) {
            mMediaPlayer.pause();
            PreferenceManager.getDefaultSharedPreferences( this ).edit().putInt( CURRENT_MEDIA_POSITION,
                    mMediaPlayer.getCurrentPosition() ).commit();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if( mMediaPlayer != null ) {
            pauseMedia();
            mMediaPlayer.release();
            PreferenceManager.getDefaultSharedPreferences( this ).edit().putInt( CURRENT_MEDIA_POSITION,
                    0 ).commit();
        }

    }
}
