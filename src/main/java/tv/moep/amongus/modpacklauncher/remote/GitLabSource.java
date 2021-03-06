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
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

public class GitLabSource extends ModPackSource {

    private static final List<String> REQUIRED_PLACEHOLDERS = Arrays.asList("user");
    private static final String API_URL = "https://gitlab.com/api/v4/";
    private static final String RELEASES_URL = "%apiurl%projects/%user%%2F%repository%/releases";

    public GitLabSource(ModPackLauncher launcher) {
        super(launcher, REQUIRED_PLACEHOLDERS);
    }

    @Override
    public String getLatestVersion(ModPackConfig config) {
        try {
            Replacer replacer = new Replacer().replace("apiurl", API_URL).replace(config.getPlaceholders("repository"));
            String[] properties = new String[0];
            if (config.getPlaceholders().containsKey("token")) {
                properties = new String[] {"Private-Token", config.getPlaceholders().get("token")};
            }
            String s = launcher.query(new URL(replacer.replaceIn(RELEASES_URL)), properties);
            if (s != null) {
                try {
                    JsonElement json = new JsonParser().parse(s);
                    if (json.isJsonArray() && ((JsonArray) json).size() > 0) {
                        for (JsonElement release : ((JsonArray) json)) {
                            if (release.isJsonObject()
                                    && ((JsonObject) release).has("tag_name")
                                    && ((JsonObject) release).has("assets")
                                    && ((JsonObject) release).get("assets").isJsonObject()) {
                                JsonObject assets = ((JsonObject) release).getAsJsonObject("assets");
                                if (assets.has("links") && assets.get("links").isJsonArray()) {
                                    for (JsonElement asset : assets.getAsJsonArray("links")) {
                                        if (asset.isJsonObject()
                                                && (((JsonObject) asset).get("name").getAsString().endsWith(".jar")
                                                        || ((JsonObject) asset).get("url").getAsString().endsWith(".jar"))) {
                                            return ((JsonObject) release).get("tag_name").getAsString();
                                        }
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
    public File downloadUpdate(ModPackConfig config) {
        try {
            Replacer replacer = new Replacer().replace("apiurl", API_URL).replace(config.getPlaceholders("repository"));
            String s = launcher.query(new URL(replacer.replaceIn(RELEASES_URL)), "Accept", "application/vnd.github.v3+json");
            if (s != null) {
                try {
                    JsonElement json = new JsonParser().parse(s);
                    if (json.isJsonArray() && ((JsonArray) json).size() > 0) {
                        for (JsonElement release : ((JsonArray) json)) {
                            if (release.isJsonObject()
                                    && ((JsonObject) release).has("tag_name")
                                    && ((JsonObject) release).has("assets")
                                    && ((JsonObject) release).get("assets").isJsonObject()) {
                                JsonObject assets = ((JsonObject) release).getAsJsonObject("assets");
                                if (assets.has("links") && assets.get("links").isJsonArray()) {
                                    for (JsonElement asset : assets.getAsJsonArray("links")) {
                                        if (asset.isJsonObject()
                                                && (((JsonObject) asset).get("name").getAsString().endsWith(".jar")
                                                        || ((JsonObject) asset).get("url").getAsString().endsWith(".jar"))) {
                                            String version = ((JsonObject) release).get("tag_name").getAsString();
                                            File target = new File(launcher.getTempFolder(), ((JsonObject) asset).get("name").getAsString());

                                            try {
                                                URL source = new URL(((JsonObject) asset).get("url").getAsString());

                                                HttpURLConnection con = (HttpURLConnection) source.openConnection();
                                                con.setRequestProperty("User-Agent", launcher.getUserAgent());
                                                if (config.getPlaceholders().containsKey("token")) {
                                                    con.setRequestProperty("Private-Token", config.getPlaceholders().get("token"));
                                                }
                                                con.setUseCaches(false);
                                                con.connect();
                                                try (InputStream in = con.getInputStream()) {
                                                    if (Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING) > 0) {
                                                        return target;
                                                    }
                                                }
                                            } catch (IOException e) {
                                                launcher.log(Level.SEVERE, "Error while trying to download update "
                                            + version + " for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch(JsonParseException e) {
                    launcher.log(Level.SEVERE, "Invalid Json returned when getting latest version for " + config.getName() + " from source " + getName() + ": " + s + ". Error: " + e.getMessage());
                }
            }
        } catch(MalformedURLException e){
            launcher.log(Level.SEVERE, "Invalid URL for getting latest version for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
        }
        return null;
    }

    @Override
    public String getName() {
        return getType().name();
    }

    @Override
    public SourceType getType() {
        return SourceType.GITLAB;
    }
}
