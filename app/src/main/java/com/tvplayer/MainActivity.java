package com.tvplayer;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.zxing.integration.android.IntentIntegrator;
import com.journeyapps.barcodescanner.CaptureActivity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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
    private FrameLayout rootLayout;
    private LinearLayout menuContainer;
    private LinearLayout panelSource;
    private LinearLayout panelCategory;
    private LinearLayout panelChannel;
    private TextView tvCategoryTitle;
    private TextView tvChannelTitle;
    private RecyclerView categoryList;
    private RecyclerView channelList;

    private CategoryAdapter categoryAdapter;
    private ChannelAdapter channelAdapter;
    private SourceAdapter sourceAdapter;

    private OkHttpClient httpClient;
    private android.os.Handler mainHandler;

    private LinkedHashMap<String, List<ChannelItem>> categoryMap = new LinkedHashMap<>();
    private List<String> categoryNames = new ArrayList<>();
    private int selectedCatIndex = -1;
    private int selectedChIndex = -1;

    private static final String SOURCE_VIP = "https://8879.kstore.space/zhibo.txt";
    private static final String SOURCE_BAOHES = "http://ygbh.cc.cd/bhzb.php";
    private static final String SOURCE_AI = "https://hub.glowp.xyz/https://raw.githubusercontent.com/jn950/live/main/tv/pllive.txt";
    private static final int REQUEST_CAMERA = 100;
    private String currentSourceKey = "vip";

    private int menuLevel = 0; // 0=hidden, 1=source, 2=category, 3=channel

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
        rootLayout = findViewById(R.id.rootLayout);
        menuContainer = findViewById(R.id.menuContainer);
        panelSource = findViewById(R.id.panelSource);
        panelCategory = findViewById(R.id.panelCategory);
        panelChannel = findViewById(R.id.panelChannel);
        tvCategoryTitle = findViewById(R.id.tvCategoryTitle);
        tvChannelTitle = findViewById(R.id.tvChannelTitle);
        categoryList = findViewById(R.id.categoryList);
        channelList = findViewById(R.id.channelList);

        // Tap to show menu
        rootLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMenu();
            }
        });

        categoryAdapter = new CategoryAdapter(new ArrayList<String>(), new java.util.function.Consumer<Integer>() {
            @Override
            public void accept(Integer position) {
                selectedCatIndex = position;
                selectedChIndex = -1;
                categoryAdapter.setSelected(position);
                String catName = categoryNames.get(position);
                tvCategoryTitle.setText(catName);
                loadChannelsForCategory(catName);
                showLevel3();
            }
        });
        categoryList.setLayoutManager(new LinearLayoutManager(this));
        categoryList.setAdapter(categoryAdapter);

        channelAdapter = new ChannelAdapter(new ArrayList<ChannelItem>(), new java.util.function.Consumer<Integer>() {
            @Override
            public void accept(Integer position) {
                selectedChIndex = position;
                channelAdapter.setSelected(position);
                String catName = categoryNames.get(selectedCatIndex);
                List<ChannelItem> channels = categoryMap.get(catName);
                if (channels != null && position < channels.size()) {
                    ChannelItem ch = channels.get(position);
                    tvChannelTitle.setText(ch.name);
                    loadSourcesForChannel(ch);
                    hideMenu();
                }
            }
        });
        channelList.setLayoutManager(new LinearLayoutManager(this));
        channelList.setAdapter(channelAdapter);

        initPlayer();
        updateSourceButtons();
        hideMenu();
    }

    private void toggleMenu() {
        if (menuContainer.getVisibility() == View.VISIBLE) {
            hideMenu();
        } else {
            showMenu();
        }
    }

    private void hideMenu() {
        menuContainer.setVisibility(View.GONE);
        menuLevel = 0;
    }

    private void showMenu() {
        menuContainer.setVisibility(View.VISIBLE);
        showLevel1();
    }

    private void showLevel1() {
        menuLevel = 1;
        panelSource.setVisibility(View.VISIBLE);
        panelCategory.setVisibility(View.GONE);
        panelChannel.setVisibility(View.GONE);
    }

    private void showLevel2() {
        menuLevel = 2;
        panelSource.setVisibility(View.VISIBLE);
        panelCategory.setVisibility(View.VISIBLE);
        panelChannel.setVisibility(View.GONE);
    }

    private void showLevel3() {
        menuLevel = 3;
        panelSource.setVisibility(View.VISIBLE);
        panelCategory.setVisibility(View.VISIBLE);
        panelChannel.setVisibility(View.VISIBLE);
    }

    private void initPlayer() {
        player = new ExoPlayer.Builder(this)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(this))
            .build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (state == Player.STATE_BUFFERING) {
                            loading.setVisibility(View.VISIBLE);
                        } else {
                            loading.setVisibility(View.GONE);
                        }
                    }
                });
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Play error", Toast.LENGTH_SHORT).show();
                        loading.setVisibility(View.GONE);
                    }
                });
            }
        });
    }

    private void loadChannelsForCategory(String catName) {
        List<ChannelItem> channels = categoryMap.get(catName);
        if (channels == null || channels.isEmpty()) {
            channelAdapter.update(new ArrayList<ChannelItem>());
            tvChannelTitle.setText("No channels");
            return;
        }
        channelAdapter.update(channels);
        tvChannelTitle.setText("Select channel");
    }

    private void loadSourcesForChannel(ChannelItem ch) {
        if (ch.urls.size() == 1) {
            playUrl(ch.urls.get(0), ch.name);
        } else {
            // Play first source, show source selection
            playUrl(ch.urls.get(0), ch.name + " (Source 1)");
        }
    }

    private void playUrl(String url, String name) {
        if (player == null) return;
        tvNoSource.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Playing: " + name, Toast.LENGTH_SHORT).show();
        MediaItem mediaItem = MediaItem.fromUri(url);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
    }

    private void parseChannels(String text) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                LinkedHashMap<String, List<ChannelItem>> result = new LinkedHashMap<String, List<ChannelItem>>();
                String currentCat = "Default";
                List<ChannelItem> currentList = new ArrayList<ChannelItem>();

                String[] lines = text.split("\r?\n");
                for (String raw : lines) {
                    String line = raw.trim();
                    if (line.isEmpty()) continue;
                    if (line.startsWith("//")) continue;

                    // Format: "频道名,#genre#"
                    if (line.contains(",#genre#")) {
                        int idx = line.indexOf(",#genre#");
                        String genrePart = line.substring(0, idx).trim();
                        if (!genrePart.isEmpty()) {
                            if (!currentList.isEmpty()) {
                                result.put(currentCat, new ArrayList<ChannelItem>(currentList));
                                currentList.clear();
                            }
                            currentCat = genrePart;
                        }
                        continue;
                    }

                    // Format: "频道名,http://..."
                    int commaIdx = line.indexOf(',');
                    if (commaIdx <= 0) continue;
                    String name = line.substring(0, commaIdx).trim();
                    String url = line.substring(commaIdx + 1).trim();
                    if (!url.startsWith("http")) continue;
                    if (name.isEmpty()) continue;

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

                if (!currentList.isEmpty()) {
                    result.put(currentCat, new ArrayList<ChannelItem>(currentList));
                }

                final LinkedHashMap<String, List<ChannelItem>> finalResult = result;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        categoryMap.clear();
                        categoryMap.putAll(finalResult);
                        categoryNames.clear();
                        categoryNames.addAll(finalResult.keySet());
                        loading.setVisibility(View.GONE);

                        if (categoryNames.isEmpty()) {
                            tvNoSource.setVisibility(View.VISIBLE);
                            tvNoSource.setText("No channels loaded");
                            categoryAdapter.update(new ArrayList<String>());
                            channelAdapter.update(new ArrayList<ChannelItem>());
                        } else {
                            tvNoSource.setVisibility(View.GONE);
                            categoryAdapter.update(new ArrayList<String>(categoryNames));
                            channelAdapter.update(new ArrayList<ChannelItem>());

                            // Auto select first category
                            selectedCatIndex = 0;
                            categoryAdapter.setSelected(0);
                            loadChannelsForCategory(categoryNames.get(0));
                        }
                    }
                });
            }
        }).start();
    }

    private void loadSource(String key) {
        Map<String, String> sources = new HashMap<String, String>();
        sources.put("vip", SOURCE_VIP);
        sources.put("baohes", SOURCE_BAOHES);
        sources.put("ai", SOURCE_AI);

        String url = sources.get(key);
        if (url == null) return;

        loading.setVisibility(View.VISIBLE);
        tvNoSource.setVisibility(View.GONE);
        tvNoSource.setText("Loading...");

        selectedCatIndex = -1;
        selectedChIndex = -1;
        categoryAdapter.update(new ArrayList<String>());
        channelAdapter.update(new ArrayList<ChannelItem>());

        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        loading.setVisibility(View.GONE);
                        tvNoSource.setVisibility(View.VISIBLE);
                        tvNoSource.setText("Load failed: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String body = response.body() != null ? response.body().string() : "";
                    parseChannels(body);
                } else {
                    final int code = response.code();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            loading.setVisibility(View.GONE);
                            tvNoSource.setVisibility(View.VISIBLE);
                            tvNoSource.setText("HTTP error: " + code);
                        }
                    });
                }
            }
        });
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
            showLevel1();
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

    public void showManualInput(View v) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Input Stream URL");
        builder.setView(R.layout.dialog_manual_input);
        builder.setPositiveButton("OK", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                EditText etUrl = ((AlertDialog) dialog).findViewById(R.id.etSourceUrl);
                if (etUrl != null) {
                    String url = etUrl.getText().toString().trim();
                    if (!url.isEmpty() && url.startsWith("http")) {
                        currentSourceKey = "custom";
                        updateSourceButtons();
                        loadCustomSource(url);
                        hideMenu();
                    } else {
                        Toast.makeText(MainActivity.this, "Invalid URL", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
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
                loadCustomSource(url);
                hideMenu();
            } else {
                Toast.makeText(this, "Invalid QR", Toast.LENGTH_SHORT).show();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void loadCustomSource(String url) {
        loading.setVisibility(View.VISIBLE);
        tvNoSource.setVisibility(View.GONE);
        tvNoSource.setText("Loading...");

        selectedCatIndex = -1;
        selectedChIndex = -1;
        categoryAdapter.update(new ArrayList<String>());
        channelAdapter.update(new ArrayList<ChannelItem>());

        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        loading.setVisibility(View.GONE);
                        tvNoSource.setVisibility(View.VISIBLE);
                        tvNoSource.setText("Load failed: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String body = response.body() != null ? response.body().string() : "";
                    parseChannels(body);
                } else {
                    final int code = response.code();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            loading.setVisibility(View.GONE);
                            tvNoSource.setVisibility(View.VISIBLE);
                            tvNoSource.setText("HTTP error: " + code);
                        }
                    });
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scanQRCode(null);
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
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

    static class ChannelItem {
        String name;
        List<String> urls = new ArrayList<String>();
        ChannelItem(String n) { name = n; }
    }
}