package tv.moep.amongus.modpacklauncher;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

public class ModPackLauncherGui extends JFrame {
    private static final Color ELEMENT_BACKGROUND = new Color(0x363636);
    private static final Color ELEMENT_FOREROUND = new Color(0xEAEAEA);

    private final ModPackLauncher launcher;
    private final JList<ModPackListEntry> packList;

    public ModPackLauncherGui(ModPackLauncher launcher) {
        super(launcher.getName() + " v" + launcher.getVersion());
        this.launcher = launcher;
        setIconImage(launcher.getIcon());
        getContentPane().setBackground(new Color(0x161515));

        setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel pathLine = new JPanel();
        pathLine.setBackground(null);
        JTextField pathField = new JTextField(20);
        pathField.setFont(getContentPane().getFont().deriveFont(14f));
        pathField.setBackground(ELEMENT_BACKGROUND);
        pathField.setForeground(ELEMENT_FOREROUND.darker());

        File steamFolder = launcher.getSteamFolder();
        if (steamFolder != null && steamFolder.exists() && steamFolder.isDirectory()) {
            pathField.setText(steamFolder.getAbsolutePath());
        }
        pathField.setEditable(false);
        pathField.setBorder(new CompoundBorder(new LineBorder(Color.BLACK, 1), new EmptyBorder(4, 4, 4, 4)));

        JButton buttonSelectPath = new HoverButton("Select Steam game folder", ELEMENT_BACKGROUND, ELEMENT_FOREROUND, new Color(0x232323), ELEMENT_FOREROUND);
        buttonSelectPath.setFont(getContentPane().getFont().deriveFont(Font.BOLD, 14f));
        buttonSelectPath.setBorder(new CompoundBorder(new LineBorder(Color.BLACK, 1), new EmptyBorder(4, 4, 4, 4)));
        ActionListener steamFolderSelector = e -> {
            String[] paths = pathField.getText().split("\" \"");
            String path = "";
            if (paths.length > 0) {
                path = paths[0];
                if (path.startsWith("\"") && path.endsWith("\"")) {
                    path = path.substring(1, path.length() - 1);
                }
            }
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (UnsupportedLookAndFeelException | ClassNotFoundException | InstantiationException | IllegalAccessException ignored) {}
            JFileChooser chooser = pathField.getText().isEmpty() || path.isEmpty() ? new JFileChooser()
                    : new JFileChooser(new File(path).getParentFile());
            chooser.setBackground(ELEMENT_BACKGROUND);
            chooser.setForeground(ELEMENT_FOREROUND);
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.showOpenDialog(null);
            if (chooser.getSelectedFile() != null) {
                pathField.setText(chooser.getSelectedFile().getAbsolutePath());
                launcher.setSteamFolder(chooser.getSelectedFile());
                updateModPackList();
                pack();
            }
        };
        pathField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                steamFolderSelector.actionPerformed(new ActionEvent(e.getSource(), e.getButton(), "Mouse Click"));
            }
        });
        buttonSelectPath.addActionListener(steamFolderSelector);

        pathLine.add(pathField);
        pathLine.add(buttonSelectPath);
        getContentPane().add(pathLine);

        packList = new JList<>();
        packList.setFont(getContentPane().getFont().deriveFont(14f));
        packList.setBackground(ELEMENT_BACKGROUND);
        packList.setForeground(ELEMENT_FOREROUND);
        packList.setSelectionBackground(ELEMENT_BACKGROUND);
        packList.setSelectionForeground(new Color(0xF21717));

        getContentPane().add(packList);

        updateModPackList();

        JPanel addMoreLine = new JPanel();
        addMoreLine.setBackground(null);
        JButton addMoreButton = new HoverButton("Install/update a Mod Pack...", ELEMENT_BACKGROUND, ELEMENT_FOREROUND, new Color(0x232323), ELEMENT_FOREROUND);
        addMoreButton.setFont(getContentPane().getFont().deriveFont(14f));
        addMoreButton.setBackground(ELEMENT_BACKGROUND);
        addMoreButton.setForeground(ELEMENT_FOREROUND.darker());
        addMoreButton.addActionListener(e -> {
            new ModInstallGui(launcher, this).setVisible(true);
        });

        addMoreLine.add(addMoreButton);
        getContentPane().add(addMoreLine);

        JButton launchGame = new HoverButton("Launch Game!", new Color(0xFFDE2A), Color.BLACK, new Color(0xF21717), Color.BLACK);
        launchGame.setBorder(new CompoundBorder(new LineBorder(Color.BLACK, 2), new EmptyBorder(10, 10, 10, 10)));
        launchGame.addActionListener(e -> {
            if (packList.getSelectedIndex() > -1 && packList.getSelectedIndex() < packList.getModel().getSize()) {
                ModPackListEntry modPack = packList.getModel().getElementAt(packList.getSelectedIndex());
                try {
                    launcher.launch(modPack.getModPack(), true);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            } else {
                JOptionPane.showMessageDialog(this, "Please select the ModPack to launch!");
            }
        });
        launchGame.setFont(getContentPane().getFont().deriveFont(Font.BOLD, 16f));

        JPanel launchLine = new JPanel();
        launchLine.setBackground(null);
        launchLine.add(launchGame);
        getContentPane().add(launchLine);

        pack();
        //setLocationByPlatform(true);
        setLocationRelativeTo(null);
    }

    private void updateModPackList() {
        if (launcher.getSteamGame() == null || !Files.exists(launcher.getSteamGame())) {
            JOptionPane.showMessageDialog(this, "Among Us not found in Steam game folder?");
        } else {
            DefaultListModel<ModPackListEntry> model = new DefaultListModel<>();
            int selected = -1;
            for (int i = 0; i < launcher.getModPacks().size(); i++) {
                Path modPack = launcher.getModPacks().get(i);
                if (modPack.getFileName().toString().equals(launcher.getSelected())) {
                    selected = i;
                }
                model.addElement(new ModPackListEntry(modPack));
            }
            packList.setModel(model);
            if (selected > -1) {
                packList.setSelectedIndex(selected);
            }
        }
        pack();
    }

    private class ModPackListEntry {
        private final Path modPack;

        public ModPackListEntry(Path modPack) {
            this.modPack = modPack;
        }

        public String toString() {
            return modPack.getFileName().toString().replace("Among Us - ", "");
        }

        public Path getModPack() {
            return modPack;
        }
    }

    private class HoverButton extends JButton {

        public HoverButton(String text, Color normalBackground, Color normalForeground, Color hoverBackground, Color hoverForeground) {
            super(text);
            setBackground(normalBackground);
            setForeground(normalForeground);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    super.mouseEntered(e);
                    setBackground(hoverBackground);
                    setForeground(hoverForeground);
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    super.mouseExited(e);
                    setBackground(normalBackground);
                    setForeground(normalForeground);
                    setCursor(Cursor.getDefaultCursor());
                }
            });
        }
    }

    private class ModInstallGui extends JFrame {
        public ModInstallGui(ModPackLauncher launcher, JFrame parent) {
            super("Install new Mod Pack!");
            setIconImage(launcher.getIcon());
            getContentPane().setBackground(new Color(0x161515));

            setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));

            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            JList<ModPackConfig> configList = new JList<>();
            configList.setFont(getContentPane().getFont().deriveFont(14f));
            configList.setBackground(ELEMENT_BACKGROUND);
            configList.setForeground(ELEMENT_FOREROUND);
            configList.setSelectionBackground(ELEMENT_BACKGROUND);
            configList.setSelectionForeground(new Color(0xF21717));
            DefaultListModel<ModPackConfig> model = new DefaultListModel<>();
            for (ModPackConfig config : launcher.getModPackConfigs()) {
                model.addElement(config);
            }
            configList.setModel(model);
            configList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    JList list = (JList) e.getSource();
                    int index = list.locationToIndex(e.getPoint());
                    ModPackConfig config = launcher.getModPackConfigs().get(index);
                    setVisible(false);
                    dispose();
                    String version = config.getSource().getLatestVersion(config);
                    try {
                        launcher.installModPack(config);
                        updateModPackList();
                        JOptionPane.showMessageDialog(parent, "Installed " + config.getName() + " " + version + " from " + config.getSource().getName());
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(parent, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        ex.printStackTrace();
                    }
                }
            });

            getContentPane().add(configList);

            pack();
            setLocationRelativeTo(parent);
        }
    }
}


