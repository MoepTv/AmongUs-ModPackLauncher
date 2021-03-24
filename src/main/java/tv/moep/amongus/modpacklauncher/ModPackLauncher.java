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
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

public class ModPackLauncher {

    private final static Pattern VERSION_PATTERN = Pattern.compile(".*(\\d{4}\\.\\d{1,2}\\.\\d{1,2}).*");
    private static final Pattern SEM_VER_PATTERN = Pattern.compile(".*?(\\d+\\.\\d+(\\.\\d+)?).*");

    private static Properties appProperties = new Properties();
    private final String name;
    private final String version;
    private final String latestVersion;
    private final ModPackConfig updateConfig;
    private final File tempFolder;
    private final Properties properties = new Properties();
    private final Map<SourceType, ModPackSource> sources = new EnumMap<>(SourceType.class);
    private final Map<String, ModPackConfig> modPackConfigs = new LinkedHashMap<>();
    private BufferedImage icon;
    private BufferedImage loadingImage;

    private Cache<URL, String> queryCache = Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.SECONDS).build();

    private Path steamFolder = null;
    private String selected = null;
    private List<ModPack> modPacks = new ArrayList<>();
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

        updateConfig = new ModPackConfig(getName(), getSource(SourceType.GITHUB), mapOf(
                "user", "MoepTv",
                "repository", "AmongUs-ModPackLauncher"
        ));
        latestVersion = updateConfig.getLatestVersion();

        // TODO: Read remote or from config
        addModPackConfig(new ModPackConfig("TheOtherRoles", getSource(SourceType.GITHUB), mapOf(
                "user", "Eisbison",
                "repository", "TheOtherRoles"
        )));
        addModPackConfig(new ModPackConfig("ExtraRoles", getSource(SourceType.GITHUB), mapOf(
                "user", "NotHunter101",
                "repository", "ExtraRolesAmongUs"
        )));
        addModPackConfig(new ModPackConfig("TownOfUs", getSource(SourceType.GITHUB), mapOf(
                "user", "slushiegoose",
                "repository", "Town-Of-Us"
        )));
        addModPackConfig(new ModPackConfig("Sheriff Mod", getSource(SourceType.GITHUB), mapOf(
                "user", "Woodi-dev",
                "repository", "Among-Us-Sheriff-Mod"
        )));

        if (new File("modpacklauncher.properties").exists()) {
            try (FileReader reader = new FileReader("modpacklauncher.properties")) {
                properties.load(reader);
                if (properties.containsKey("steam-folder")) {
                    setSteamFolder(Paths.get(properties.getProperty("steam-folder")));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (steamFolder == null) {
            String detectedSteam = detectSteamFolder();
            if (detectedSteam != null) {
                setSteamFolder(Paths.get(detectedSteam));
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

        try {
            loadingImage = ImageIO.read(ModPackLauncher.class.getClassLoader().getResourceAsStream("images/loading.gif"));
        } catch (IOException e) {
            loadingImage = null;
        }

        if (System.console() == null && !GraphicsEnvironment.isHeadless()) {
            new ModPackLauncherGui(this);
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

    public Collection<ModPackConfig> getModPackConfigs() {
        return modPackConfigs.values();
    }

    private void addModPackConfig(ModPackConfig modPackConfig) {
        modPackConfigs.put(modPackConfig.getName(), modPackConfig);
    }

    public ModPackConfig getModPackConfig(String name) {
        return modPackConfigs.get(name);
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getUpdateUrl() {
        return updateConfig.getUpdateUrl();
    }

    public boolean hasNewerVersion() {
        return isVersionNewer(getVersion(), getLatestVersion());
    }

    public boolean isVersionNewer(String version, String toCompare) {
        if (version == null || toCompare == null) {
            return false;
        }
        String sanVersion = sanitize(version);
        String sanToCompare = sanitize(toCompare);

        if (sanVersion.indexOf('.') > 0 && sanToCompare.indexOf('.') > 0) {
            try {
                int[] semVer = parseSemVer(sanVersion);
                int[] toCompareSemVer = parseSemVer(sanToCompare);
                return compareTo(toCompareSemVer, semVer) > 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private int compareTo(int[] latestSemVer, int[] installedSemVer) {
        for (int i = 0; i < installedSemVer.length && i < latestSemVer.length; i++) {
            int latestVersionInt = latestSemVer[i];
            int installedVersionInt = installedSemVer[i];
            if (latestVersionInt > installedVersionInt) {
                return 1;
            } else if (latestVersionInt < installedVersionInt) {
                return -1;
            }
        }

        if (installedSemVer.length < latestSemVer.length) {
            return 1;
        } else if (installedSemVer.length > latestSemVer.length) {
            return -1;
        }
        return 0;
    }

    private int[] parseSemVer(String version) throws NumberFormatException {
        String[] split = version.split("\\.");
        int[] semVer = new int[split.length];
        for (int i = 0; i < split.length; i++) {
            semVer[i] = Integer.parseInt(split[i]);
        }
        return semVer;
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
        Matcher matcher = SEM_VER_PATTERN.matcher(version);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return version;
    }

    public Properties getProperties() {
        return properties;
    }

    public Path getSteamFolder() {
        return steamFolder;
    }

    public void setSteamFolder(Path path) {
        steamFolder = path;
        steamGame = steamFolder.resolve("Among Us");
        if (Files.exists(steamGame) && Files.isDirectory(steamGame)) {
            try {
                selected = readSelected();
                if (selected == null || selected.startsWith("Original")) {
                    String version = parseGameVersion(steamGame);
                    if (version != null) {
                        Path originalGame = steamFolder.resolve("Among Us - Original - " + version);
                        selected = "Original - " + version;
                        if (!Files.exists(originalGame)) {
                            copyDirectory(steamGame, originalGame);
                            Properties properties = new Properties();
                            try (OutputStream out = Files.newOutputStream(originalGame.resolve("modpack.properties"))) {
                                properties.setProperty("name", "Original");
                                properties.setProperty("version", version);
                                properties.store(out, getName() + " " + getVersion() + " Config");
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        updateModPacks();
        setProperty("steam-folder", path.toAbsolutePath().toString());
    }

    private void updateModPacks() {
        modPacks.clear();
        try {
            Files.list(steamFolder).filter(p -> p.getFileName().toString().startsWith("Among Us - ") && Files.isDirectory(p)).forEach(p -> {
                Properties properties = loadModPackProperties(p);
                modPacks.add(new ModPack(p, properties.containsKey("name") ? properties.getProperty("name") : p.getFileName().toString().replace("Among Us - ", ""), properties.getProperty("version")));
            });
            modPacks.sort((o1, o2) -> {
                if (o1.getName().equals("Original") && !o2.getName().equals("Orginal")) {
                    return -1;
                } else if (!o1.getName().equals("Original") && o2.getName().equals("Orginal")) {
                    return 1;
                }
                return o1.getId().compareToIgnoreCase(o2.getId());
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String detectSteamFolder() {
        String os = System.getProperties().getProperty("os.name").toLowerCase(Locale.ROOT);
        Path steamApps;
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
        Properties properties = loadModPackProperties(getSteamGame());
        if (properties.containsKey("name") && properties.containsKey("version")) {
            return properties.getProperty("name") + " - " + properties.getProperty("version");
        }
        return null;
    }

    private Properties loadModPackProperties(Path path) {
        File propertiesFile = path.resolve("modpack.properties").toFile();
        Properties properties = new Properties();
        if (propertiesFile.exists()) {
            try (FileReader reader = new FileReader(propertiesFile)) {
                properties.load(reader);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return properties;
    }

    public void setProperty(String key, String value) {
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

    public List<ModPack> getModPacks() {
        return modPacks;
    }

    public void installModPack(Path baseDirectory, ModPackConfig config, String version) throws IOException {
        String gameVersion = null;
        if (baseDirectory.getFileName().toString().startsWith("Among Us - Original - ")) {
            gameVersion = baseDirectory.getFileName().toString().substring("Among Us - Original - ".length());
        }
        Path downloaded = config.downloadUpdate(gameVersion).toPath();
        Path modPackFolder = steamFolder.resolve("Among Us - " + config.getName());
        if (Files.exists(modPackFolder)) {
            deleteDirectory(modPackFolder);
        }

        copyDirectory(baseDirectory, modPackFolder);

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
        Properties properties = loadModPackProperties(modPackFolder);
        try (FileWriter writer = new FileWriter(propertiesFile)) {
            properties.setProperty("name", config.getName());
            properties.setProperty("version", version);
            properties.store(writer, getName() + " " + getVersion() + " Config");
        }

        updateModPacks();
    }

    public void launch(ModPack modPack, boolean viaSteam) throws IOException {
        if (Files.exists(modPack.getPath()) && Files.isDirectory(modPack.getPath())) {
            deleteDirectory(steamGame);
            try {
                Files.delete(steamGame);
            } catch (IOException ignored) {}

            copyDirectory(modPack.getPath(), steamGame);

            File propertiesFile = steamGame.resolve("modpack.properties").toFile();
            if (!propertiesFile.exists()) {
                Properties properties = new Properties();
                try (FileWriter writer = new FileWriter(propertiesFile)) {
                    properties.setProperty("name", modPack.getName());
                    if (modPack.getVersion() != null) {
                        properties.setProperty("version", modPack.getVersion());
                    }
                    properties.store(writer, getName() + " " + getVersion() + " Config");
                }
            }
            if (viaSteam) {
                try {
                    Desktop.getDesktop().browse(new URI("steam://run/945360/"));
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                    startGame(steamGame.resolve("Among Us.exe").toString());
                }
            } else {
                startGame(steamGame.resolve("Among Us.exe").toString());
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
        } catch (NoSuchFileException ignored) {
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

    private String parseGameVersion(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new NotDirectoryException(directory + " is not a directory!");
        }

        Path gameFile = directory.resolve("Among Us_Data/globalgamemanagers");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(gameFile)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Innersloth") && line.contains("Among Us")) {
                    Matcher matcher = VERSION_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        return matcher.group(1);
                    }
                }
            }
        }

        return null;
    }

    public List<Path> getOriginalGames() {
        try {
            return Files.list(steamFolder)
                    .filter(p -> Files.isDirectory(p))
                    .filter(p -> p.getFileName().toString().startsWith("Among Us - Original - "))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }
}
