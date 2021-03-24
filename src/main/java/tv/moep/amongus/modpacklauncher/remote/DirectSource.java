package tv.moep.amongus.modpacklauncher.remote;

/*
 * PhoenixUpdater - core
 * Copyright (C) 2020 Max Lee aka Phoenix616 (max@themoep.de)
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
import java.util.List;
import java.util.logging.Level;

public class DirectSource extends ModPackSource {

    private final String name;
    private final String latestVersion;
    private final String download;

    public DirectSource(String name, ModPackLauncher launcher, String latestVersion, String download, List<String> requiredPlaceholders) {
        super(launcher, requiredPlaceholders);
        this.name = name;
        this.latestVersion = latestVersion;
        this.download = download;
    }

    @Override
    public String getLatestVersion(ModPackConfig config) {
        try {
            String r = launcher.query(new URL(new Replacer().replace(config.getPlaceholders()).replaceIn(latestVersion)));
            if (r != null && !r.isEmpty()) {
                return r;
            }
        } catch (MalformedURLException e) {
            launcher.log(Level.SEVERE, "Invalid URL for getting latest direct version for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
        }
        return null;
    }

    @Override
    public File downloadUpdate(ModPackConfig config, String gameVersion) {
        String version = config.getLatestVersion();
        if (version != null) {

            try {
                URL source = new URL(new Replacer().replace(config.getPlaceholders()).replaceIn(download));
                File target = new File(launcher.getTempFolder(), source.getPath().substring(source.getPath().lastIndexOf('/') + 1));

                HttpURLConnection con = (HttpURLConnection) source.openConnection();
                con.setUseCaches(false);
                con.setRequestProperty("User-Agent", launcher.getUserAgent());
                con.connect();
                try (InputStream in = con.getInputStream()) {
                    if (Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING) > 0) {
                        return target;
                    }
                }
            } catch (IOException e) {
                launcher.log(Level.SEVERE, "Error while trying to download update " + version + " for " + config.getName() + " from source " + getName() + "! " + e.getMessage());
            }
        }

        return null;
    }

    @Override
    public String getUpdateUrl(ModPackConfig config) {
        return new Replacer().replace(config.getPlaceholders()).replaceIn(download);
    }

    @Override
    public String getInfoUrl(ModPackConfig config) {
        return null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public SourceType getType() {
        return SourceType.DIRECT;
    }
}
