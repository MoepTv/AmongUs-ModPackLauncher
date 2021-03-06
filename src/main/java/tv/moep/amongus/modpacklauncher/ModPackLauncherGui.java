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
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
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
    private final JList packList;

    public ModPackLauncherGui(ModPackLauncher launcher) {
        super(launcher.getName() + " v" + launcher.getVersion());
        this.launcher = launcher;
        setIconImage(launcher.getIcon());
        getContentPane().setBackground(new Color(0x161515));
        getContentPane().setFont(getContentPane().getFont().deriveFont(20f));

        setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel pathLine = new JPanel();
        pathLine.setBackground(null);
        JTextField pathField = new JTextField(20);
        pathField.setBackground(ELEMENT_BACKGROUND);
        pathField.setForeground(ELEMENT_FOREROUND);

        File steamFolder = launcher.getSteamFolder();
        if (steamFolder != null && steamFolder.exists() && steamFolder.isDirectory()) {
            pathField.setText(steamFolder.getAbsolutePath());
        }
        pathField.setEditable(false);
        pathField.setBorder(new CompoundBorder(new LineBorder(Color.BLACK, 1), new EmptyBorder(4, 4, 4, 4)));

        JButton buttonSelectPath = new HoverButton("Select Steam game folder", ELEMENT_BACKGROUND, ELEMENT_FOREROUND, new Color(0x343434), ELEMENT_FOREROUND);
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
            JFileChooser chooser = pathField.getText().isEmpty() || path.isEmpty() ? new JFileChooser()
                    : new JFileChooser(new File(path).getParentFile());
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

        packList = new JList();
        packList.setBackground(ELEMENT_BACKGROUND);
        packList.setForeground(ELEMENT_FOREROUND);
        packList.setSelectionBackground(ELEMENT_BACKGROUND);
        packList.setSelectionForeground(new Color(0xF21717));

        getContentPane().add(packList);

        updateModPackList();

        JButton launchGame = new HoverButton("Launch Game!", new Color(0xFFDE2A), Color.BLACK, new Color(0xF21717), Color.BLACK);
        launchGame.setBorder(new CompoundBorder(new LineBorder(Color.BLACK, 2), new EmptyBorder(10, 10, 10, 10)));
        launchGame.addActionListener(e -> {
            if (packList.getSelectedIndex() > -1 && packList.getSelectedIndex() < packList.getModel().getSize()) {
                ModPackListEntry modPack = (ModPackListEntry) packList.getModel().getElementAt(packList.getSelectedIndex());
                // TODO: Launch via Steam?
                launcher.launch(modPack.getModPack(), true);
            } else {
                JOptionPane.showMessageDialog(this, "Please select the ModPack to launch!");
            }
        });

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
            DefaultListModel model = new DefaultListModel();
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
    }

    private class ModPackListEntry {
        private final Path modPack;

        public ModPackListEntry(Path modPack) {
            this.modPack = modPack;
        }

        public String toString() {
            return modPack.getFileName().toString();
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
}


