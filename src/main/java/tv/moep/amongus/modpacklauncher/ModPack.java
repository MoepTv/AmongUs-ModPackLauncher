package tv.moep.amongus.modpacklauncher;

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

import java.nio.file.Path;

public class ModPack {
    private final Path path;
    private final String name;
    private final String version;

    public ModPack(Path path, String name, String version) {
        this.path = path;
        this.name = name;
        this.version = version;
    }

    public Path getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getId() {
        return name + " - " + version;
    }
}
