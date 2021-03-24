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

import tv.moep.amongus.modpacklauncher.ModPackConfig;
import tv.moep.amongus.modpacklauncher.ModPackLauncher;

import java.io.File;
import java.util.Collection;

public abstract class ModPackSource {
    protected final ModPackLauncher launcher;
    private final Collection<String> requiredPlaceholders;

    public ModPackSource(ModPackLauncher launcher, Collection<String> requiredPlaceholders) {
        this.launcher = launcher;
        this.requiredPlaceholders = requiredPlaceholders;
    }

    /**
     * Get a collection of placeholders that an update config needs to define for this source to work
     * @return The list of placeholders
     */
    public Collection<String> getRequiredPlaceholders() {
        return requiredPlaceholders;
    }

    /**
     * Get the latest version of a plugin.
     * @param config The mod pack config
     * @return The latest version string or <code>null</code> if not found or an error occured
     */
    public abstract String getLatestVersion(ModPackConfig config);

    /**
     * Download the latest version of a plugin into the target folder specified by the Updater.
     * @param config The mod pack config
     * @param gameVersion The version of the game to get the files for if multiple are included
     * @return A reference to the newly downloaded file or <code>null</code> if not found
     */
    public abstract File downloadUpdate(ModPackConfig config, String gameVersion);

    /**
     * Get the URL where to download updates from manually
     * @param config The mod pack config
     * @return The update url
     */
    public abstract String getUpdateUrl(ModPackConfig config);

    /**
     * Get the URL where to download updates from manually
     * @param config The mod pack config
     * @return The update url
     */
    public abstract String getInfoUrl(ModPackConfig config);

    /**
     * Get the name of the source
     * @return The name of the source
     */
    public abstract String getName();

    /**
     * Get the type of this source
     * @return The type
     */
    public abstract SourceType getType();
}
