package com.tvplayer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.zxing.integration.android.IntentIntegrator;
import com.journeyapps.barcodescanner.CaptureActivity;
import com.tvplayer.okhttp3.Call;
import com.tvplayer.okhttp3.Callback;
import com.tvplayer.okhttp3.OkHttpClient;
import com.tvplayer.okhttp3.Request;
import com.tvplayer.okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@UnstableApi
public class MainActivity extends AppCompatActivity {

    private ExoPlayer player;
    private PlayerView playerView;
    private RecyclerView channelList;
    private ChannelAdapter adapter;
    private TextView nowPlaying;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar loading;
    private TextView noSourceView;

    private OkHttpClient httpClient;
    private List<Channel> channels = new ArrayList<>();
    private Map<String, String> sources = new HashMap<>();
    private String currentSourceKey = "vip";
    private android.os.Handler mainHandler;

    private static final String SOURCE_VIP = "https://8879.kstore.space/zhibo.txt";
    private static final String SOURCE_BAOHES = "http://ygbh.cc.cd/bhzb.php";
    private static final String SOURCE_AI = "https://hub.glowp.xyz/https://raw.githubusercontent.com/jn950/live/main/tv/pllive.txt";
    private static final int REQUEST_CAMERA = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        mainHandler = new android.os.Handler(getMainLooper());
        httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

        initViews();
        initSources();
        loadSource(currentSourceKey);
    }

    private void initViews() {
        playerView = findViewById(R.id.playerView);
        channelList = findViewById(R.id.channelList);
        nowPlaying = findViewById(R.id.nowPlaying);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        loading = findViewById(R.id.loading);
        noSourceView = findViewById(R.id.noSourceView);

        adapter = new ChannelAdapter(channels, this::playChannel);
        channelList.setLayoutManager(new GridLayoutManager(this, 3));
        channelList.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(() -> loadSource(currentSourceKey));

        initPlayer();
        updateSourceButtons();
    }

    private void initPlayer() {
        player = new ExoPlayer.Builder(this)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(this))
            .build();
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                runOnUiThread(() -> {
                    if (state == Player.STATE_BUFFERING) {
                        loading.setVisibility(View.VISIBLE);
                    } else {
                        loading.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this,
                        "播放错误: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void initSources() {
        sources.put("vip", SOURCE_VIP);
        sources.put("baohes", SOURCE_BAOHES);
        sources.put("ai", SOURCE_AI);
    }

    public void switchSource(View v) {
        String key = null;
        int id = v.getId();
        if (id == R.id.btnSourceVip) key = "vip";
        else if (id == R.id.btnSourceBaohes) key = "baohes";
        else if (id == R.id.btnSourceAi) key = "ai";
        if (key != null && !key.equals(currentSourceKey)) {
            currentSourceKey = key;
            updateSourceButtons();
            loadSource(key);
        }
    }

    private void updateSourceButtons() {
        Button btnVip = findViewById(R.id.btnSourceVip);
        Button btnBh = findViewById(R.id.btnSourceBaohes);
        Button btnAi = findViewById(R.id.btnSourceAi);
        btnVip.setSelected("vip".equals(currentSourceKey));
        btnBh.setSelected("baohes".equals(currentSourceKey));
        btnAi.setSelected("ai".equals(currentSourceKey));
        btnVip.setAlpha("vip".equals(currentSourceKey) ? 1.0f : 0.5f);
        btnBh.setAlpha("baohes".equals(currentSourceKey) ? 1.0f : 0.5f);
        btnAi.setAlpha("ai".equals(currentSourceKey) ? 1.0f : 0.5f);
    }

    public void scanQRCode(View v) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        } else {
            IntentIntegrator integrator = new IntentIntegrator(this);
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
            integrator.setCameraId(0);
            integrator.setBeepEnabled(false);
            integrator.setBarcodeImageEnabled(false);
            integrator.setOrientationLocked(true);
            integrator.setCaptureActivity(CaptureActivity.class);
            integrator.initiateScan();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null && result.getContents() != null) {
            String url = result.getContents().trim();
            if (url.startsWith("http")) {
                sources.put("custom", url);
                currentSourceKey = "custom";
                updateSourceButtons();
                loadSource("custom");
            } else {
                Toast.makeText(this, "无效的二维码内容", Toast.LENGTH_SHORT).show();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scanQRCode(null);
            } else {
                Toast.makeText(this, "需要相机权限才能扫描二维码", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadSource(String key) {
        String url = sources.get(key);
        if (url == null) return;

        loading.setVisibility(View.VISIBLE);
        swipeRefresh.setRefreshing(true);
        channels.clear();
        adapter.notifyDataSetChanged();
        noSourceView.setVisibility(View.GONE);

        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> {
                    loading.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    noSourceView.setText("加载失败: " + e.getMessage());
                    noSourceView.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String body = response.body() != null ? response.body().string() : "";
                    parseChannels(body);
                } else {
                    mainHandler.post(() -> {
                        loading.setVisibility(View.GONE);
                        swipeRefresh.setRefreshing(false);
                        noSourceView.setText("HTTP错误: " + response.code());
                        noSourceView.setVisibility(View.VISIBLE);
                    });
                }
            }
        });
    }

    private void parseChannels(String text) {
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
                    noSourceView.setText("暂无频道\n下拉刷新");
                    noSourceView.setVisibility(View.VISIBLE);
                } else {
                    noSourceView.setVisibility(View.GONE);
                }
            });
        }).start();
    }

    private void playChannel(Channel channel) {
        if (player == null) return;
        nowPlaying.setText(channel.name);
        MediaItem mediaItem = MediaItem.fromUri(channel.url);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) player.play();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }

    static class Channel {
        String name;
        String url;
        Channel(String n, String u) { name = n; url = u; }
    }
}
