package com.vibemusic;

public class Song {
    public String id;
    public String title;
    public String artist;
    public String thumbnailUrl;
    public String duration;
    public long durationMs;

    public Song(String id, String title, String artist, String thumbnailUrl, String duration, long durationMs) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.thumbnailUrl = thumbnailUrl;
        this.duration = duration;
        this.durationMs = durationMs;
    }
}
