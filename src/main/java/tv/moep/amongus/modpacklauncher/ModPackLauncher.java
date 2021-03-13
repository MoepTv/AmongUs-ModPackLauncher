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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinReg;
import tv.moep.amongus.modpacklauncher.remote.GitHubSource;
import tv.moep.amongus.modpacklauncher.remote.GitLabSource;
import tv.moep.amongus.modpacklauncher.remote.ModPackSource;
import tv.moep.amongus.modpacklauncher.remote.SourceType;

import javax.imageio.ImageIO;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.zip.ZipFile;

public class ModPackLauncher {

    private static Properties appProperties = new Properties();
    private final String name;
    private final String version;
    private final File tempFolder;
    private final Properties properties = new Properties();
    private final Map<SourceType, ModPackSource> sources = new EnumMap<>(SourceType.class);
    private final List<ModPackConfig> modPackConfigs = new ArrayList<>();
    private BufferedImage icon;
    private BufferedImage loadingImage;

    private Cache<URL, String> queryCache = Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.SECONDS).build();

    private File steamFolder = null;
    private String selected = null;
    private List<Path> modPacks = new ArrayList<>();
    private Path steamGame = null;

    public static void main(String[] args) {
        try {
            InputStream s = ModPackLauncher.class.getClassLoader().getResourceAsStream("app.properties");
            appProperties.load(s);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        new ModPackLauncher();
    }

    private ModPackLauncher() {
        sources.put(SourceType.GITHUB, new GitHubSource(this));
        sources.put(SourceType.GITLAB, new GitLabSource(this));

        // TODO: Read remote or from config
        modPackConfigs.add(new ModPackConfig("TheOtherRoles", getSource(SourceType.GITHUB), mapOf(
                "user", "Eisbison",
                "repository", "TheOtherRoles"
        )));
        modPackConfigs.add(new ModPackConfig("ExtraRoles", getSource(SourceType.GITHUB), mapOf(
                "user", "NotHunter101",
                "repository", "ExtraRolesAmongUs"
        )));
        modPackConfigs.add(new ModPackConfig("TownOfUs", getSource(SourceType.GITHUB), mapOf(
                "user", "slushiegoose",
                "repository", "Town-Of-Us"
        )));
        modPackConfigs.add(new ModPackConfig("Sheriff Mod", getSource(SourceType.GITHUB), mapOf(
                "user", "Woodi-dev",
                "repository", "Among-Us-Sheriff-Mod"
        )));

        if (new File("modpacklauncher.properties").exists()) {
            try (FileReader reader = new FileReader("modpacklauncher.properties")) {
                properties.load(reader);
                if (properties.containsKey("steam-folder")) {
                    setSteamFolder(new File(properties.getProperty("steam-folder")));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (steamFolder == null) {
            String detectedSteam = detectSteamFolder();
            if (detectedSteam != null) {
                setSteamFolder(new File(detectedSteam));
            }
        }
        name = appProperties.getProperty("application.name");
        version = appProperties.getProperty("application.version");

        tempFolder = new File(System.getProperty("java.io.tmpdir"), name);

        if (!tempFolder.exists()) {
            tempFolder.mkdirs();
        }

        try {
            icon = ImageIO.read(ModPackLauncher.class.getClassLoader().getResourceAsStream("images/icon.png"));
        } catch (IOException e) {
            icon = null;
        }

        if (System.console() == null && !GraphicsEnvironment.isHeadless()) {
            new ModPackLauncherGui(this).setVisible(true);
        } else {
            log(Level.SEVERE, "This software requires a GUI!\n");
        }
    }

    private Map<String, String> mapOf(String... args) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            map.put(args[i], args[i + 1]);
        }
        return map;
    }

    private ModPackSource getSource(SourceType sourceType) {
        return sources.get(sourceType);
    }

    public List<ModPackConfig> getModPackConfigs() {
        return modPackConfigs;
    }


    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public void log(Level level, String message, Throwable... exception) {
        System.out.println("[" + level + "] " + message);
        for (Throwable throwable : exception) {
            throwable.printStackTrace();
        }
    }

    public String query(URL url, String... properties) {
        return queryCache.get(url, u -> {
            try {
                HttpURLConnection con = (HttpURLConnection) u.openConnection();
                con.setRequestProperty("User-Agent", getUserAgent());
                for (int i = 0; i + 1 < properties.length; i += 2) {
                    con.addRequestProperty(properties[i], properties[i+1]);
                }
                StringBuilder msg = new StringBuilder();
                con.setUseCaches(false);
                con.connect();
                if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                        String line;
                        while ((line = in.readLine()) != null) {
                            if (msg.length() != 0) {
                                msg.append("\n");
                            }
                            msg.append(line);
                        }
                    }
                    return msg.toString();
                }
            } catch (IOException e) {
                log(Level.SEVERE, "Error while trying to query url " + url.toString() + ".", e);
            }
            return null;
        });
    }


    public String getUserAgent() {
        return getName() + "/" + getVersion();
    }

    public File getTempFolder() {
        return tempFolder;
    }

    public static String sanitize(String version) {
        return version.split("[\\s(\\-#\\[{]", 2)[0];
    }

    public File getSteamFolder() {
        return steamFolder;
    }

    public void setSteamFolder(File file) {
        steamFolder = file;
        steamGame = steamFolder.toPath().resolve("Among Us");
        Path originalGame = new File(steamFolder, "Among Us - Original").toPath();
        if (Files.exists(steamGame) && Files.isDirectory(steamGame)) {
            try {
                selected = readSelected();
                if (selected == null) {
                    selected = originalGame.getFileName().toString();
                    if (!Files.exists(originalGame)) {
                        Files.copy(steamGame, originalGame);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        updateModPacks();
        setProperty("steam-folder", file.getAbsolutePath());
    }

    private void updateModPacks() {
        modPacks.clear();
        try {
            Files.list(steamFolder.toPath()).filter(p -> p.getFileName().toString().startsWith("Among Us - ") && Files.isDirectory(p)).forEach(p -> {
                modPacks.add(p);
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String detectSteamFolder() {
        String os = System.getProperties().getProperty("os.name").toLowerCase(Locale.ROOT);
        Path steamApps;
        Object o;
        if (os.contains("windows")) {
            try {
                String steamPath = Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, "SOFTWARE\\Valve\\Steam", "SteamPath");
                if (steamPath == null) {
                    return null;
                }
                steamApps = Paths.get(steamPath, "steamapps");
            } catch (Win32Exception e) {
                return null;
            }
        } else if (os.contains("linux")) {
            steamApps = Paths.get(System.getProperties().getProperty("user.home"), ".steam", "steam", "steamapps");
        } else if (os.contains("darwin")) {
            steamApps = Paths.get(System.getProperties().getProperty("user.home"), "Library", "Application Support", "Steam", "steamapps");
        } else {
            return null;
        }
        return steamApps.resolve("common").toString();
    }

    private String readSelected() {
        Properties properties = new Properties();
        try (FileReader reader = new FileReader(getSteamGame().resolve("modpack.properties").toFile())) {
            properties.load(reader);
            return (String) properties.get("name");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void setProperty(String key, String value) {
        properties.setProperty(key, value);
        try (FileWriter writer = new FileWriter("modpacklauncher.properties")) {
            properties.store(writer, getName() + " " + getVersion() + " Config");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public BufferedImage getIcon() {
        return icon;
    }

    public BufferedImage getLoadingImage() {
        return loadingImage;
    }

    public Path getSteamGame() {
        return steamGame;
    }

    public String getSelected() {
        return selected;
    }

    public List<Path> getModPacks() {
        return modPacks;
    }

    public void installModPack(ModPackConfig config, String version) throws IOException {
        Path downloaded = config.getSource().downloadUpdate(config).toPath();
        Path modPackFolder = steamFolder.toPath().resolve("Among Us - " + config.getName());
        if (Files.exists(modPackFolder)) {
            deleteDirectory(modPackFolder);
        }

        copyDirectory(steamFolder.toPath().resolve("Among Us - Original"), modPackFolder);

        ZipFile zip = new ZipFile(downloaded.toFile());
        zip.stream().forEach(e -> {
            try {
                Path entryPath = modPackFolder.resolve(e.toString());
                Files.createDirectories(entryPath.getParent());
                if (e.isDirectory()) {
                    Files.createDirectory(entryPath);
                } else {
                    Files.copy(zip.getInputStream(e), entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        });
        zip.close();

        try {
            Files.delete(downloaded);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Delete steam_appid.txt, otherwise Steam might auto-update the modded game
        try {
            Files.delete(modPackFolder.resolve("steam_appid.txt"));
        } catch (NoSuchFileException ignored) {
        } catch (IOException e) {
            e.printStackTrace();
        }

        File propertiesFile = modPackFolder.resolve("modpack.properties").toFile();
        if (!propertiesFile.exists()) {
            Properties properties = new Properties();
            try (FileWriter writer = new FileWriter(propertiesFile)) {
                properties.setProperty("name", config.getName());
                properties.setProperty("version", version);
                properties.store(writer, getName() + " " + getVersion() + " Config");
            }
        }

        updateModPacks();
    }

    public void launch(Path modPack, boolean viaSteam) throws IOException {
        if (Files.exists(modPack) && Files.isDirectory(modPack)) {
            if (viaSteam) {
                deleteDirectory(steamGame);
                try {
                    Files.delete(steamGame);
                } catch (IOException ignored) {}

                copyDirectory(modPack, steamGame);

                File propertiesFile = steamGame.resolve("modpack.properties").toFile();
                if (!propertiesFile.exists()) {
                    Properties properties = new Properties();
                    try (FileWriter writer = new FileWriter(propertiesFile)) {
                        properties.setProperty("name", modPack.getFileName().toString());
                        properties.store(writer, getName() + " " + getVersion() + " Config");
                    }
                }
                startGame(steamGame.resolve("Among Us.exe").toString());
                //Desktop.getDesktop().browse(new URI("steam://run/945360/"));
            } else {
                startGame(modPack.resolve("Among Us.exe").toString());
            }
        }
    }

    private void startGame(String exe) throws IOException {
        String os = System.getProperties().getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("windows")) {
            Runtime.getRuntime().exec(exe);
        } else if (os.contains("linux") || os.contains("darwin")) {
            Runtime.getRuntime().exec("wine \"" + exe + "\"");
        }
    }

    private void deleteDirectory(Path folder) {
        try {
            Files.list(folder).forEach(p -> {
                if (Files.isDirectory(p)) {
                    deleteDirectory(p);
                } else {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            Files.delete(folder);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source,  new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)));
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
