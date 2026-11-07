package com.dap.dapurhomies.update;

import com.dap.dapurhomies.DapUrHomiesMod;
import com.dap.dapurhomies.config.ModConfig;
import com.dap.dapurhomies.permission.PlayerPermissionManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 更新检查与下载。
 *
 * <p>下载的 jar 先放在 mods/.dap-ur-homies-update/，不直接进 mods。原因是 Fabric 不允许
 * 两个同 id 的 jar 同时存在，直接放进去会导致下次启动失败。应用更新时先移除当前 jar，
 * 再把新 jar 移到 mods/，保证任意时刻只有一个版本。</p>
 */
public final class UpdateChecker {
    private static final String REPO = "Zeiver1337/dap-ur-homies-server-side";
    private static final String API_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String USER_AGENT = "dap-ur-homies-updater";

    private static final Pattern JAR_NAME = Pattern.compile("^dap-ur-homies-(\\d+(?:\\.\\d+)*)\\.jar$");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "dap-ur-homies-updater");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicBoolean CHECKING = new AtomicBoolean(false);
    private static final AtomicBoolean DOWNLOADING = new AtomicBoolean(false);

    private static volatile String latestVersion;
    private static volatile String assetName;
    private static volatile String assetUrl;
    private static volatile long assetSize;
    private static volatile String assetDigest;

    private UpdateChecker() {
    }

    public record Status(boolean checkOk, boolean updateAvailable, String current, String latest, String message) {
    }

    private static Path modsDir() {
        return FabricLoader.getInstance().getGameDir().resolve("mods");
    }

    private static Path stagingDir() {
        return modsDir().resolve(".dap-ur-homies-update");
    }

    public static String currentVersion() {
        return FabricLoader.getInstance().getModContainer(DapUrHomiesMod.MOD_ID)
                .map(ModContainer::getMetadata)
                .map(m -> m.getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static Path currentJar() {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(DapUrHomiesMod.MOD_ID);
        if (container.isEmpty()) return null;
        for (Path path : container.get().getOrigin().getPaths()) {
            if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar")) {
                return path;
            }
        }
        return null;
    }

    private static volatile MinecraftServer server;

    /** 启动时调用：清掉旧版本残留、尝试应用已下载的更新、按配置检查新版本。 */
    public static void initialize() {
        cleanupOldJars();
        tryApplyStaged();

        ServerLifecycleEvents.SERVER_STARTED.register(started -> {
            server = started;
            if (latestVersion != null && isNewer(latestVersion, currentVersion())) {
                notifyAdmins(started);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> server = null);

        if (ModConfig.get().updateCheckEnabled) {
            checkAsync().whenComplete((status, error) -> {
                if (error != null) {
                    DapUrHomiesMod.LOGGER.warn("[DapUrHomies] Update check failed: {}", error.getMessage());
                } else if (status.updateAvailable()) {
                    DapUrHomiesMod.LOGGER.info("[DapUrHomies] Update available: {} (current {}). Use /dap update download.",
                            status.latest(), status.current());
                    MinecraftServer running = server;
                    if (running != null) running.execute(() -> notifyAdmins(running));
                    if (ModConfig.get().autoUpdateEnabled) {
                        DapUrHomiesMod.LOGGER.info("[DapUrHomies] Auto-update is enabled, downloading {}.", status.latest());
                        downloadAsync().whenComplete((result, downloadError) -> {
                            if (downloadError != null) {
                                DapUrHomiesMod.LOGGER.warn("[DapUrHomies] Auto-update failed: {}", downloadError.getMessage());
                            } else {
                                DapUrHomiesMod.LOGGER.info("[DapUrHomies] Auto-update: {}", result.message());
                            }
                        });
                    }
                } else if (status.checkOk()) {
                    DapUrHomiesMod.LOGGER.info("[DapUrHomies] Up to date ({}).", status.current());
                }
            });
        }
    }

    // ---------------------------------------------------------------- check

    public static CompletableFuture<Status> checkAsync() {
        return CompletableFuture.supplyAsync(() -> {
            if (!CHECKING.compareAndSet(false, true)) {
                return new Status(true, false, currentVersion(), latestVersion, "检查已在进行中");
            }
            try {
                fetchLatestRelease();
                String current = currentVersion();
                boolean available = isNewer(latestVersion, current);
                return new Status(true, available, current, latestVersion,
                        available ? "有新版本可用" : "已是最新版本");
            } catch (Exception e) {
                DapUrHomiesMod.LOGGER.warn("[DapUrHomies] Update check failed: {}", e.toString());
                return new Status(false, false, currentVersion(), latestVersion, shorten(e.getMessage()));
            } finally {
                CHECKING.set(false);
            }
        }, WORKER);
    }

    private static void fetchLatestRelease() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/vnd.github+json")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("GitHub API 返回 " + response.statusCode());
        }

        JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
        latestVersion = trimV(release.get("tag_name").getAsString());

        JsonArray assets = release.getAsJsonArray("assets");
        if (assets == null) throw new IOException("该版本没有附件");

        JsonObject chosen = null;
        for (JsonElement element : assets) {
            JsonObject asset = element.getAsJsonObject();
            String name = asset.get("name").getAsString();
            if (name.startsWith("dap-ur-homies-") && name.endsWith(".jar") && !name.contains("-sources")) {
                chosen = asset;
                break;
            }
        }
        if (chosen == null) throw new IOException("该版本没有可下载的模组 jar");

        assetName = chosen.get("name").getAsString();
        assetUrl = chosen.get("browser_download_url").getAsString();
        assetSize = chosen.has("size") ? chosen.get("size").getAsLong() : -1;
        assetDigest = chosen.has("digest") && !chosen.get("digest").isJsonNull()
                ? chosen.get("digest").getAsString() : null;
    }

    // ------------------------------------------------------------- download

    public static CompletableFuture<Status> downloadAsync() {
        return CompletableFuture.supplyAsync(() -> {
            if (!DOWNLOADING.compareAndSet(false, true)) {
                return new Status(true, true, currentVersion(), latestVersion, "已有下载任务在进行");
            }
            try {
                if (latestVersion == null || assetUrl == null) {
                    fetchLatestRelease();
                }
                String current = currentVersion();
                if (!isNewer(latestVersion, current)) {
                    return new Status(true, false, current, latestVersion, "已是最新版本，无需下载");
                }

                Path staging = stagingDir();
                Files.createDirectories(staging);
                Path target = staging.resolve(assetName);
                Path temp = staging.resolve(assetName + ".part");

                try (InputStream in = openAsset(); OutputStream out = Files.newOutputStream(temp)) {
                    in.transferTo(out);
                }

                long size = Files.size(temp);
                if (assetSize > 0 && size != assetSize) {
                    Files.deleteIfExists(temp);
                    return new Status(false, true, current, latestVersion,
                            "下载文件大小不符（" + size + " / " + assetSize + "）");
                }
                if (assetDigest != null && assetDigest.startsWith("sha256:")) {
                    String actual = "sha256:" + sha256(temp);
                    if (!actual.equalsIgnoreCase(assetDigest)) {
                        Files.deleteIfExists(temp);
                        return new Status(false, true, current, latestVersion, "下载文件校验失败");
                    }
                }

                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                applyStaged(target);

                String message = applied
                        ? "已下载 " + latestVersion + " 并替换旧版本，重启服务器后生效"
                        : "已下载到 " + target + "，但运行中无法删除旧 jar，请关服后手动替换";
                return new Status(true, true, current, latestVersion, message);
            } catch (Exception e) {
                DapUrHomiesMod.LOGGER.warn("[DapUrHomies] Update download failed: {}", e.toString());
                return new Status(false, true, currentVersion(), latestVersion, shorten(e.getMessage()));
            } finally {
                DOWNLOADING.set(false);
            }
        }, WORKER);
    }

    private static InputStream openAsset() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(assetUrl))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("下载返回 " + response.statusCode());
        }
        return response.body();
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    // ---------------------------------------------------------------- apply

    private static volatile boolean applied;

    /**
     * 把暂存的新 jar 换成正在运行的 jar。先挪走旧文件再放新的，
     * 中途失败会把旧文件挪回去，避免出现 mods 里没有本模组的情况。
     */
    private static void applyStaged(Path staged) {
        applied = false;
        Path running = currentJar();
        if (running == null) return;

        Path backup = staged.resolveSibling(running.getFileName() + ".old");
        try {
            Files.move(running, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Windows 会锁住正在使用的 jar，这里退回到手动替换。
            DapUrHomiesMod.LOGGER.warn("[DapUrHomies] Cannot replace running jar: {}", e.toString());
            return;
        }

        try {
            Files.move(staged, modsDir().resolve(staged.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            applied = true;
            deleteQuietly(backup);
            DapUrHomiesMod.LOGGER.info("[DapUrHomies] Update applied, effective after restart.");
        } catch (IOException e) {
            try {
                Files.move(backup, running, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException restore) {
                DapUrHomiesMod.LOGGER.error("[DapUrHomies] Failed to restore old jar; move {} back to mods manually.",
                        backup, restore);
            }
            DapUrHomiesMod.LOGGER.warn("[DapUrHomies] Failed to install update: {}", e.toString());
        }
    }

    /** 启动时把上次没应用成功的更新再试一次。 */
    private static void tryApplyStaged() {
        Path staged = findStaged();
        if (staged == null) return;
        DapUrHomiesMod.LOGGER.info("[DapUrHomies] Found staged update {}.", staged.getFileName());
        applyStaged(staged);
    }

    private static Path findStaged() {
        String version = currentVersion();
        try (var stream = Files.list(stagingDir())) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> JAR_NAME.matcher(p.getFileName().toString()).matches())
                    .filter(p -> isNewer(versionOf(p.getFileName().toString()), version))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /** 新版本启动时清理旧版本 jar，找不到就跳过。 */
    private static void cleanupOldJars() {
        if (!Files.isDirectory(modsDir())) return;
        String current = currentVersion();
        Path running = currentJar();
        List<Path> removed = new ArrayList<>();
        try (var stream = Files.list(modsDir())) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                if (running != null && path.toAbsolutePath().equals(running.toAbsolutePath())) continue;
                String name = path.getFileName().toString();
                Matcher matcher = JAR_NAME.matcher(name);
                if (!matcher.matches()) continue;
                if (!isNewer(current, matcher.group(1))) continue;
                if (deleteQuietly(path)) removed.add(path);
            }
        } catch (IOException e) {
            DapUrHomiesMod.LOGGER.warn("[DapUrHomies] Failed to scan mods directory: {}", e.toString());
            return;
        }

        if (removed.isEmpty()) {
            DapUrHomiesMod.LOGGER.info("[DapUrHomies] No older jar to clean up (current {}).", current);
        } else {
            for (Path path : removed) {
                DapUrHomiesMod.LOGGER.info("[DapUrHomies] Removed old jar {}.", path.getFileName());
            }
        }
    }

    private static boolean deleteQuietly(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            DapUrHomiesMod.LOGGER.warn("[DapUrHomies] Failed to delete {}: {}", path.getFileName(), e.toString());
            return false;
        }
    }

    // ----------------------------------------------------------- utilities

    public static String describe() {
        String current = currentVersion();
        String latest = latestVersion == null ? "未检查" : latestVersion;
        Path staged = findStaged();
        String stagedText = staged == null ? "无" : staged.getFileName().toString();
        return "当前版本 " + current + "，最新版本 " + latest + "，待应用的下载 " + stagedText;
    }

    private static void notifyAdmins(MinecraftServer server) {
        String message = "[Dap Ur Homies] 新版本 " + latestVersion + " 可用，管理员可执行 /dap update download";
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (PlayerPermissionManager.hasPermission(player, "dap-ur-homies.admin")) {
                player.sendMessage(Text.literal(message), false);
            }
        }
    }

    private static String trimV(String value) {
        return value.startsWith("v") || value.startsWith("V") ? value.substring(1) : value;
    }

    private static String versionOf(String jarName) {
        Matcher matcher = JAR_NAME.matcher(jarName);
        return matcher.matches() ? matcher.group(1) : "0";
    }

    /** 简单的数字段比较，够用于 x.y.z。 */
    private static boolean isNewer(String candidate, String base) {
        if (candidate == null) return false;
        if (base == null || base.equals("unknown")) return true;
        String[] a = candidate.split("\\.");
        String[] b = base.split("\\.");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int left = i < a.length ? parseIntOrZero(a[i]) : 0;
            int right = i < b.length ? parseIntOrZero(b[i]) : 0;
            if (left != right) return left > right;
        }
        return false;
    }

    private static int parseIntOrZero(String value) {
        try {
            return Integer.parseInt(value.replaceAll("\\D.*$", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String shorten(String message) {
        if (message == null) return "未知错误";
        return message.length() > 120 ? message.substring(0, 120) + "…" : message;
    }
}
