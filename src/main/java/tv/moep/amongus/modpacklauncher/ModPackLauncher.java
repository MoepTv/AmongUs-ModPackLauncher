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
import com.sun.deploy.util.WinRegistry;

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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class ModPackLauncher {

    private static Properties appProperties = new Properties();
    private final String name;
    private final String version;
    private final File tempFolder;
    private final Properties properties = new Properties();
    private BufferedImage icon;

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
        try (FileReader reader = new FileReader("modpacklauncher.properties")) {
            properties.load(reader);
            if (properties.containsKey("steam-folder")) {
                setSteamFolder(new File(properties.getProperty("steam-folder")));
            }
        } catch (IOException e) {
            e.printStackTrace();
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

        try {
            Files.list(steamFolder.toPath()).filter(p -> p.getFileName().toString().startsWith("Among Us - ") && Files.isDirectory(p)).forEach(p -> {
                modPacks.add(p);
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        setProperty("steam-folder", file.getAbsolutePath());
    }

    private String detectSteamFolder() {
        String os = System.getProperties().getProperty("os.name").toLowerCase(Locale.ROOT);
        Path steamApps;
        Object o;
        if (os.contains("windows")) {
            Object steamPath = WinRegistry.get(WinRegistry.HKEY_CURRENT_USER, "Software\\Valve\\Steam", "SteamPath");
            if (!(steamPath instanceof String)) {
                return null;
            }
            steamApps = Paths.get((String) steamPath, "steamapps");
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

    public Path getSteamGame() {
        return steamGame;
    }

    public String getSelected() {
        return selected;
    }

    public List<Path> getModPacks() {
        return modPacks;
    }

    public void launch(Path modPack, boolean viaSteam) {
        if (Files.exists(modPack) && Files.isDirectory(modPack)) {
            if (viaSteam) {
                try {
                    deleteFolder(steamGame);
                    try {
                        Files.delete(steamGame);
                    } catch (IOException ignored) {}

                    Files.walkFileTree(modPack,  new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                            Files.createDirectories(steamGame.resolve(modPack.relativize(dir)));
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.copy(file, steamGame.resolve(modPack.relativize(file)));
                            return FileVisitResult.CONTINUE;
                        }
                    });

                    Properties properties = new Properties();
                    try (FileWriter writer = new FileWriter(steamGame.resolve("modpack.properties").toFile())) {
                        properties.setProperty("name", modPack.getFileName().toString());
                        properties.store(writer, getName() + " " + getVersion() + " Config");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    startGame(steamGame.resolve("Among Us.exe").toString());
                    //Desktop.getDesktop().browse(new URI("steam://run/945360/"));
                } catch (IOException /*| URISyntaxException*/ e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    startGame(modPack.resolve("Among Us.exe").toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
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

    private void deleteFolder(Path folder) {
        try {
            Files.list(folder).forEach(p -> {
                if (Files.isDirectory(p)) {
                    deleteFolder(p);
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
}
