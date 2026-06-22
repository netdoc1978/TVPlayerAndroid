package com.tvplayer;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    
    private ExoPlayer player;
    private RecyclerView channelList;
    private ChannelAdapter adapter;
    private TextView nowPlaying;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar loading;
    private View playerContainer;
    private View noSourceView;
    
    private OkHttpClient httpClient;
    private List<Channel> channels = new ArrayList<>();
    private Map<String, String> sources = new HashMap<>();
    private String currentSourceKey = "vip";
    
    // 内置直播源
    private static final String SOURCE_VIP = "https://8879.kstore.space/zhibo.txt";
    private static final String SOURCE_BAOHES = "http://ygbh.cc.cd/bhzb.php";
    private static final String SOURCE_AI = "https://hub.glowp.xyz/https://raw.githubusercontent.com/jn950/live/main/tv/pllive.txt";
    
    private Handler mainHandler;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 全屏设置
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        setContentView(R.layout.activity_main);
        
        mainHandler = new Handler(Looper.getMainLooper());
        httpClient = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build();
        
        initViews();
        initSources();
        loadSource(currentSourceKey);
    }
    
    private void initViews() {
        playerContainer = findViewById(R.id.playerContainer);
        channelList = findViewById(R.id.channelList);
        nowPlaying = findViewById(R.id.nowPlaying);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        loading = findViewById(R.id.loading);
        noSourceView = findViewById(R.id.noSourceView);
        
        adapter = new ChannelAdapter(channels, channel -> playChannel(channel));
        channelList.setLayoutManager(new LinearLayoutManager(this));
        channelList.setAdapter(adapter);
        
        swipeRefresh.setOnRefreshListener(() -> loadSource(currentSourceKey));
        
        // 初始化播放器
        initPlayer();
    }
    
    private void initPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerContainer = findViewById(R.id.playerView);
        if (playerContainer != null && playerContainer instanceof android.widget.FrameLayout) {
            androidx.media3.ui.PlayerView playerView = new androidx.media3.ui.PlayerView(this);
            playerView.setPlayer(player);
            ((FrameLayout) playerContainer).addView(playerView);
        }
        
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    loading.setVisibility(View.VISIBLE);
                } else {
                    loading.setVisibility(View.GONE);
                }
            }
            
            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Toast.makeText(MainActivity.this, "播放错误: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void initSources() {
        sources.put("vip", SOURCE_VIP);
        sources.put("baohes", SOURCE_BAOHES);
        sources.put("ai", SOURCE_AI);
    }
    
    private void loadSource(String key) {
        String url = sources.get(key);
        if (url == null) return;
        
        loading.setVisibility(View.VISIBLE);
        swipeRefresh.setRefreshing(true);
        
        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> {
                    loading.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(MainActivity.this, "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String body = response.body() != null ? response.body().string() : "";
                    parseChannels(body, key);
                } else {
                    mainHandler.post(() -> {
                        loading.setVisibility(View.GONE);
                        swipeRefresh.setRefreshing(false);
                        Toast.makeText(MainActivity.this, "加载失败: HTTP " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
    
    private void parseChannels(String text, String sourceKey) {
        new Thread(() -> {
            List<Channel> parsed = new ArrayList<>();
            String[] lines = text.split("\n");
            
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("//") || line.startsWith("#")) continue;
                if (line.contains("#genre#")) continue;
                
                int commaIdx = line.indexOf(",");
                if (commaIdx > 0) {
                    String name = line.substring(0, commaIdx).trim();
                    String url = line.substring(commaIdx + 1).trim();
                    
                    if (url.startsWith("http")) {
                        parsed.add(new Channel(name, url));
                    }
                }
            }
            
            mainHandler.post(() -> {
                channels.clear();
                channels.addAll(parsed);
                adapter.notifyDataSetChanged();
                loading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                
                if (channels.isEmpty()) {
                    noSourceView.setVisibility(View.VISIBLE);
                } else {
                    noSourceView.setVisibility(View.GONE);
                }
            });
        }).start();
    }
    
    private void playChannel(Channel channel) {
        nowPlaying.setText(channel.name);
        
        MediaItem mediaItem;
        if (channel.url.contains(".m3u8")) {
            DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0");
            HlsMediaSource hlsSource = new HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(channel.url));
            player.setMediaSource(hlsSource);
        } else {
            mediaItem = MediaItem.fromUri(channel.url);
            player.setMediaItem(mediaItem);
        }
        
        player.prepare();
        player.play();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) {
            player.play();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            player.pause();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }
    
    // 频道数据类
    static class Channel {
        String name;
        String url;
        
        Channel(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }
    
    // 频道适配器
    class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ViewHolder> {
        private List<Channel> list;
        private OnItemClickListener listener;
        
        interface OnItemClickListener {
            void onClick(Channel channel);
        }
        
        ChannelAdapter(List<Channel> list, OnItemClickListener listener) {
            this.list = list;
            this.listener = listener;
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_channel, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Channel channel = list.get(position);
            holder.name.setText(channel.name);
            holder.itemView.setOnClickListener(v -> listener.onClick(channel));
        }
        
        @Override
        public int getItemCount() {
            return list.size();
        }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name;
            
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.channelName);
            }
        }
    }
}
