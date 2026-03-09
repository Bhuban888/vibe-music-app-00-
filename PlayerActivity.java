package com.vibemusic;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class PlayerActivity extends AppCompatActivity {

    private ImageView ivAlbumArt;
    private TextView tvTitle, tvArtist, tvCurrentTime, tvTotalTime;
    private SeekBar seekBar;
    private ImageButton btnPlayPause, btnBack;

    private MusicService musicService;
    private boolean bound = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (bound && musicService != null) {
                long duration = musicService.getDuration();
                long position = musicService.getCurrentPosition();
                if (duration > 0) {
                    seekBar.setMax((int) duration);
                    seekBar.setProgress((int) position);
                    tvCurrentTime.setText(formatTime(position));
                    tvTotalTime.setText(formatTime(duration));
                }
                updatePlayButton();
            }
            handler.postDelayed(this, 500);
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder b = (MusicService.MusicBinder) service;
            musicService = b.getService();
            bound = true;
            updateUI();
            handler.post(progressRunnable);
        }
        @Override
        public void onServiceDisconnected(ComponentName name) { bound = false; }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        ivAlbumArt = findViewById(R.id.iv_album_art);
        tvTitle = findViewById(R.id.tv_title);
        tvArtist = findViewById(R.id.tv_artist);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalTime = findViewById(R.id.tv_total_time);
        seekBar = findViewById(R.id.seek_bar);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnBack = findViewById(R.id.btn_back);

        btnBack.setOnClickListener(v -> finish());

        btnPlayPause.setOnClickListener(v -> {
            if (bound && musicService != null) {
                musicService.togglePlayPause();
                updatePlayButton();
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && bound && musicService != null) {
                    musicService.seekTo(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        bindService(new Intent(this, MusicService.class), connection, Context.BIND_AUTO_CREATE);
    }

    private void updateUI() {
        if (!bound || musicService == null) return;
        Song song = musicService.getCurrentSong();
        if (song == null) return;
        tvTitle.setText(song.title);
        tvArtist.setText(song.artist);
        Glide.with(this).load(song.thumbnailUrl)
            .placeholder(android.R.drawable.ic_media_play)
            .into(ivAlbumArt);
        updatePlayButton();
    }

    private void updatePlayButton() {
        if (bound && musicService != null) {
            btnPlayPause.setImageResource(
                musicService.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        }
    }

    private String formatTime(long ms) {
        long min = TimeUnit.MILLISECONDS.toMinutes(ms);
        long sec = TimeUnit.MILLISECONDS.toSeconds(ms) % 60;
        return String.format(Locale.US, "%d:%02d", min, sec);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(progressRunnable);
        if (bound) { unbindService(connection); bound = false; }
        super.onDestroy();
    }
}
