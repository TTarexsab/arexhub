package dev.arex.arexhub;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Modrinth API'sinden resource pack arar ve indirir. */
public final class ModrinthApi {
    public static final String MC_VERSION = "1.21.4";
    private static final String BASE = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "arex/arexhub/1.0.0 (Minecraft " + MC_VERSION + ")";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private ModrinthApi() {}

    private static HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    public static CompletableFuture<List<ArexPack>> search(String query) {
        String facets = "[[\"project_type:resourcepack\"],[\"versions:" + MC_VERSION + "\"]]";
        String index = query.isBlank() ? "downloads" : "relevance";
        String url = BASE + "/search?limit=40&index=" + index
                + "&query=" + enc(query) + "&facets=" + enc(facets);

        return HTTP.sendAsync(request(url), HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
            if (res.statusCode() != 200) {
                throw new CompletionException(new IOException("HTTP " + res.statusCode()));
            }
            JsonArray hits = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonArray("hits");
            List<ArexPack> out = new ArrayList<>();
            for (JsonElement e : hits) {
                JsonObject o = e.getAsJsonObject();
                int dl = o.has("downloads") ? o.get("downloads").getAsInt() : 0;
                out.add(new ArexPack(str(o, "project_id"), str(o, "title"),
                        str(o, "author"), str(o, "description"), dl));
            }
            return out;
        });
    }

    /** Pack'in 1.21.4 sürümünü resourcepacks klasörüne indirir. */
    public static void download(ArexPack pack, Path dir, DownloadTask task) {
        CompletableFuture.runAsync(() -> {
            try {
                String versionsUrl = BASE + "/project/" + pack.id()
                        + "/version?game_versions=" + enc("[\"" + MC_VERSION + "\"]");
                HttpResponse<String> vres = HTTP.send(request(versionsUrl), HttpResponse.BodyHandlers.ofString());
                if (vres.statusCode() != 200) throw new IOException("HTTP " + vres.statusCode());

                JsonArray versions = JsonParser.parseString(vres.body()).getAsJsonArray();
                if (versions.isEmpty()) throw new IOException("1.21.4 sürümü bulunamadı");

                JsonArray files = versions.get(0).getAsJsonObject().getAsJsonArray("files");
                JsonObject chosen = files.get(0).getAsJsonObject();
                for (JsonElement e : files) {
                    JsonObject f = e.getAsJsonObject();
                    if (f.has("primary") && f.get("primary").getAsBoolean()) {
                        chosen = f;
                        break;
                    }
                }

                String fileUrl = str(chosen, "url");
                String filename = str(chosen, "filename").replaceAll("[^a-zA-Z0-9._-]", "_");
                if (!filename.toLowerCase().endsWith(".zip")) filename += ".zip";

                Files.createDirectories(dir);
                Path target = dir.resolve(filename);
                Path tmp = dir.resolve(filename + ".part");

                HttpResponse<InputStream> res = HTTP.send(request(fileUrl), HttpResponse.BodyHandlers.ofInputStream());
                if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode());
                long total = res.headers().firstValueAsLong("Content-Length").orElse(-1L);

                try (InputStream in = res.body(); OutputStream out = Files.newOutputStream(tmp)) {
                    byte[] buf = new byte[16 * 1024];
                    long read = 0;
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                        read += n;
                        task.progress = total > 0 ? (float) read / total : -1f;
                    }
                }
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);

                task.progress = 1f;
                task.status = DownloadTask.Status.DONE;

                // Pack listesini yenile ki Texture Packs menüsünde hemen görünsün.
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.execute(() -> mc.getResourcePackManager().scanPacks());
            } catch (Exception ex) {
                task.error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                task.status = DownloadTask.Status.FAILED;
            }
        });
    }
}
