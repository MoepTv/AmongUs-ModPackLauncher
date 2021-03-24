package tv.moep.amongus.modpacklauncher.remote;

/*
 * AmongUs-ModPackLauncher - modpacklauncher
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

import java.util.Arrays;
import java.util.List;

public class NightlySource extends DirectSource {

    private static final List<String> REQUIRED_PLACEHOLDERS = Arrays.asList("user", "repository", "file");

    public NightlySource(ModPackLauncher launcher) {
        super("Nightly", launcher, null, "https://nightly.link/%user%/%repository%/workflows/main/master/%file%", REQUIRED_PLACEHOLDERS);
    }

    @Override
    public String getLatestVersion(ModPackConfig config) {
        return "nightly";
    }

    @Override
    public SourceType getType() {
        return SourceType.NIGHTLY;
    }
}
