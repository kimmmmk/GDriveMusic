package com.lgcns.gdrivemusic;

import android.graphics.BitmapFactory;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.browse.MediaBrowser;
import android.media.browse.MediaBrowser.MediaItem;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.service.media.MediaBrowserService;
import android.text.TextUtils;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;

import java.util.ArrayList;
import java.util.List;

/**
 * This class provides a MediaBrowser through a service. It exposes the media library to a browsing
 * client, through the onGetRoot and onLoadChildren methods. It also creates a MediaSession and
 * exposes it through its MediaSession.Token, which allows the client to create a MediaController
 * that connects to and send control commands to the MediaSession remotely. This is useful for
 * user interfaces that need to interact with your media session, like Android Auto. You can
 * (should) also use the same service from your app's UI, which gives a seamless playback
 * experience to the user.
 * <p>
 * To implement a MediaBrowserService, you need to:
 * <p>
 * <ul>
 * <p>
 * <li> Extend {@link android.service.media.MediaBrowserService}, implementing the media browsing
 * related methods {@link android.service.media.MediaBrowserService#onGetRoot} and
 * {@link android.service.media.MediaBrowserService#onLoadChildren};
 * <li> In onCreate, start a new {@link android.media.session.MediaSession} and notify its parent
 * with the session's token {@link android.service.media.MediaBrowserService#setSessionToken};
 * <p>
 * <li> Set a callback on the
 * {@link android.media.session.MediaSession#setCallback(android.media.session.MediaSession.Callback)}.
 * The callback will receive all the user's actions, like play, pause, etc;
 * <p>
 * <li> Handle all the actual music playing using any method your app prefers (for example,
 * {@link android.media.MediaPlayer})
 * <p>
 * <li> Update playbackState, "now playing" metadata and queue, using MediaSession proper methods
 * {@link android.media.session.MediaSession#setPlaybackState(android.media.session.PlaybackState)}
 * {@link android.media.session.MediaSession#setMetadata(android.media.MediaMetadata)} and
 * {@link android.media.session.MediaSession#setQueue(java.util.List)})
 * <p>
 * <li> Declare and export the service in AndroidManifest with an intent receiver for the action
 * android.media.browse.MediaBrowserService
 * <p>
 * </ul>
 * <p>
 * To make your app compatible with Android Auto, you also need to:
 * <p>
 * <ul>
 * <p>
 * <li> Declare a meta-data tag in AndroidManifest.xml linking to a xml resource
 * with a &lt;automotiveApp&gt; root element. For a media app, this must include
 * an &lt;uses name="media"/&gt; element as a child.
 * For example, in AndroidManifest.xml:
 * &lt;meta-data android:name="com.google.android.gms.car.application"
 * android:resource="@xml/automotive_app_desc"/&gt;
 * And in res/values/automotive_app_desc.xml:
 * &lt;automotiveApp&gt;
 * &lt;uses name="media"/&gt;
 * &lt;/automotiveApp&gt;
 * <p>
 * </ul>
 *
 * @see <a href="README.md">README.md</a> for more details.
 */
public class GDriveMusicService extends MediaBrowserService {

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

        //mSongs = SongGenerator.generateSongs();

        initMediaSession();
    }

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
            mediaItems.add( generateBrowseableMediaItemByGenre(BROWSEABLE_CAJUN) );
            mediaItems.add( generateBrowseableMediaItemByGenre(BROWSEABLE_JAZZ) );
            mediaItems.add( generateBrowseableMediaItemByGenre(BROWSEABLE_ROCK) );
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
        mediaDescriptionBuilder.setIconBitmap( BitmapFactory.decodeResource( getResources(), R.drawable.folder ) );

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
        if( mMediaPlayer != null )
            mMediaPlayer.reset();

        //Should check id to determine what to play in a real app
        int songId = getApplicationContext().getResources().getIdentifier("geoff_ledak_dust_array_preview", "raw", getApplicationContext().getPackageName());
        mMediaPlayer = MediaPlayer.create(getApplicationContext(), songId);

        if( position > 0 )
            mMediaPlayer.seekTo( position );
        mMediaPlayer.start();

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
