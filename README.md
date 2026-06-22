# TV直播播放器 - Android原生版

## 项目结构

```
TVPlayerAndroid/
├── app/
│   ├── src/main/
│   │   ├── java/com/tvplayer/
│   │   │   └── MainActivity.java      # 主活动
│   │   ├── res/layout/
│   │   │   ├── activity_main.xml     # 主布局
│   │   │   └── item_channel.xml      # 频道项布局
│   │   ├── res/values/
│   │   │   ├── strings.xml           # 字符串资源
│   │   │   └── themes.xml            # 主题
│   │   ├── res/drawable/
│   │   │   └── ic_launcher.xml       # 应用图标
│   │   └── AndroidManifest.xml       # 应用清单
│   └── build.gradle                  # 应用构建配置
├── build.gradle                      # 项目构建配置
├── settings.gradle                   # 项目设置
├── gradle.properties                 # Gradle属性
└── gradlew.bat                       # Windows构建脚本
```

## 内置直播源

- VIP直播: https://8879.kstore.space/zhibo.txt
- 宝盒直播: http://ygbh.cc.cd/bhzb.php
- AI更新: https://hub.glowp.xyz/...

## 构建步骤

### 方法1：使用Android Studio（推荐）

1. 下载并安装 Android Studio: https://developer.android.com/studio
2. 打开 Android Studio，选择 "Open an existing project"
3. 选择 `TVPlayerAndroid` 文件夹
4. 等待 Gradle 同步完成
5. 点击菜单 "Build" → "Build Bundle(s) / APK(s)" → "Build APK(s)"
6. APK 将生成在 `app/build/outputs/apk/debug/` 目录

### 方法2：使用命令行

1. 下载 Gradle 8.0+: https://gradle.org/releases/
2. 解压到本地
3. 在项目目录执行:
   ```bash
   gradle wrapper
   ./gradlew assembleDebug
   ```

### 方法3：使用在线构建

1. 将整个 `TVPlayerAndroid` 文件夹上传到 GitHub
2. 使用 GitHub Actions 自动构建
3. 下载生成的 APK

## 技术特性

- **ExoPlayer**: 支持HLS、HTTP等多种流媒体格式
- **原生Android**: 直接调用系统解码器，性能优异
- **无CORS限制**: 原生播放器不受浏览器跨域限制
- **支持Android 5.0+**: 覆盖绝大多数Android设备

## 修改直播源

在 `MainActivity.java` 中的 `initSources()` 方法修改直播源URL。

## 权限说明

- INTERNET: 访问网络
- ACCESS_NETWORK_STATE: 检测网络状态
- ACCESS_WIFI_STATE: WiFi状态

## 许可证

MIT License
