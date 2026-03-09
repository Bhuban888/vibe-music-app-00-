package com.vibemusic;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.ViewHolder> {

    public interface OnSongClick {
        void onSongClick(Song song, int position);
    }

    private List<Song> songs;
    private final OnSongClick listener;

    public SongAdapter(List<Song> songs, OnSongClick listener) {
        this.songs = songs;
        this.listener = listener;
    }

    public void updateSongs(List<Song> newSongs) {
        this.songs = newSongs;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_song, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Song song = songs.get(position);
        holder.tvTitle.setText(song.title);
        holder.tvArtist.setText(song.artist);
        holder.tvDuration.setText(song.duration);
        Glide.with(holder.ivThumb.getContext())
            .load(song.thumbnailUrl)
            .placeholder(android.R.drawable.ic_media_play)
            .centerCrop()
            .into(holder.ivThumb);
        holder.itemView.setOnClickListener(v -> listener.onSongClick(song, position));
    }

    @Override
    public int getItemCount() { return songs != null ? songs.size() : 0; }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumb;
        TextView tvTitle, tvArtist, tvDuration;

        ViewHolder(View v) {
            super(v);
            ivThumb = v.findViewById(R.id.iv_thumb);
            tvTitle = v.findViewById(R.id.tv_title);
            tvArtist = v.findViewById(R.id.tv_artist);
            tvDuration = v.findViewById(R.id.tv_duration);
        }
    }
}
