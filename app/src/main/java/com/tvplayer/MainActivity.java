package com.tvplayer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.zxing.integration.android.IntentIntegrator;
import com.journeyapps.barcodescanner.CaptureActivity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@UnstableApi
public class MainActivity extends AppCompatActivity {

    private ExoPlayer player;
    private PlayerView playerView;
    private ProgressBar loading;
    private TextView tvNoSource;
    private TextView tvSourceTitle;
    private TextView tvCategoryTitle;

    private RecyclerView categoryList;
    private RecyclerView channelList;
    private RecyclerView sourceList;

    private CategoryAdapter categoryAdapter;
    private ChannelAdapter channelAdapter;
    private SourceAdapter sourceAdapter;

    private OkHttpClient httpClient;
    private android.os.Handler mainHandler;

    // 三级数据
    private LinkedHashMap<String, List<ChannelItem>> categoryMap = new LinkedHashMap<>();
    private List<String> categoryNames = new ArrayList<>();
    private int selectedCatIndex = -1;
    private int selectedChIndex = -1;

    // 当前播放信息
    private String playingName = "";

    private static final String SOURCE_VIP = "https://8879.kstore.space/zhibo.txt";
    private static final String SOURCE_BAOHES = "http://ygbh.cc.cd/bhzb.php";
    private static final String SOURCE_AI = "https://hub.glowp.xyz/https://raw.githubusercontent.com/jn950/live/main/tv/pllive.txt";
    private static final int REQUEST_CAMERA = 100;
    private String currentSourceKey = "vip";

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
        loadSource(currentSourceKey);
    }

    private void initViews() {
        playerView = findViewById(R.id.playerView);
        loading = findViewById(R.id.loading);
        tvNoSource = findViewById(R.id.tvNoSource);
        tvSourceTitle = findViewById(R.id.tvSourceTitle);
        tvCategoryTitle = findViewById(R.id.tvCategoryTitle);
        categoryList = findViewById(R.id.categoryList);
        channelList = findViewById(R.id.channelList);
        sourceList = findViewById(R.id.sourceList);

        // 一级目录
        categoryAdapter = new CategoryAdapter(new ArrayList<>(), position -> {
            selectedCatIndex = position;
            selectedChIndex = -1;
            categoryAdapter.setSelected(position);
            String catName = categoryNames.get(position);
            tvCategoryTitle.setText(catName);
            loadChannelsForCategory(catName);
        });
        categoryList.setLayoutManager(new LinearLayoutManager(this));
        categoryList.setAdapter(categoryAdapter);

        // 二级目录
        channelAdapter = new ChannelAdapter(new ArrayList<>(), position -> {
            selectedChIndex = position;
            channelAdapter.setSelected(position);
            String catName = categoryNames.get(selectedCatIndex);
            List<ChannelItem> channels = categoryMap.get(catName);
            if (channels != null && position < channels.size()) {
                ChannelItem ch = channels.get(position);
                loadSourcesForChannel(ch);
            }
        });
        channelList.setLayoutManager(new LinearLayoutManager(this));
        channelList.setAdapter(channelAdapter);

        // 三级目录（横向）
        sourceAdapter = new SourceAdapter(new ArrayList<>(), position -> {
            SourceItem src = sourceAdapter.getItem(position);
            if (src != null) playUrl(src.url, src.label);
        });
        sourceList.setLayoutManager(new GridLayoutManager(this, 3, GridLayoutManager.HORIZONTAL, false));
        sourceList.setAdapter(sourceAdapter);

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
                    loading.setVisibility(state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                });
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "播放错误", Toast.LENGTH_SHORT).show();
                    loading.setVisibility(View.GONE);
                });
            }
        });
    }

    // ==================== 加载频道列表 ====================
    private void loadChannelsForCategory(String catName) {
        List<ChannelItem> channels = categoryMap.get(catName);
        if (channels == null || channels.isEmpty()) {
            channelAdapter.update(new ArrayList<>());
            sourceAdapter.update(new ArrayList<>());
            tvSourceTitle.setText("该分类暂无频道");
            return;
        }
        channelAdapter.update(channels);
        sourceAdapter.update(new ArrayList<>());
        tvSourceTitle.setText("请选择频道");

        // 默认选中第一个频道
        selectedChIndex = 0;
        channelAdapter.setSelected(0);
        loadSourcesForChannel(channels.get(0));
    }

    // ==================== 加载源列表并自动播放 ====================
    private void loadSourcesForChannel(ChannelItem ch) {
        if (ch.urls.size() == 1) {
            playUrl(ch.urls.get(0), ch.name);
            List<SourceItem> sources = new ArrayList<>();
            sources.add(new SourceItem(ch.name + " (唯一)", ch.urls.get(0)));
            sourceAdapter.update(sources);
            tvSourceTitle.setText(ch.name + " (1个源)");
        } else {
            List<SourceItem> sources = new ArrayList<>();
            for (int i = 0; i < ch.urls.size(); i++) {
                String label = "源" + (i + 1) + " " + ch.name;
                sources.add(new SourceItem(label, ch.urls.get(i)));
            }
            sourceAdapter.update(sources);
            tvSourceTitle.setText(ch.name + " (" + ch.urls.size() + "个源，选1播放)");
            // 自动播放第一个
            playUrl(ch.urls.get(0), "源1 " + ch.name);
        }
    }

    // ==================== 播放 ====================
    private void playUrl(String url, String name) {
        if (player == null) return;
        playingName = name;
        tvNoSource.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
        tvSourceTitle.setText("▶ " + name);
        MediaItem mediaItem = MediaItem.fromUri(url);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
    }

    // ==================== 解析直播源 ====================
    // 格式: 频道名,#genre#  (表示分类名)
    //       CCTV1,http://xxx.m3u8  (频道行)
    // #genre#行本身是分类标记，该行前面的文字是分类名
    private void parseChannels(String text) {
        new Thread(() -> {
            LinkedHashMap<String, List<ChannelItem>> result = new LinkedHashMap<>();
            String currentCat = "未分类";
            List<ChannelItem> currentList = new ArrayList<>();

            // 支持多种分隔符
            String[] separators = {"\r\n", "\r", "\n"};
            String[] lines = text.split(separators[0]);
            for (int k = 1; k < separators.length && lines.length <= 1; k++) {
                lines = text.split(separators[k]);
            }

            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("//")) continue; // 注释行跳过

                // #genre# 行 = 分类标记行
                // 格式: "央视频道,#genre#" 或 "央视频道 , #genre#" 或只有 "#genre#"
                if (line.contains("#genre#")) {
                    // 提取分类名（去掉 #genre# 部分）
                    String genrePart = line.substring(0, line.indexOf("#genre#")).trim();
                    if (!genrePart.isEmpty()) {
                        // 保存上一个分类
                        if (!currentList.isEmpty()) {
                            result.put(currentCat, new ArrayList<>(currentList));
                            currentList.clear();
                        }
                        currentCat = genrePart;
                    }
                    continue;
                }

                // 频道行: name,url
                int commaIdx = line.indexOf(',');
                if (commaIdx <= 0) continue;
                String name = line.substring(0, commaIdx).trim();
                String url = line.substring(commaIdx + 1).trim();
                if (!url.startsWith("http")) continue;
                if (name.isEmpty()) continue;

                // 去重：同分类下同名频道合并URL
                ChannelItem existing = null;
                for (ChannelItem c : currentList) {
                    if (c.name.equals(name)) {
                        existing = c;
                        break;
                    }
                }
                if (existing != null) {
                    if (!existing.urls.contains(url)) {
                        existing.urls.add(url);
                    }
                } else {
                    ChannelItem ch = new ChannelItem(name);
                    ch.urls.add(url);
                    currentList.add(ch);
                }
            }

            // 保存最后一个分类
            if (!currentList.isEmpty()) {
                result.put(currentCat, new ArrayList<>(currentList));
            }

            final LinkedHashMap<String, List<ChannelItem>> finalResult = result;
            mainHandler.post(() -> {
                categoryMap.clear();
                categoryMap.putAll(finalResult);
                categoryNames.clear();
                categoryNames.addAll(finalResult.keySet());
                loading.setVisibility(View.GONE);

                if (categoryNames.isEmpty()) {
                    tvNoSource.setVisibility(View.VISIBLE);
                    tvNoSource.setText("暂无频道\n下拉刷新");
                    categoryAdapter.update(new ArrayList<>());
                    channelAdapter.update(new ArrayList<>());
                    sourceAdapter.update(new ArrayList<>());
                    tvCategoryTitle.setText("分类");
                    tvSourceTitle.setText("请选择频道");
                } else {
                    tvNoSource.setVisibility(View.GONE);
                    categoryAdapter.update(new ArrayList<>(categoryNames));
                    channelAdapter.update(new ArrayList<>());
                    sourceAdapter.update(new ArrayList<>());
                    tvCategoryTitle.setText(categoryNames.get(0));
                    tvSourceTitle.setText("请选择频道");

                    // 自动加载第一个分类
                    selectedCatIndex = 0;
                    categoryAdapter.setSelected(0);
                    String firstCat = categoryNames.get(0);
                    loadChannelsForCategory(firstCat);
                }
            });
        }).start();
    }

    // ==================== 网络加载 ====================
    private void loadSource(String key) {
        Map<String, String> sources = new HashMap<>();
        sources.put("vip", SOURCE_VIP);
        sources.put("baohes", SOURCE_BAOHES);
        sources.put("ai", SOURCE_AI);

        String url = sources.get(key);
        if (url == null) return;

        loading.setVisibility(View.VISIBLE);
        tvNoSource.setVisibility(View.GONE);
        tvNoSource.setText("加载中...");

        selectedCatIndex = -1;
        selectedChIndex = -1;
        categoryAdapter.update(new ArrayList<>());
        channelAdapter.update(new ArrayList<>());
        sourceAdapter.update(new ArrayList<>());
        tvCategoryTitle.setText("分类");
        tvSourceTitle.setText("请选择频道");

        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> {
                    loading.setVisibility(View.GONE);
                    tvNoSource.setVisibility(View.VISIBLE);
                    tvNoSource.setText("加载失败: " + e.getMessage());
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
                        tvNoSource.setVisibility(View.VISIBLE);
                        tvNoSource.setText("HTTP错误: " + response.code());
                    });
                }
            }
        });
    }

    // ==================== 按钮事件 ====================
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
        btnVip.setAlpha("vip".equals(currentSourceKey) ? 1.0f : 0.5f);
        btnBh.setAlpha("baohes".equals(currentSourceKey) ? 1.0f : 0.5f);
        btnAi.setAlpha("ai".equals(currentSourceKey) ? 1.0f : 0.5f);
    }

    public void refreshSource(View v) {
        loadSource(currentSourceKey);
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
        com.google.zxing.integration.android.IntentResult result =
            IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null && result.getContents() != null) {
            String url = result.getContents().trim();
            if (url.startsWith("http")) {
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

    // ==================== 数据类 ====================
    static class ChannelItem {
        String name;
        List<String> urls = new ArrayList<>();
        ChannelItem(String n) { name = n; }
    }

    static class SourceItem {
        String label;
        String url;
        SourceItem(String l, String u) { label = l; url = u; }
    }
}