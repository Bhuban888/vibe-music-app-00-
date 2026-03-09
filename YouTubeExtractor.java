package com.vibemusic;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class YouTubeExtractor {

    private static final String TAG = "YouTubeExtractor";
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface SearchCallback {
        void onResults(List<Song> songs);
        void onError(String error);
    }

    public interface StreamCallback {
        void onStream(String url);
        void onError(String error);
    }

    public void search(String query, SearchCallback callback) {
        executor.execute(() -> {
            try {
                String encoded = URLEncoder.encode(query + " music", "UTF-8");
                String urlStr = "https://www.youtube.com/results?search_query=" + encoded + "&sp=EgIQAQ%3D%3D";
                String html = fetchUrl(urlStr);
                List<Song> results = parseSearch(html);
                mainHandler.post(() -> callback.onResults(results));
            } catch (Exception e) {
                Log.e(TAG, "Search error: " + e.getMessage());
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    public void getStreamUrl(String videoId, StreamCallback callback) {
        executor.execute(() -> {
            try {
                String url = extractStreamUrl(videoId);
                if (url != null) {
                    mainHandler.post(() -> callback.onStream(url));
                } else {
                    mainHandler.post(() -> callback.onError("Could not extract stream URL"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Stream error: " + e.getMessage());
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    private List<Song> parseSearch(String html) {
        List<Song> songs = new ArrayList<>();
        try {
            String marker = "var ytInitialData = ";
            int start = html.indexOf(marker);
            if (start == -1) return songs;

            int jsonStart = start + marker.length();
            int braceCount = 0;
            int jsonEnd = jsonStart;
            for (int i = jsonStart; i < Math.min(jsonStart + 500000, html.length()); i++) {
                char c = html.charAt(i);
                if (c == '{') braceCount++;
                else if (c == '}') {
                    braceCount--;
                    if (braceCount == 0) { jsonEnd = i + 1; break; }
                }
            }

            JSONObject json = new JSONObject(html.substring(jsonStart, jsonEnd));
            JSONArray contents = json
                .getJSONObject("contents")
                .getJSONObject("twoColumnSearchResultsRenderer")
                .getJSONObject("primaryContents")
                .getJSONObject("sectionListRenderer")
                .getJSONArray("contents");

            for (int i = 0; i < contents.length() && songs.size() < 20; i++) {
                try {
                    JSONObject section = contents.getJSONObject(i);
                    if (!section.has("itemSectionRenderer")) continue;
                    JSONArray items = section.getJSONObject("itemSectionRenderer").getJSONArray("contents");

                    for (int j = 0; j < items.length() && songs.size() < 20; j++) {
                        try {
                            JSONObject item = items.getJSONObject(j);
                            if (!item.has("videoRenderer")) continue;
                            JSONObject video = item.getJSONObject("videoRenderer");

                            String videoId = video.getString("videoId");
                            String title = video.getJSONObject("title")
                                .getJSONArray("runs").getJSONObject(0).getString("text");
                            String channel = video.getJSONObject("ownerText")
                                .getJSONArray("runs").getJSONObject(0).getString("text");

                            JSONArray thumbs = video.getJSONObject("thumbnail").getJSONArray("thumbnails");
                            String thumb = thumbs.getJSONObject(thumbs.length() > 1 ? 1 : 0).getString("url");

                            String durText = "0:00";
                            if (video.has("lengthText")) {
                                durText = video.getJSONObject("lengthText").getString("simpleText");
                            }

                            songs.add(new Song(videoId, title, channel, thumb, durText, parseDuration(durText)));
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            Log.e(TAG, "Parse error: " + e.getMessage());
        }
        return songs;
    }

    private String extractStreamUrl(String videoId) throws Exception {
        String html = fetchUrl("https://www.youtube.com/watch?v=" + videoId);

        String marker = "ytInitialPlayerResponse = ";
        int start = html.indexOf(marker);
        if (start == -1) return null;

        int jsonStart = start + marker.length();
        int braceCount = 0;
        int jsonEnd = jsonStart;
        for (int i = jsonStart; i < Math.min(jsonStart + 200000, html.length()); i++) {
            char c = html.charAt(i);
            if (c == '{') braceCount++;
            else if (c == '}') {
                braceCount--;
                if (braceCount == 0) { jsonEnd = i + 1; break; }
            }
        }

        JSONObject player = new JSONObject(html.substring(jsonStart, jsonEnd));
        JSONObject streamingData = player.optJSONObject("streamingData");
        if (streamingData == null) return null;

        JSONArray formats = streamingData.optJSONArray("adaptiveFormats");
        if (formats == null) return null;

        String bestUrl = null;
        int bestBitrate = 0;

        for (int i = 0; i < formats.length(); i++) {
            JSONObject fmt = formats.getJSONObject(i);
            String mime = fmt.optString("mimeType", "");
            if (!mime.startsWith("audio/")) continue;

            int bitrate = fmt.optInt("averageBitrate", fmt.optInt("bitrate", 0));
            if (bitrate > bestBitrate) {
                String url = fmt.optString("url", "");
                if (!url.isEmpty()) {
                    bestUrl = url;
                    bestBitrate = bitrate;
                }
            }
        }
        return bestUrl;
    }

    private String fetchUrl(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("User-Agent",
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }
        return sb.toString();
    }

    private long parseDuration(String d) {
        try {
            String[] parts = d.split(":");
            if (parts.length == 3)
                return (Long.parseLong(parts[0]) * 3600 + Long.parseLong(parts[1]) * 60 + Long.parseLong(parts[2])) * 1000;
            if (parts.length == 2)
                return (Long.parseLong(parts[0]) * 60 + Long.parseLong(parts[1])) * 1000;
        } catch (Exception ignored) {}
        return 0;
    }
}
