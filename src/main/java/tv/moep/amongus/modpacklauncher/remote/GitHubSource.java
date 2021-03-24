package tv.moep.amongus.modpacklauncher.remote;

/*
 * AmongUs-ModPackLauncher - AmongUs-ModPackLauncher
 * Copyright (c) 2021 Max Lee (max@themoep.de)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import de.themoep.minedown.adventure.Replacer;
import tv.moep.amongus.modpacklauncher.ContentType;
import tv.moep.amongus.modpacklauncher.ModPackConfig;
import tv.moep.amongus.modpacklauncher.ModPackLauncher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class GitHubSource extends ModPackSource {

    private static final List<String> REQUIRED_PLACEHOLDERS = Arrays.asList("user");
    private static final String API_HEADER = "application/vnd.github.v3+json";
    private static final String RELEASES_URL = "https://api.github.com/repos/%user%/%repository%/releases";
    private static final String UPDATE_URL = "https://github.com/%user%/%repository%/releases/tag/%version%";
    private static final String INFO_URL = "https://github.com/%user%/%repository%#readme";

    public GitHubSource(ModPackLauncher launcher) {
        super(launcher, REQUIRED_PLACEHOLDERS);
    }

    @Override
    public String getLatestVersion(ModPackConfig config) {
        try {
            List<String> properties = new ArrayList<>(Arrays.asList("Accept", API_HEADER));
            if (config.getPlaceholders().containsKey("token")) {
                Collections.addAll(properties, "Authorization", "token " + config.getPlaceholders().get("token"));
            } else if (config.getPlaceholders().containsKey("username") && config.getPlaceholders().containsKey("password")) {
                String userPass = config.getPlaceholders().get("username") + ":" + config.getPlaceholders().get("password");
                Collections.addAll(properties, "Authorization", "Basic " + Base64.getEncoder().encodeToString(userPass.getBytes()));
            }
            String s = launcher.query(new URL(new Replacer().replace(config.getPlaceholders("repository")).replaceIn(RELEASES_URL)), properties.toArray(new String[0]));
            if (s != null) {
                try {
                    JsonElement json = new JsonParser().parse(s);
                    if (json.isJsonArray() && ((JsonArray) json).size() > 0) {
                        for (JsonElement release : ((JsonArray) json)) {
                            if (release.isJsonObject()
                                    && ((JsonObject) release).has("tag_name")
                                    && ((JsonObject) release).has("assets")
                                    && ((JsonObject) release).get("assets").isJsonArray()) {
                                for (JsonElement asset : ((JsonObject) release).getAsJsonArray("assets")) {
                                    if (asset.isJsonObject()
                                            && ((JsonObject) asset).has("content_type")) {
                                        return ((JsonObject) release).get("tag_name").getAsString();
                                    }
                                }
                            }
                        }
                    }
                } catch (JsonParseException e) {
                    launcher.log(Level.SEVERE, "Invalid Json returned when getting latest version for " + config.getName() + " from source " + getName() + ": " + s + ". Error: " + e.getMessage());
                }
            }
        } catch (MalformedURLException e) {
            launcher.log(Level.SEVERE, "Invalid URL for getting latest version for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
        }
        return null;
    }

    @Override
    public File downloadUpdate(ModPackConfig config, String gameVersion) {
        try {
            List<String> properties = new ArrayList<>(Arrays.asList("Accept", API_HEADER));
            if (config.getPlaceholders().containsKey("token")) {
                Collections.addAll(properties, "Authorization", "token " + config.getPlaceholders().get("token"));
            } else if (config.getPlaceholders().containsKey("username") && config.getPlaceholders().containsKey("password")) {
                String userPass = config.getPlaceholders().get("username") + ":" + config.getPlaceholders().get("password");
                Collections.addAll(properties, "Authorization", "Basic " + Base64.getEncoder().encodeToString(userPass.getBytes()));
            }
            String s = launcher.query(new URL(new Replacer().replace(config.getPlaceholders("repository")).replaceIn(RELEASES_URL)), properties.toArray(new String[0]));
            if (s != null) {
                try {
                    JsonElement json = JsonParser.parseString(s);
                    if (json.isJsonArray() && ((JsonArray) json).size() > 0) {
                        for (JsonElement release : ((JsonArray) json)) {
                            if (release.isJsonObject()
                                    && ((JsonObject) release).has("tag_name")
                                    && ((JsonObject) release).has("assets")
                                    && ((JsonObject) release).get("assets").isJsonArray()) {
                                String fileName = null;
                                URL source = null;
                                String version = ((JsonObject) release).get("tag_name").getAsString();

                                for (JsonElement asset : ((JsonArray) ((JsonObject) release).get("assets"))) {
                                    if (asset.isJsonObject()
                                            && ((JsonObject) asset).has("browser_download_url")
                                            && ((JsonObject) asset).has("content_type")
                                            && ((JsonObject) asset).has("name")) {
                                        String contentType = ((JsonObject) asset).get("content_type").getAsString();
                                        if (ContentType.ZIP.matches(contentType)) {
                                            try {
                                                fileName = ((JsonObject) asset).get("name").getAsString();
                                                source = new URL(((JsonObject) asset).get("browser_download_url").getAsString());
                                                if (gameVersion == null || fileName.contains(gameVersion)) {
                                                    break;
                                                }
                                            } catch (MalformedURLException e) {
                                                launcher.log(Level.SEVERE, ((JsonObject) asset).get("browser_download_url").getAsString() + " is not a valid URL for update " + version + " for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
                                            }
                                        }
                                    }
                                }

                                if (fileName != null && source != null) {

                                    File target = new File(launcher.getTempFolder(), fileName);

                                    try {
                                        HttpURLConnection con = (HttpURLConnection) source.openConnection();
                                        con.setRequestProperty("User-Agent", launcher.getUserAgent());
                                        con.addRequestProperty("Accept", API_HEADER);
                                        con.addRequestProperty("Accept", "application/octet-stream");
                                        if (config.getPlaceholders().containsKey("token")) {
                                            con.addRequestProperty("Authorization", "token " + config.getPlaceholders().get("token"));
                                        } else if (config.getPlaceholders().containsKey("username") && config.getPlaceholders().containsKey("password")) {
                                            String userPass = config.getPlaceholders().get("username") + ":" + config.getPlaceholders().get("password");
                                            con.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString(userPass.getBytes()));
                                        }
                                        con.setUseCaches(false);
                                        con.connect();
                                        try (InputStream in = con.getInputStream()) {
                                            if (Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING) > 0) {
                                                return target;
                                            }
                                        }
                                    } catch (IOException e) {
                                        launcher.log(Level.SEVERE, "Error while trying to download update " + version + " for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
                                    }
                                } else {
                                    launcher.log(Level.SEVERE, "Unable to find downloadable file for update " + version + " of " + config.getName() + " from source " + getName() + "!");
                                }
                            }
                        }
                    }
                } catch (JsonParseException e) {
                    launcher.log(Level.SEVERE, "Invalid Json returned when getting latest version for " + config.getName() + " from source " + getName() + ": " + s + ". Error: " + e.getMessage());
                }
            }
        } catch (MalformedURLException e) {
            launcher.log(Level.SEVERE, "Invalid URL for getting latest version for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
        }
        return null;
    }

    @Override
    public String getUpdateUrl(ModPackConfig config) {
        String latestVersion = getLatestVersion(config);
        return new Replacer().replace(config.getPlaceholders("repository")).replace("version", latestVersion).replaceIn(UPDATE_URL);
    }

    @Override
    public String getInfoUrl(ModPackConfig config) {
        return new Replacer().replace(config.getPlaceholders("repository")).replaceIn(INFO_URL);
    }

    @Override
    public String getName() {
        return getType().name();
    }

    @Override
    public SourceType getType() {
        return SourceType.GITHUB;
    }
}
