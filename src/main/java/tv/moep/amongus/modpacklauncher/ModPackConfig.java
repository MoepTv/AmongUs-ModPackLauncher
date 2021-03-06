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

import tv.moep.amongus.modpacklauncher.remote.ModPackSource;

import java.util.LinkedHashMap;
import java.util.Map;

public class ModPackConfig {
    private String name;
    private ModPackSource source;
    private Map<String, String> placeholders;

    public ModPackConfig(String name, ModPackSource source, Map<String, String> placeholders) {
        this.name = name;
        this.source = source;
        this.placeholders = new LinkedHashMap<>(placeholders);
        this.placeholders.putIfAbsent("name", name);
    }

    public Map<String, String> getPlaceholders() {
        return placeholders;
    }

    public Map<String, String> getPlaceholders(String nameFallback) {
        if (getPlaceholders().containsKey(nameFallback)) {
            return getPlaceholders();
        }
        Map<String, String> placeholders = new LinkedHashMap<>(getPlaceholders());
        placeholders.put(nameFallback, getName());
        return placeholders;
    }

    public String toString() {
        return getName() + " (" + getSource().getName() + ")";
    }

    public String getName() {
        return name;
    }

    public ModPackSource getSource() {
        return source;
    }
}