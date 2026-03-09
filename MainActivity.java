package com.vibemusic;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private EditText etSearch;
    private ProgressBar progressSearch;
    private RecyclerView rvSongs;
    private LinearLayout miniPlayer;
    private ImageView ivMiniThumb;
    private TextView tvMiniTitle, tvMiniArtist;
    private ImageButton btnMiniPlayPause;
    private ProgressBar progressMini;
    private TextView tvEmptyState;

    private SongAdapter adapter;
    private List<Song> songList = new ArrayList<>();
    private YouTubeExtractor extractor = new YouTubeExtractor();

    private MusicService musicService;
    private boolean bound = false;

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (bound && musicService != null) {
                long dur = musicService.getDuration();
                long pos = musicService.getCurrentPosition();
                if (dur > 0) {
                    progressMini.setProgress((int) (pos * 100 / dur));
                }
                progressHandler.postDelayed(this, 500);
            }
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder b = (MusicService.MusicBinder) service;
            musicService = b.getService();
            bound = true;
            updateMiniPlayer();
        }
        @Override
        public void onServiceDisconnected(ComponentName name) { bound = false; }
    };

    private final BroadcastReceiver songEndedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (bound && musicService != null) {
                ImageButton btn = findViewById(R.id.btn_mini_play_pause);
                btn.setImageResource(android.R.drawable.ic_media_play);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etSearch = findViewById(R.id.et_search);
        progressSearch = findViewById(R.id.progress_search);
        rvSongs = findViewById(R.id.rv_songs);
        miniPlayer = findViewById(R.id.mini_player);
        ivMiniThumb = findViewById(R.id.iv_mini_thumb);
        tvMiniTitle = findViewById(R.id.tv_mini_title);
        tvMiniArtist = findViewById(R.id.tv_mini_artist);
        btnMiniPlayPause = findViewById(R.id.btn_mini_play_pause);
        progressMini = findViewById(R.id.progress_mini);
        tvEmptyState = findViewById(R.id.tv_empty_state);

        adapter = new SongAdapter(songList, this::onSongSelected);
        rvSongs.setLayoutManager(new LinearLayoutManager(this));
        rvSongs.setAdapter(adapter);

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                performSearch();
                return true;
            }
            return false;
        });

        findViewById(R.id.btn_search).setOnClickListener(v -> performSearch());

        btnMiniPlayPause.setOnClickListener(v -> {
            if (bound && musicService != null) {
                musicService.togglePlayPause();
                btnMiniPlayPause.setImageResource(
                    musicService.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
            }
        });

        miniPlayer.setOnClickListener(v -> {
            if (bound && musicService != null && musicService.getCurrentSong() != null) {
                startActivity(new Intent(this, PlayerActivity.class));
            }
        });

        // Start and bind service
        Intent serviceIntent = new Intent(this, MusicService.class);
        startService(serviceIntent);
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);

        registerReceiver(songEndedReceiver,
            new IntentFilter("com.vibemusic.SONG_ENDED"), RECEIVER_NOT_EXPORTED);
    }

    private void performSearch() {
        String query = etSearch.getText().toString().trim();
        if (query.isEmpty()) return;

        hideKeyboard();
        progressSearch.setVisibility(View.VISIBLE);
        tvEmptyState.setVisibility(View.GONE);
        songList.clear();
        adapter.updateSongs(songList);

        extractor.search(query, new YouTubeExtractor.SearchCallback() {
            @Override
            public void onResults(List<Song> songs) {
                progressSearch.setVisibility(View.GONE);
                if (songs.isEmpty()) {
                    tvEmptyState.setVisibility(View.VISIBLE);
                    tvEmptyState.setText("No results found");
                } else {
                    songList.clear();
                    songList.addAll(songs);
                    adapter.updateSongs(songList);
                }
            }
            @Override
            public void onError(String error) {
                progressSearch.setVisibility(View.GONE);
                tvEmptyState.setVisibility(View.VISIBLE);
                tvEmptyState.setText("Search failed. Check internet connection.");
                Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void onSongSelected(Song song, int position) {
        Toast.makeText(this, "Loading: " + song.title, Toast.LENGTH_SHORT).show();
        extractor.getStreamUrl(song.id, new YouTubeExtractor.StreamCallback() {
            @Override
            public void onStream(String url) {
                if (bound && musicService != null) {
                    musicService.playSong(song, url);
                    updateMiniPlayer();
                    progressHandler.post(progressRunnable);
                    // Open player
                    Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                    startActivity(intent);
                }
            }
            @Override
            public void onError(String error) {
                Toast.makeText(MainActivity.this, "Playback error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateMiniPlayer() {
        if (bound && musicService != null && musicService.getCurrentSong() != null) {
            Song song = musicService.getCurrentSong();
            miniPlayer.setVisibility(View.VISIBLE);
            tvMiniTitle.setText(song.title);
            tvMiniArtist.setText(song.artist);
            btnMiniPlayPause.setImageResource(
                musicService.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
            Glide.with(this).load(song.thumbnailUrl).into(ivMiniThumb);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
    }

    @Override
    protected void onDestroy() {
        progressHandler.removeCallbacks(progressRunnable);
        unregisterReceiver(songEndedReceiver);
        if (bound) { unbindService(connection); bound = false; }
        super.onDestroy();
    }
}
