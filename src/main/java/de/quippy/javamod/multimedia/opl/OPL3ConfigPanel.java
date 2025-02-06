/*
 * @(#) OPL3ConfigPanel.java
 *
 * Created on 03.08.2020 by Daniel Becker
 *
 *-----------------------------------------------------------------------
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *----------------------------------------------------------------------
 */

package de.quippy.javamod.multimedia.opl;

import java.awt.GridBagLayout;
import java.awt.LayoutManager;
import java.awt.event.ItemEvent;
import java.io.File;
import java.io.Serial;
import java.util.Properties;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.filechooser.FileFilter;

import de.quippy.javamod.main.gui.tools.FileChooserFilter;
import de.quippy.javamod.main.gui.tools.FileChooserResult;
import de.quippy.javamod.multimedia.opl.emu.EmuOPL;
import de.quippy.javamod.multimedia.opl.emu.EmuOPL.Version;
import de.quippy.javamod.system.Helpers;


/**
 * @author Daniel Becker
 * @since 03.08.2020
 */
public class OPL3ConfigPanel extends JPanel {

    @Serial
    private static final long serialVersionUID = 2068150448569323448L;

    private JPanel checkboxConfigPanel = null;
    private JCheckBox virtualStereo = null;
    private JLabel oplVersionLabel = null;
    private JComboBox<String> oplVersion = null;
    private JLabel rolSoundBankLabel = null;
    private JTextField rolSoundBankUrl = null;
    private JButton searchButton = null;

    OPL3Container parentContainer = null;

    /**
     * Constructor for OPL3ConfigPanel
     */
    public OPL3ConfigPanel() {
        super();
        initialize();
    }

    /**
     * Constructor for OPL3ConfigPanel
     *
     * @param layout
     */
    public OPL3ConfigPanel(LayoutManager layout) {
        super(layout);
        initialize();
    }

    /**
     * Constructor for OPL3ConfigPanel
     *
     * @param isDoubleBuffered
     */
    public OPL3ConfigPanel(boolean isDoubleBuffered) {
        super(isDoubleBuffered);
        initialize();
    }

    /**
     * Constructor for OPL3ConfigPanel
     *
     * @param layout
     * @param isDoubleBuffered
     */
    public OPL3ConfigPanel(LayoutManager layout, boolean isDoubleBuffered) {
        super(layout, isDoubleBuffered);
        initialize();
    }

    /**
     * @return the parent
     */
    public OPL3Container getParentContainer() {
        return parentContainer;
    }

    /**
     * @param parent the parent to set
     */
    public void setParentContainer(OPL3Container parent) {
        this.parentContainer = parent;
    }

    private void initialize() {
        setName("OPL3ConfigPanel");
        setLayout(new GridBagLayout());

        add(getCheckboxConfigPanel(), Helpers.getGridBagConstraint(0, 0, 1, 0, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, 0.0, 0.0));
        add(getRolSoundBankLabel(), Helpers.getGridBagConstraint(0, 1, 1, 0, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, 0.0, 0.0));
        add(getRolSoundBankURL(), Helpers.getGridBagConstraint(0, 2, 1, 1, java.awt.GridBagConstraints.HORIZONTAL, java.awt.GridBagConstraints.WEST, 1.0, 0.0));
        add(getSearchButton(), Helpers.getGridBagConstraint(1, 2, 1, 0, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, 0.0, 0.0));
    }

    private JPanel getCheckboxConfigPanel() {
        if (checkboxConfigPanel == null) {
            checkboxConfigPanel = new JPanel();
            checkboxConfigPanel.setName("checkboxConfigPanel");
            checkboxConfigPanel.setLayout(new GridBagLayout());

            checkboxConfigPanel.add(getVirtualStereo(), Helpers.getGridBagConstraint(0, 0, 2, 1, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, 0.0, 0.0));
            checkboxConfigPanel.add(getOplVersionLabel(), Helpers.getGridBagConstraint(1, 0, 1, 1, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, 0.0, 0.0));
            checkboxConfigPanel.add(getOplVersion(), Helpers.getGridBagConstraint(2, 0, 1, 1, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, 0.0, 0.0));
        }
        return checkboxConfigPanel;
    }

    private JCheckBox getVirtualStereo() {
        if (virtualStereo == null) {
            virtualStereo = new JCheckBox();
            virtualStereo.setName("virtualStereo");
            virtualStereo.setText("Virtual Stereo Mix (not with OPL2)");
            virtualStereo.setFont(Helpers.getDialogFont());
            virtualStereo.addItemListener(e -> {
                if (e.getStateChange() == ItemEvent.SELECTED || e.getStateChange() == ItemEvent.DESELECTED) {
                    OPL3Container parent = getParentContainer();
                    if (parent != null) {
                        OPL3Mixer currentMixer = parent.getCurrentMixer();
                        if (currentMixer != null)
                            currentMixer.setDoVirtualStereoMix(getVirtualStereo().isSelected());
                    }
                }
            });
        }
        return virtualStereo;
    }

    private JLabel getOplVersionLabel() {
        if (oplVersionLabel == null) {
            oplVersionLabel = new JLabel();
            oplVersionLabel.setName("oplVersionLabel");
            oplVersionLabel.setText("OPL Version");
            oplVersionLabel.setFont(Helpers.getDialogFont());
        }
        return oplVersionLabel;
    }

    private JComboBox<String> getOplVersion() {
        if (oplVersion == null) {
            oplVersion = new JComboBox<>();
            oplVersion.setName("oplVersion");

            DefaultComboBoxModel<String> theModel = new DefaultComboBoxModel<>(EmuOPL.Version.versionNames());
            oplVersion.setModel(theModel);
            oplVersion.setFont(Helpers.getDialogFont());
            oplVersion.setEnabled(true);
        }
        return oplVersion;
    }

    private JLabel getRolSoundBankLabel() {
        if (rolSoundBankLabel == null) {
            rolSoundBankLabel = new JLabel("ROL Soundbank File");
            rolSoundBankLabel.setFont(Helpers.getDialogFont());
        }
        return rolSoundBankLabel;
    }

    private JTextField getRolSoundBankURL() {
        if (rolSoundBankUrl == null) {
            rolSoundBankUrl = new javax.swing.JTextField();
            rolSoundBankUrl.setColumns(20);
            rolSoundBankUrl.setFont(Helpers.getDialogFont());
        }
        return rolSoundBankUrl;
    }

    private void doSelectSoundbankFile() {
        FileFilter[] fileFilter = new FileFilter[] {new FileChooserFilter("*", "All files"), new FileChooserFilter("bnk", "AdLib Soundbank file (*.bnk)")};
        FileChooserResult selectedFile = Helpers.selectFileNameFor(this, null, "Select soundbank file", fileFilter, false, 0, false, false);
        if (selectedFile != null) {
            File select = selectedFile.getSelectedFile();
            getRolSoundBankURL().setText(select.toString());
        }
    }

    private JButton getSearchButton() {
        if (searchButton == null) {
            searchButton = new javax.swing.JButton();
            searchButton.setMnemonic('S');
            searchButton.setText("Search");
            searchButton.setFont(Helpers.getDialogFont());
            searchButton.setToolTipText("Search an AdLib soundbank file for the ROL synthesizer");
            searchButton.addActionListener(evt -> doSelectSoundbankFile());
        }
        return searchButton;
    }

    private Version getOPLVersion() {
        int index = getOplVersion().getSelectedIndex();
        return EmuOPL.Version.valueOf(index);
    }

    private void setOPLVersion(Version version) {
        int index = version.ordinal();
        getOplVersion().setSelectedIndex(index);
    }

    public void configurationChanged(Properties newProps) {
        getRolSoundBankURL().setText(newProps.getProperty(OPL3Container.PROPERTY_OPL3PLAYER_SOUNDBANK, OPL3Container.DEFAULT_SOUNDBANKURL));
        getVirtualStereo().setSelected(Boolean.parseBoolean(newProps.getProperty(OPL3Container.PROPERTY_OPL3PLAYER_VIRTUAL_STEREO, OPL3Container.DEFAULT_VIRTUAL_STEREO)));
        Version version = Enum.valueOf(Version.class, newProps.getProperty(OPL3Container.PROPERTY_OPL3PLAYER_OPLVERSION, OPL3Container.DEFAULT_OPLVERSION));
        setOPLVersion(version);
    }

    public void configurationSave(Properties props) {
        props.setProperty(OPL3Container.PROPERTY_OPL3PLAYER_SOUNDBANK, getRolSoundBankURL().getText());
        props.setProperty(OPL3Container.PROPERTY_OPL3PLAYER_VIRTUAL_STEREO, Boolean.toString(getVirtualStereo().isSelected()));
        props.setProperty(OPL3Container.PROPERTY_OPL3PLAYER_OPLVERSION, getOPLVersion().toString());
    }
}
