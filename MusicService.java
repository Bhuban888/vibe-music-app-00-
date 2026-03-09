package com.vibemusic;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

public class MusicService extends Service {

    private static final String CHANNEL_ID = "vibe_music_channel";
    private static final int NOTIFICATION_ID = 1;

    private ExoPlayer player;
    private final IBinder binder = new MusicBinder();
    private Song currentSong;

    public class MusicBinder extends Binder {
        public MusicService getService() { return MusicService.this; }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        player = new ExoPlayer.Builder(this).build();
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updateNotification();
            }
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) {
                    // notify activity
                    Intent i = new Intent("com.vibemusic.SONG_ENDED");
                    sendBroadcast(i);
                }
            }
        });
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public void playSong(Song song, String streamUrl) {
        this.currentSong = song;
        player.setMediaItem(MediaItem.fromUri(streamUrl));
        player.prepare();
        player.play();
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    public void togglePlayPause() {
        if (player.isPlaying()) player.pause();
        else player.play();
        updateNotification();
    }

    public void seekTo(long ms) { player.seekTo(ms); }

    public boolean isPlaying() { return player.isPlaying(); }
    public long getCurrentPosition() { return player.getCurrentPosition(); }
    public long getDuration() { return player.getDuration(); }
    public Song getCurrentSong() { return currentSong; }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "Vibe Music", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Music playback");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = currentSong != null ? currentSong.title : "Vibe Music";
        String artist = currentSong != null ? currentSong.artist : "";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)
            .build();
    }

    private void updateNotification() {
        getSystemService(NotificationManager.class)
            .notify(NOTIFICATION_ID, buildNotification());
    }

    @Override
    public void onDestroy() {
        player.release();
        super.onDestroy();
    }
}
