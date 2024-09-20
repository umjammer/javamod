/*
 * @(#) ModPatternDialog.java
 *
 * Created on 25.07.2020 by Daniel Becker
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

package de.quippy.javamod.multimedia.mod.gui;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.Serial;
import java.util.Arrays;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import de.quippy.javamod.mixer.Mixer;
import de.quippy.javamod.multimedia.mod.ModConstants;
import de.quippy.javamod.multimedia.mod.ModInfoPanel;
import de.quippy.javamod.multimedia.mod.ModMixer;
import de.quippy.javamod.multimedia.mod.loader.Module;
import de.quippy.javamod.multimedia.mod.loader.pattern.Pattern;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternContainer;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternElement;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternRow;
import de.quippy.javamod.multimedia.mod.mixer.BasicModMixer;
import de.quippy.javamod.system.Helpers;


/**
 * @author Daniel Becker
 * @since 25.07.2020
 */
public class ModPatternDialog extends JDialog implements ModUpdateListener {

    @Serial
    private static final long serialVersionUID = 4511905120124137632L;
    // This is a kind of a hack to fill the row - and it must have contend - otherwise it does not work
    private static final int ANZ_BUTTONS = 4;
    private static final JLabel EMPTY_LABEL_CHANNEL = new JLabel(" ");
    private static final GridBagConstraints EMPTY_LABEL_CONSTRAINT_CHANNEL = Helpers.getGridBagConstraint(0, 0, ANZ_BUTTONS, 1, java.awt.GridBagConstraints.HORIZONTAL, java.awt.GridBagConstraints.WEST, 1.0, 0.0, Helpers.NULL_INSETS);

    private static final int SOLOCHANNEL = 1;
    private static final int TOGGLEMUTE = 2;
    private static final int RESET = 3;

    private JPanel topArrangementPanel = null;
    private JButton nextPatternButton = null;
    private JButton prevPatternButton = null;
    private JCheckBox followSongCheckBox = null;
    private JLabel patternNameLabel = null;
    private JTextField patternNameField = null;
    private JPanel arrangementPanel = null;
    private JScrollPane scrollPane_ArrangementData = null;
    private ButtonGroup buttonGroup = null;
    private JToggleButton[] buttonArrangement;

    private PatternImagePanel patternImagePanel = null;
    private JScrollPane scrollPane_PatternData = null;

    private JPanel channelHeadlinePanel = null;
    private JButton[] channelButtons = null;
    private JButton[] peakMeterButtons = null;
    private JButton[] effectButtons = null;
    private JButton[] volEffectButtons = null;
    private JPopupMenu channelPopUp = null;
    private JMenuItem popUpEntrySoloChannel = null;
    private JMenuItem popUpEntryMuteChannel = null;
    private JMenuItem popUpEntryUnMuteAll = null;

    private JLabel patternNumberLabel = null;

    private Dimension PATTERNINDEX_BUTTON = null;
    private Dimension CHANNELPATTERNINDEX_SIZE = null;
    private Dimension CHANNELBUTTON_SIZE = null;

    private int[] arrangement = null;
    private boolean[] internalMuteStatus = null;

    private PatternContainer patternContainer = null;
    private int currentIndex;
    private int selectedChannelNumber = -1; // Popup on which channel?

    private String[] peekMeterColorStrings;

    private final ModInfoPanel myModInfoPanel;
    private Mixer currentMixer;
    private BasicModMixer currentModMixer;
    private boolean isPlaying = false;

    /**
     * Constructor for ModPatternDialog
     *
     * @param owner
     * @param modal
     * @param infoPanel
     */
    public ModPatternDialog(Window owner, boolean modal, ModInfoPanel infoPanel) {
        super(owner, modal ? DEFAULT_MODALITY_TYPE : ModalityType.MODELESS);
        myModInfoPanel = infoPanel;
        initialize();
    }

    private void initialize() {
        // Let's first create the normal and darker color for the meters
        peekMeterColorStrings = new String[16];
        for (int i = 0; i < 8; i++) {
            int r = i * 255 / 8;
            int g = 255 - r;
            final int b = 0;
            peekMeterColorStrings[i] = "#" + ModConstants.getAsHex(r, 2) + ModConstants.getAsHex(g, 2) + ModConstants.getAsHex(b, 2);
            Color buttonColor = PatternImagePanel.getButtonColor();
            peekMeterColorStrings[i + 8] = "#" + ModConstants.getAsHex(buttonColor.getRed(), 2) + ModConstants.getAsHex(buttonColor.getGreen(), 2) + ModConstants.getAsHex(buttonColor.getBlue(), 2);
        }

        CHANNELBUTTON_SIZE = new Dimension(getPatternImagePanel().getRowElementPixelWidth(), getPatternImagePanel().getRowElementPixelHeight());
        CHANNELPATTERNINDEX_SIZE = new Dimension(getPatternImagePanel().getButtonPixelWidth(), getPatternImagePanel().getButtonPixelHeight() * ANZ_BUTTONS); // three buttons stacked on top of each other

        Container baseContentPane = getContentPane();
        FontMetrics dialogMetrics = baseContentPane.getFontMetrics(Helpers.getDialogFont());
        PATTERNINDEX_BUTTON = new Dimension(dialogMetrics.charWidth('0') * 6, dialogMetrics.getHeight() * 2);

        baseContentPane.setLayout(new java.awt.GridBagLayout());

        baseContentPane.add(getTopArrangementPanel(), Helpers.getGridBagConstraint(0, 0, 1, 0, java.awt.GridBagConstraints.HORIZONTAL, java.awt.GridBagConstraints.WEST, 1.0, 0.0));
        baseContentPane.add(getScrollPane_PatternData(), Helpers.getGridBagConstraint(0, 1, 1, 0, java.awt.GridBagConstraints.BOTH, java.awt.GridBagConstraints.WEST, 1.0, 1.0));

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                doClose();
            }
        });

        setName("Show mod pattern");
        setTitle("Show mod pattern");
        setResizable(true);
        pack();
    }

    private void doClose() {
        setVisible(false);
        dispose();
    }

    private JPanel getTopArrangementPanel() {
        if (topArrangementPanel == null) {
            topArrangementPanel = new JPanel();
            topArrangementPanel.setLayout(new GridBagLayout());
            topArrangementPanel.add(getFollowSongCheckBox(), Helpers.getGridBagConstraint(0, 0, 1, 2, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.CENTER, 0.0, 0.0));
            topArrangementPanel.add(getPatternNameLabel(), Helpers.getGridBagConstraint(2, 0, 1, 1, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, 0.0, 0.0));
            topArrangementPanel.add(getPatternNameField(), Helpers.getGridBagConstraint(3, 0, 1, 1, java.awt.GridBagConstraints.HORIZONTAL, java.awt.GridBagConstraints.WEST, 0.0, 0.0));
            topArrangementPanel.add(getPrevPatternButton(), Helpers.getGridBagConstraint(0, 1, 1, 1, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, 0.0, 0.0));
            topArrangementPanel.add(getNextPatternButton(), Helpers.getGridBagConstraint(1, 1, 1, 1, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, 0.0, 0.0));
            topArrangementPanel.add(getScrollPane_ArrangementData(), Helpers.getGridBagConstraint(2, 1, 2, 2, java.awt.GridBagConstraints.BOTH, java.awt.GridBagConstraints.WEST, 1.0, 1.0));
            topArrangementPanel.add(new JLabel(" "), Helpers.getGridBagConstraint(0, 2, 1, 2, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, 0.0, 0.0));
        }
        return topArrangementPanel;
    }

    private JButton getPrevPatternButton() {
        if (prevPatternButton == null) {
            prevPatternButton = new JButton();
            prevPatternButton.setName("prevPatternButton");
            prevPatternButton.setText("<<");
            prevPatternButton.setFont(Helpers.getDialogFont());
            prevPatternButton.setToolTipText("Show previous pattern");
            prevPatternButton.addActionListener(e -> {
                if (arrangement != null && currentIndex > 0 && !isFollowSongActive())
                    setCurrentPattern(currentIndex - 1);
            });
        }
        return prevPatternButton;
    }

    private JButton getNextPatternButton() {
        if (nextPatternButton == null) {
            nextPatternButton = new JButton();
            nextPatternButton.setName("nextPatternButton");
            nextPatternButton.setText(">>");
            nextPatternButton.setFont(Helpers.getDialogFont());
            nextPatternButton.setToolTipText("Show previous pattern");
            nextPatternButton.addActionListener(e -> {
                if (arrangement != null && currentIndex < (arrangement.length - 1) && !isFollowSongActive())
                    setCurrentPattern(currentIndex + 1);
            });
        }
        return nextPatternButton;
    }

    private JCheckBox getFollowSongCheckBox() {
        if (followSongCheckBox == null) {
            followSongCheckBox = new JCheckBox();
            followSongCheckBox.setName("followSongCheckBox");
            followSongCheckBox.setText("Follow song");
            followSongCheckBox.setFont(Helpers.getDialogFont());
            followSongCheckBox.setToolTipText("Control whether to follow the song or not");
            followSongCheckBox.setSelected(true);
            // When "follow Song" is not selected, make the caret a bit lighter in color
            followSongCheckBox.addItemListener(e -> {
                if (e.getStateChange() == ItemEvent.SELECTED || e.getStateChange() == ItemEvent.DESELECTED) {
                    // If we want to follow the song, remove current Playing row indicator
                    if (getFollowSongCheckBox().isSelected()) setActivePlayingRow(null);
                }
            });
        }
        return followSongCheckBox;
    }

    private JLabel getPatternNameLabel() {
        if (patternNameLabel == null) {
            patternNameLabel = new JLabel();
            patternNameLabel.setName("patternNameLabel");
            patternNameLabel.setFont(Helpers.getDialogFont());
            patternNameLabel.setText("Pattern name:");
            patternNameLabel.setToolTipText("Current name of pattern");
        }
        return patternNameLabel;
    }

    private JTextField getPatternNameField() {
        if (patternNameField == null) {
            patternNameField = new JTextField();
            patternNameField.setName("patternNameField");
            patternNameField.setFont(Helpers.getDialogFont());
            patternNameField.setText(Helpers.EMPTY_STING);
            patternNameField.setToolTipText("Current name of pattern");
        }
        return patternNameField;
    }

    /**
     * @param patternIndex
     * @since 06.02.2024
     */
    private void setPatternNameField(int patternIndex) {
        Pattern pattern = patternContainer.getPattern(patternIndex);
        if (pattern != null) {
            String patternName = pattern.getPatternName();
            if (patternName != null) {
                getPatternNameField().setText('\n' + patternName);
                return;
            }
        }
        getPatternNameField().setText(Helpers.EMPTY_STING);
    }

    private JScrollPane getScrollPane_ArrangementData() {
        if (scrollPane_ArrangementData == null) {
            scrollPane_ArrangementData = new JScrollPane();
            scrollPane_ArrangementData.setName("scrollPane_ArrangementData");
            scrollPane_ArrangementData.setViewportView(getArrangementPanel());
        }
        return scrollPane_ArrangementData;
    }

    private JPanel getArrangementPanel() {
        if (arrangementPanel == null) {
            arrangementPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            fillButtonsForArrangement();
        }
        return arrangementPanel;
    }

    private JToggleButton createButtonForIndex(int index, int arrangementIndex, Dimension size) {
        JToggleButton newButton = new JToggleButton();
        newButton.setName("ArrangementButton_" + index);
        newButton.setText((arrangementIndex > -1) ? ModConstants.getAsHex(arrangementIndex, 2) : "--");
        newButton.setFont(Helpers.getDialogFont());
        newButton.setToolTipText("Show pattern " + arrangementIndex + " of arrangement index " + index);
        newButton.setMargin(Helpers.NULL_INSETS);
        newButton.setSize(size);
        newButton.setMinimumSize(size);
        newButton.setMaximumSize(size);
        newButton.setPreferredSize(size);
        if (arrangementIndex > -1) {
            newButton.addActionListener(evt -> {
                if (currentMixer != null && !currentMixer.isStopped() && currentModMixer != null) {
                    Module mod = currentModMixer.getMod();
                    if (mod != null) {
                        long seek = mod.getMsTimeIndex()[index];
                        if (seek >= 0) currentMixer.setMillisecondPosition(seek);
                    }
                } else {
                    if (!isFollowSongActive())
                        setCurrentPattern(index);
                    else
                        selectArrangementButton(currentIndex);
                }
            });
        }
        return newButton;
    }

    private void fillButtonsForArrangement() {
        int length = (arrangement == null) ? 25 : arrangement.length;

        getArrangementPanel().removeAll();
        buttonGroup = new ButtonGroup();

        buttonArrangement = new JToggleButton[length];
        for (int i = 0; i < length; i++) {
            buttonArrangement[i] = createButtonForIndex(i, (arrangement == null) ? -1 : arrangement[i], PATTERNINDEX_BUTTON);
            buttonGroup.add(buttonArrangement[i]);
            getArrangementPanel().add(buttonArrangement[i], i);
        }

        // Set scroll bar increments to the width/height of one button.
        JToggleButton firstButton = buttonArrangement[0];
        getScrollPane_ArrangementData().getHorizontalScrollBar().setUnitIncrement(firstButton.getWidth());
        getScrollPane_ArrangementData().getVerticalScrollBar().setUnitIncrement(firstButton.getHeight()); // BTW: we shall never see you!!

//		EventQueue.invokeLater(new Runnable()
//		{
//			public void run()
//			{				
        getScrollPane_ArrangementData().getHorizontalScrollBar().setValue(0);
        getScrollPane_ArrangementData().getVerticalScrollBar().setValue(0);
        getScrollPane_ArrangementData().repaint();
//			}
//		});
    }

    private JScrollPane getScrollPane_PatternData() {
        if (scrollPane_PatternData == null) {
            scrollPane_PatternData = new JScrollPane();
            scrollPane_PatternData.setName("scrollPane_PatternData");
            scrollPane_PatternData.setViewportView(getPatternImagePanel());
            scrollPane_PatternData.setColumnHeaderView(getChannelHeadlinePanel());
        }
        return scrollPane_PatternData;
    }

    private PatternImagePanel getPatternImagePanel() {
        if (patternImagePanel == null) {
            patternImagePanel = new PatternImagePanel();
            patternImagePanel.setName("modInfo_PatternData");
            patternImagePanel.setFont(Helpers.getTextAreaFont());
            patternImagePanel.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.isConsumed() || e.isShiftDown() || e.isMetaDown()) return;

                    if (isFollowSongActive()) {
                        e.consume();
                        return;
                    }

                    PatternImagePosition position = getPatternImagePanel().getCurrentEditingRow();
                    if (position != null && position.row != -1 && position.pattern != null) {
                        if (e.isAltDown() || e.isAltGraphDown()) {
                            switch (e.getKeyCode()) {
                                case KeyEvent.VK_LEFT:
                                    if (arrangement != null && currentIndex > 0)
                                        setCurrentPattern(currentIndex - 1);
                                    break;
                                case KeyEvent.VK_RIGHT:
                                    if (arrangement != null && currentIndex < (arrangement.length - 1))
                                        setCurrentPattern(currentIndex + 1);
                                    break;
                            }
                        } else if (e.isControlDown()) {
                            switch (e.getKeyCode()) {
                                case KeyEvent.VK_HOME:
                                    position.row = 0;
                                    normalizeAndDisplayEditingRow(position);
                                    e.consume();
                                    break;
                                case KeyEvent.VK_END:
                                    position.row = position.pattern.getRowCount() - 1;
                                    normalizeAndDisplayEditingRow(position);
                                    e.consume();
                                    break;
                                case KeyEvent.VK_LEFT:
                                    position.channel--;
                                    normalizeAndDisplayEditingRow(position);
                                    e.consume();
                                    break;
                                case KeyEvent.VK_RIGHT:
                                    position.channel++;
                                    normalizeAndDisplayEditingRow(position);
                                    e.consume();
                                    break;
                                case KeyEvent.VK_PAGE_UP:
                                    position.row -= 16;
                                    normalizeAndDisplayEditingRow(position);
                                    e.consume();
                                    break;
                                case KeyEvent.VK_PAGE_DOWN:
                                    position.row += 16;
                                    normalizeAndDisplayEditingRow(position);
                                    e.consume();
                                    break;
                            }
                        } else {
                            switch (e.getKeyCode()) {
                                case KeyEvent.VK_ESCAPE:
                                    setActiveEditingRow(currentIndex, null);
                                    e.consume();
                                    break;
                                case KeyEvent.VK_ENTER:
                                    if (position.column == PatternImagePosition.COLUMN_INSTRUMENT && myModInfoPanel != null) {
                                        PatternElement element = position.pattern.getPatternElement(position.row, position.channel);
                                        if (element != null)
                                            myModInfoPanel.showInstrument(element.getInstrument() - 1); // 0 is no Instrument...
                                    }
                                    break;
                                case KeyEvent.VK_HOME:
                                    position.column = 0;
                                    position.channel = 0;
                                    normalizeAndDisplayEditingRow(position);
                                    e.consume();
                                    break;
                                case KeyEvent.VK_END:
                                    position.column = PatternImagePosition.COLUMN_EFFECT_OP;
                                    position.channel = position.pattern.getChannels() - 1;
                                    normalizeAndDisplayEditingRow(position);
                                    e.consume();
                                    break;
                                case KeyEvent.VK_UP:
                                    position.row--;
                                    normalizeAndDisplayEditingRow(position);
                                    e.consume();
                                    break;
                                case KeyEvent.VK_DOWN:
                                    position.row++;
                                    normalizeAndDisplayEditingRow(position);
                                    e.consume();
                                    break;
                                case KeyEvent.VK_LEFT:
                                    position.column--;
                                    normalizeAndDisplayEditingRow(position);
                                    e.consume();
                                    break;
                                case KeyEvent.VK_RIGHT:
                                    position.column++;
                                    normalizeAndDisplayEditingRow(position);
                                    e.consume();
                                    break;
                                case KeyEvent.VK_PAGE_UP:
                                    position.row -= 4;
                                    normalizeAndDisplayEditingRow(position);
                                    e.consume();
                                    break;
                                case KeyEvent.VK_PAGE_DOWN:
                                    position.row += 4;
                                    normalizeAndDisplayEditingRow(position);
                                    e.consume();
                                    break;
                            }
                        }
                    }
                }
            });
            patternImagePanel.addMouseListener(new MouseAdapter() {
                /**
                 * @param e
                 * @see java.awt.event.MouseAdapter#mouseClicked(java.awt.event.MouseEvent)
                 */
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.isConsumed() || isFollowSongActive()) return;

                    if (SwingUtilities.isLeftMouseButton(e)) {
                        PatternImagePosition position = getPatternImagePanel().view2Model(e.getPoint());
                        if (position != null) {
                            normalizeAndDisplayEditingRow(position);
                            if (e.getClickCount() > 1 && position.pattern != null) {
                                PatternElement element = position.pattern.getPatternElement(position.row, position.channel);
                                if (position.column == PatternImagePosition.COLUMN_INSTRUMENT && element != null) {
                                    if (myModInfoPanel != null)
                                        myModInfoPanel.showInstrument(element.getInstrument() - 1); // 0 is no Instrument...
                                }
                            }
                            e.consume();
                        }
                    }
                }
            });
            patternImagePanel.addFocusListener(new FocusAdapter() {
                /**
                 * @param e
                 * @see java.awt.event.FocusAdapter#focusLost(java.awt.event.FocusEvent)
                 */
                @Override
                public void focusLost(FocusEvent e) {
                    // regain input focus, if the edit row is present
                    if (getPatternImagePanel().getCurrentEditingRow() != null)
                        getPatternImagePanel().requestFocusInWindow();
                }
            });
        }
        return patternImagePanel;
    }

    private JMenuItem getPopUpEntryUnMuteAll() {
        if (popUpEntryUnMuteAll == null) {
            popUpEntryUnMuteAll = new javax.swing.JMenuItem();
            popUpEntryUnMuteAll.setName("popUpEntryUnMuteAll");
            popUpEntryUnMuteAll.setText("Reset all channels");
            popUpEntryUnMuteAll.addActionListener(
                    e -> doMute(RESET, selectedChannelNumber));
        }
        return popUpEntryUnMuteAll;
    }

    private JMenuItem getPopUpEntrySoloChannel() {
        if (popUpEntrySoloChannel == null) {
            popUpEntrySoloChannel = new javax.swing.JMenuItem();
            popUpEntrySoloChannel.setName("popUpEntrySoloChannel");
            popUpEntrySoloChannel.setText("Solo channel");
            popUpEntrySoloChannel.addActionListener(
                    e -> doMute(SOLOCHANNEL, selectedChannelNumber));
        }
        return popUpEntrySoloChannel;
    }

    private JMenuItem getPopUpEntryMuteChannel() {
        if (popUpEntryMuteChannel == null) {
            popUpEntryMuteChannel = new javax.swing.JMenuItem();
            popUpEntryMuteChannel.setName("popUpEntryMuteChannel");
            popUpEntryMuteChannel.setText("(Un-)mute channel");
            popUpEntryMuteChannel.addActionListener(
                    e -> doMute(TOGGLEMUTE, selectedChannelNumber));
        }
        return popUpEntryMuteChannel;
    }

    private JPopupMenu getPopup() {
        if (channelPopUp == null) {
            channelPopUp = new javax.swing.JPopupMenu();
            channelPopUp.setName("channelPopUp");
            channelPopUp.add(getPopUpEntryUnMuteAll());
            channelPopUp.add(new javax.swing.JSeparator());
            channelPopUp.add(getPopUpEntrySoloChannel());
            channelPopUp.add(getPopUpEntryMuteChannel());
        }
//        getPopUpEntryUnMuteAll().setEnabled(currentModMixer != null);
//        getPopUpEntryMuteChannel().setEnabled(currentModMixer != null);
//        getPopUpEntrySoloChannel().setEnabled(currentModMixer != null);
        return channelPopUp;
    }

    private JPanel getChannelHeadlinePanel() {
        if (channelHeadlinePanel == null) {
            channelHeadlinePanel = new JPanel(new GridBagLayout());
            channelHeadlinePanel.setBackground(getPatternImagePanel().getBackground());
        }
        return channelHeadlinePanel;
    }

    private JLabel getPatternNumberLabel() {
        if (patternNumberLabel == null) {
            patternNumberLabel = new JLabel();
            patternNumberLabel.setName("patternNumberLabel");
            patternNumberLabel.setText(Helpers.EMPTY_STING);
            patternNumberLabel.setHorizontalAlignment(SwingConstants.CENTER);
            patternNumberLabel.setVerticalAlignment(SwingConstants.CENTER);
            patternNumberLabel.setFont(Helpers.getDialogFont());
            patternNumberLabel.setToolTipText("Current number of pattern");
            patternNumberLabel.setOpaque(true);
            patternNumberLabel.setBackground(PatternImagePanel.getButtonColor());
            patternNumberLabel.setBorder(null);
            patternNumberLabel.setSize(CHANNELPATTERNINDEX_SIZE);
            patternNumberLabel.setMinimumSize(CHANNELPATTERNINDEX_SIZE);
            patternNumberLabel.setMaximumSize(CHANNELPATTERNINDEX_SIZE);
            patternNumberLabel.setPreferredSize(CHANNELPATTERNINDEX_SIZE);
        }
        return patternNumberLabel;
    }

    /**
     * @param channel
     * @param highLeft
     * @param highRight
     * @return
     * @since 28.11.2023
     */
    private String createPeakMeter(int channel, int highLeft, int highRight, boolean isSurround) {
        StringBuilder sb = new StringBuilder("<html>");
        if (isSurround) {
            for (int i = 7; i > 0; i--)
                sb.append("<font color=").append(peekMeterColorStrings[(i > highLeft) ? i + 8 : i]).append(">)</font>");
            sb.append(' ');
            for (int i = 0; i < 8; i++)
                sb.append("<font color=").append(peekMeterColorStrings[(i < highRight) ? i : i + 8]).append(">(</font>");
        } else {
            for (int i = 7; i > 0; i--)
                sb.append("<font color=").append(peekMeterColorStrings[(i > highLeft) ? i + 8 : i]).append(">(</font>");
            sb.append(' ');
            for (int i = 0; i < 8; i++)
                sb.append("<font color=").append(peekMeterColorStrings[(i < highRight) ? i : i + 8]).append(">)</font>");
        }
        return sb.append("</html>").toString();
    }

    private String getChannelName(int channel) {
        StringBuilder sb = new StringBuilder();
        sb.append(channel + 1);
        if (patternContainer != null) {
            String chnName = patternContainer.getChannelName(channel);
            if (chnName != null) sb.append(':').append(chnName);
        }
        return sb.toString();
    }

    private Color getChannelColor(int channel) {
        if (patternContainer != null) {
            Color chnColor = patternContainer.getChannelColor(channel);
            if (chnColor != null) return chnColor;
        }
        return PatternImagePanel.getButtonColor();
    }

    /**
     * @param channelNumber
     * @return
     * @since 28.11.2023
     */
    private JButton createChannelButton(int channelNumber) {
        JButton channelButton = new JButton();
        channelButton.setName("Channel_" + channelNumber);
        channelButton.setText(getChannelName(channelNumber));
        channelButton.setHorizontalAlignment(SwingConstants.CENTER);
        channelButton.setFont(Helpers.getDialogFont());
        channelButton.setToolTipText("Channel " + (channelNumber + 1) + " mute/unmute");
        channelButton.setBackground(getChannelColor(channelNumber));
        channelButton.setBorder(null);
        channelButton.setBorderPainted(false);
        channelButton.setMargin(Helpers.NULL_INSETS);
        channelButton.setSize(CHANNELBUTTON_SIZE);
        channelButton.setMinimumSize(CHANNELBUTTON_SIZE);
        channelButton.setMaximumSize(CHANNELBUTTON_SIZE);
        channelButton.setPreferredSize(CHANNELBUTTON_SIZE);
        channelButton.addActionListener(e -> doMute(TOGGLEMUTE, channelNumber));
        channelButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isConsumed()) return;
                if (SwingUtilities.isRightMouseButton(e)) {
                    selectedChannelNumber = channelNumber;
                    getPopup().show(channelButton, e.getX(), e.getY());
                    e.consume();
                }
            }
        });
        return channelButton;
    }

    private JButton createPeakMeterButton(int channelNumber) {
        JButton channelButton = new JButton();
        channelButton.setName("Channel_" + channelNumber);
        channelButton.setText(createPeakMeter(channelNumber, 0, 0, false));
        channelButton.setHorizontalAlignment(SwingConstants.CENTER);
        channelButton.setFont(Helpers.getDialogFont());
        channelButton.setToolTipText("Channel " + (channelNumber + 1) + " mute/unmute");
        channelButton.setBackground(PatternImagePanel.getButtonColor());
        channelButton.setBorder(null);
        channelButton.setBorderPainted(false);
        channelButton.setMargin(Helpers.NULL_INSETS);
        channelButton.setSize(CHANNELBUTTON_SIZE);
        channelButton.setMinimumSize(CHANNELBUTTON_SIZE);
        channelButton.setMaximumSize(CHANNELBUTTON_SIZE);
        channelButton.setPreferredSize(CHANNELBUTTON_SIZE);
        channelButton.addActionListener(e -> doMute(TOGGLEMUTE, channelNumber));
        channelButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isConsumed()) return;
                if (SwingUtilities.isRightMouseButton(e)) {
                    selectedChannelNumber = channelNumber;
                    getPopup().show(channelButton, e.getX(), e.getY());
                    e.consume();
                }
            }
        });
        return channelButton;
    }

    private JButton createVolEffectLabel(int channelNumber) {
        JButton volEffectLabel = new JButton();
        volEffectLabel.setName("VolEffectLabel_" + channelNumber);
        volEffectLabel.setText(Helpers.EMPTY_STING);
        volEffectLabel.setHorizontalAlignment(SwingConstants.LEFT);
        volEffectLabel.setFont(Helpers.getDialogFont());
        volEffectLabel.setToolTipText("Channel " + (channelNumber + 1) + " current volume effect");
        volEffectLabel.setBackground(PatternImagePanel.getButtonColor());
        volEffectLabel.setBorder(null);
        volEffectLabel.setBorderPainted(false);
        volEffectLabel.setMargin(Helpers.NULL_INSETS);
        volEffectLabel.setSize(CHANNELBUTTON_SIZE);
        volEffectLabel.setMinimumSize(CHANNELBUTTON_SIZE);
        volEffectLabel.setMaximumSize(CHANNELBUTTON_SIZE);
        volEffectLabel.setPreferredSize(CHANNELBUTTON_SIZE);
        volEffectLabel.addActionListener(e -> doMute(TOGGLEMUTE, channelNumber));
        volEffectLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isConsumed()) return;
                if (SwingUtilities.isRightMouseButton(e)) {
                    selectedChannelNumber = channelNumber;
                    getPopup().show(volEffectLabel, e.getX(), e.getY());
                    e.consume();
                }
            }
        });
        return volEffectLabel;
    }

    private JButton createEffectLabel(int channelNumber) {
        JButton effectLabel = new JButton();
        effectLabel.setName("EffectLabel_" + channelNumber);
        effectLabel.setText(Helpers.EMPTY_STING);
        effectLabel.setHorizontalAlignment(SwingConstants.LEFT);
        effectLabel.setFont(Helpers.getDialogFont());
        effectLabel.setToolTipText("Channel " + (channelNumber + 1) + " current effect");
        effectLabel.setBackground(PatternImagePanel.getButtonColor());
        effectLabel.setBorder(null);
        effectLabel.setBorderPainted(false);
        effectLabel.setMargin(Helpers.NULL_INSETS);
        effectLabel.setSize(CHANNELBUTTON_SIZE);
        effectLabel.setMinimumSize(CHANNELBUTTON_SIZE);
        effectLabel.setMaximumSize(CHANNELBUTTON_SIZE);
        effectLabel.setPreferredSize(CHANNELBUTTON_SIZE);
        effectLabel.addActionListener(e -> doMute(TOGGLEMUTE, channelNumber));
        effectLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isConsumed()) return;
                if (SwingUtilities.isRightMouseButton(e)) {
                    selectedChannelNumber = channelNumber;
                    getPopup().show(effectLabel, e.getX(), e.getY());
                    e.consume();
                }
            }
        });
        return effectLabel;
    }

    /**
     * Create the buttons for the channels
     *
     * @param channels
     * @since 27.11.2023
     */
    private void createChannelButtons(int channels) {
        channelButtons = new JButton[channels];
        peakMeterButtons = new JButton[channels];
        effectButtons = new JButton[channels];
        volEffectButtons = new JButton[channels];

        internalMuteStatus = new boolean[channels];
        copyFromMixerMuteStatus();

        for (int i = 0; i < channels; i++) {
            channelButtons[i] = createChannelButton(i);
            peakMeterButtons[i] = createPeakMeterButton(i);
            volEffectButtons[i] = createVolEffectLabel(i);
            effectButtons[i] = createEffectLabel(i);
        }
    }

    /**
     * Fill the panel with all channel buttons. As these do not change
     * during one piece, we will create them once, not every pattern change
     *
     * @param patternIndex
     * @param channels
     * @since 27.11.2023
     */
    private void fillButtonsForChannels(int patternIndex, int channels) {
        getChannelHeadlinePanel().removeAll();
        getChannelHeadlinePanel().add(getPatternNumberLabel(), Helpers.getGridBagConstraint(0, 0, ANZ_BUTTONS, 1, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, 0.0, 0.0, Helpers.NULL_INSETS));
        for (int i = 0; i < channels; i++) {
            getChannelHeadlinePanel().add(channelButtons[i], Helpers.getGridBagConstraint(i + 1, 0, 1, 1, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, 0.0, 0.0, Helpers.NULL_INSETS));
            getChannelHeadlinePanel().add(peakMeterButtons[i], Helpers.getGridBagConstraint(i + 1, 1, 1, 1, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, 0.0, 0.0, Helpers.NULL_INSETS));
            getChannelHeadlinePanel().add(volEffectButtons[i], Helpers.getGridBagConstraint(i + 1, 2, 1, 1, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, 0.0, 0.0, Helpers.NULL_INSETS));
            getChannelHeadlinePanel().add(effectButtons[i], Helpers.getGridBagConstraint(i + 1, 3, 1, 1, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, 0.0, 0.0, Helpers.NULL_INSETS));
        }
        // Hack: to make the buttons stay and not get horizontally centered, we give the panel something to make bigger instead.
        EMPTY_LABEL_CONSTRAINT_CHANNEL.gridx = channels;
        getChannelHeadlinePanel().add(EMPTY_LABEL_CHANNEL, EMPTY_LABEL_CONSTRAINT_CHANNEL);
        getChannelHeadlinePanel().repaint();
    }

    /**
     * @return if is playing and the follow song is active
     * @since 28.11.2023
     */
    private boolean isFollowSongActive() {
        return isPlaying && followSongCheckBox.isSelected();
    }

    /**
     * Update the Strings on the channelButtons with new colors depending on volume.
     *
     * @param peekVolumeLeft
     * @param peekVolumeRight
     * @since 28.11.2023
     */
    private void updateVolume(int channel, int peekVolumeLeft, int peekVolumeRight, boolean isSurround) {
        // because of possible race conditions (EventQueue.invokeLater) a new mod can already be loaded while the old updater is still updating for an old mod
        // this should not happen, but occasional it does
        if (channelButtons != null && channel < channelButtons.length)
            peakMeterButtons[channel].setText(createPeakMeter(channel, peekVolumeLeft, peekVolumeRight, isSurround));
    }

    /**
     * @since 28.11.2023
     */
    private void resetVolume() {
        if (patternContainer != null) {
            int channels = channelButtons.length;
            for (int c = 0; c < channels; c++) {
                updateVolume(c, 0, 0, false);
            }
        }
    }

    /**
     * @since 28.11.2023
     */
    private void updateMuteStatus() {
        if (patternContainer != null) { // if we do not display anything, there is nothing to update
            boolean[] muteStatus = (currentModMixer != null) ? currentModMixer.getMuteStatus() : null;
            int channels = patternContainer.getChannels();
            for (int i = 0; i < channels; i++) {
                if (!patternContainer.getIsChannelActive(i))
                    channelButtons[i].setForeground(Color.lightGray);
                else if (muteStatus != null && i < muteStatus.length && i < internalMuteStatus.length)
                    channelButtons[i].setForeground((!(internalMuteStatus[i] = muteStatus[i])) ? Color.black : Color.lightGray);
                else if (muteStatus == null && i < internalMuteStatus.length)
                    channelButtons[i].setForeground((!internalMuteStatus[i]) ? Color.black : Color.lightGray);
            }
        }
    }

    /**
     * @since 18.03.2024
     */
    private void copyToMixerMuteStatus() {
        if (currentMixer != null) {
            for (int i = 0; i < internalMuteStatus.length; i++) {
                currentModMixer.setMuteChannel(i, internalMuteStatus[i]);
            }
        }
    }

    /**
     * @since 18.03.2024
     */
    private void copyFromMixerMuteStatus() {
        if (currentMixer != null) {
            boolean[] muteStatus = currentModMixer.getMuteStatus();
            for (int i = 0; i < internalMuteStatus.length; i++) {
                if (muteStatus != null && i < muteStatus.length) {
                    internalMuteStatus[i] = muteStatus[i];
                }
            }

        }
    }

    /**
     * @param channelNumber
     * @since 18.03.2024
     */
    public void makeChannelSolo(int channelNumber) {
        if (internalMuteStatus != null && channelNumber >= 0 && channelNumber <= internalMuteStatus.length) {
            for (int c = 0; c < internalMuteStatus.length; c++) {
                internalMuteStatus[c] = c != channelNumber;
            }
        }
    }

    /**
     * @param channelNumber
     * @since 18.03.2024
     */
    public void toggleMuteChannel(int channelNumber) {
        if (internalMuteStatus != null && channelNumber >= 0 && channelNumber <= internalMuteStatus.length) {
            internalMuteStatus[channelNumber] = !internalMuteStatus[channelNumber];
        }
    }

    /**
     * @since 18.03.2024
     */
    public void unMuteAll() {
        if (internalMuteStatus != null) {
            Arrays.fill(internalMuteStatus, false);
        }
    }

    /**
     * @param whatMute
     * @param channelNumber
     * @since 28.11.2023
     */
    private void doMute(int whatMute, int channelNumber) {
        selectedChannelNumber = channelNumber;
        if (internalMuteStatus != null && channelNumber <= internalMuteStatus.length) {
            switch (whatMute) {
                case SOLOCHANNEL:
                    makeChannelSolo(channelNumber);
                    break;
                case TOGGLEMUTE:
                    toggleMuteChannel(channelNumber);
                    break;
                case RESET:    // Unmute all / reset to defaults
                default:    // is also the default fall through
                    unMuteAll();
            }
            copyToMixerMuteStatus();
            updateMuteStatus();
        }
    }

    /**
     * @param index
     * @since 07.01.2024
     */
    private void selectArrangementButton(int index) {
        if (index >= buttonArrangement.length) return; // just to be save

        // set the correct Button of the arrangement
        JToggleButton theButton = buttonArrangement[index];
        theButton.setSelected(true);
        // now scroll to button to become visible
        getArrangementPanel().scrollRectToVisible(theButton.getBounds());
        // and display patternNumber
        getPatternNumberLabel().setText('#' + ModConstants.getAsHex(arrangement[index], 2));
        setPatternNameField(arrangement[index]);
    }

    private Pattern getPrevPattern(int index) {
        return (index - 1) >= 0 ? patternContainer.getPattern(arrangement[index - 1]) : null;
    }

    private Pattern getNextPattern(int index) {
        return (index + 1) < arrangement.length ? patternContainer.getPattern(arrangement[index + 1]) : null;
    }

    private void setActivePlayingRow(PatternImagePosition position) {
        EventQueue.invokeLater(() -> {
            try {
                getPatternImagePanel().setActivePlayingRow(position);
            } catch (Throwable ex) { /*NOOP*/ }
        });
    }

    private void setCurrentPattern(int index) {
        PatternImagePosition position = getPatternImagePanel().getCurrentEditingRow();
        if (position != null) {
            position.pattern = patternContainer.getPattern(arrangement[index]);
            position.row = 0;
            setActiveEditingRow(index, position);
        } else {
            EventQueue.invokeLater(() -> {
                try {
                    if (index != currentIndex) selectArrangementButton(currentIndex = index);
                    getPatternImagePanel().setCurrentPattern(getPrevPattern(index), patternContainer.getPattern(arrangement[index]), getNextPattern(index));
                } catch (Throwable ex) { /*NOOP*/ }
            });
        }
    }

    private void setActiveEditingRow(int index, PatternImagePosition position) {
        EventQueue.invokeLater(() -> {
            try {
                if (index != currentIndex) selectArrangementButton(currentIndex = index);
                getPatternImagePanel().setActiveEditingRow(getPrevPattern(index), patternContainer.getPattern(arrangement[index]), getNextPattern(index), position);
                if (position != null) {
                    showCurrentEffectNames(position.pattern, position.row);
                    if (!getPatternImagePanel().hasFocus()) getPatternImagePanel().requestFocusInWindow();
                } else {
                    clearCurrentEffectNames();
                }
            } catch (Throwable ex) { /*NOOP*/ }
        });
    }

    private void normalizeAndDisplayEditingRow(PatternImagePosition position) {
        if (position.column <= PatternImagePosition.COLUMN_BEYOND_LEFT) {
            if (position.channel > 0) {
                position.channel--;
                position.column = PatternImagePosition.COLUMN_EFFECT_OP;
            } else
                position.column = PatternImagePosition.COLUMN_NOTE;
        } else if (position.column >= PatternImagePosition.COLUMN_BEYOND_RIGHT) {
            if (position.channel < (position.pattern.getChannels() - 1)) {
                position.channel++;
                position.column = PatternImagePosition.COLUMN_NOTE;
            } else
                position.column = PatternImagePosition.COLUMN_EFFECT_OP;
        }
        if (position.channel < 0) {
            position.channel = 0;
        } else if (position.channel >= position.pattern.getChannels()) {
            position.channel = position.pattern.getChannels() - 1;
        }
        if (position.row < 0) {
            if (currentIndex <= 0)
                position.row = 0;
            else {
                position.pattern = patternContainer.getPattern(arrangement[currentIndex - 1]);
                position.row += position.pattern.getRowCount();
            }
        } else if (position.row >= position.pattern.getRowCount()) {
            if (currentIndex >= arrangement.length - 1)
                position.row = position.pattern.getRowCount() - 1;
            else {
                position.row -= position.pattern.getRowCount();
                position.pattern = patternContainer.getPattern(arrangement[currentIndex + 1]);
            }
        }
        int index = currentIndex;

        if (position.pattern == getPrevPattern(index))
            index--;
        else if (position.pattern == getNextPattern(index))
            index++;

        setActiveEditingRow(index, position);
    }

    private void showCurrentEffectNames(Pattern pattern, int row) {
        if (effectButtons != null) {
            int channels = pattern.getChannels();
            PatternRow patternRow = pattern.getPatternRow(row);
            for (int c = 0; c < channels; c++) {
                PatternElement element = patternRow.getPatternElement(c);
                effectButtons[c].setText(element.getEffectName());
                volEffectButtons[c].setText(element.getVolEffectName());
            }
        }
    }

    private void clearCurrentEffectNames() {
        if (effectButtons != null) {
            int channels = effectButtons.length;
            for (int c = 0; c < channels; c++) {
                effectButtons[c].setText(Helpers.EMPTY_STING);
                volEffectButtons[c].setText(Helpers.EMPTY_STING);
            }
        }
    }

    /**
     * Do everything that is needed to display a new pattern.
     *
     * @param information
     * @since 07.01.2024
     */
    private void displayPattern(PatternPositionInformation information) {
        if (information != null) {
            int index = information.patternIndex;
            Pattern pattern = patternContainer.getPattern(arrangement[index]);

            if (pattern != null) {
                int row = information.patternRow;
                // checks followSong: if active, the editing row moves the pattern, otherwise we will not move
                PatternImagePosition position = new PatternImagePosition(pattern, row);
                if (isFollowSongActive())
                    setActiveEditingRow(index, position);
                else
                    setActivePlayingRow(position);
            }
        }
    }

    /**
     * This method will get called from outside to set a new MOD.
     *
     * @param songLength
     * @param newArrangement
     * @param newPatternContainer
     * @since 28.11.2023
     */
    public void fillWithPatternArray(int modID, int songLength, int[] newArrangement, PatternContainer newPatternContainer) {
        patternContainer = newPatternContainer;
        currentIndex = -1;

        // We need to copy the arrangement to songLength values
        arrangement = new int[songLength];
        for (int i = 0; i < songLength; i++) arrangement[i] = newArrangement[i];

        createChannelButtons(patternContainer.getChannels());

        EventQueue.invokeLater(() -> {
            try {
                // and then display them
                fillButtonsForArrangement();
                fillButtonsForChannels(0, patternContainer.getChannels());
                setCurrentPattern(0);
                setPreferredSize(getSize());
                updateMuteStatus();
                pack();
            } catch (Throwable ex) {
                // Keep it!
            }
        });
    }

    /**
     * For mute/unmute we need the current Mixer.
     * ModContainer will take care of setting it. If no mixer is present,
     * it is set to "null" here!
     * ModMixer must already have a BasicModMixer created when setting it here.
     *
     * @param theModMixer the ModMixer. If set to NULL we deregister the listeners and stop the update thread.
     * @since 28.11.2023
     */
    public void setMixer(ModMixer theModMixer) {
        if (theModMixer != null) {
            currentMixer = theModMixer;
            currentModMixer = theModMixer.getModMixer();
            if (currentModMixer != null) {
                copyToMixerMuteStatus();
                updateMuteStatus(); // THIS DOES ONLY WORK, IF THE PIECE IN HERE FITS TO THE MIXER! ModContainer takes care of that...
            }
        } else {
            currentMixer = null;
            currentModMixer = null;
        }
    }

    @Override
    public void getPatternPositionInformation(PatternPositionInformation infoObject) {
        if (isVisible()) displayPattern(infoObject);
    }

    @Override
    public void getPeekInformation(PeekInformation infoObject) {
        if (isVisible())
            updateVolume(infoObject.channel, infoObject.actPeekLeft, infoObject.actPeekRight, infoObject.isSurround);
    }

    @Override
    public void getStatusInformation(StatusInformation infoObject) {
        isPlaying = infoObject.status;
        if (isFollowSongActive()) {
            // if we start playing and follow the song, hide the editing row
            setActiveEditingRow(currentIndex, null);
        } else if (!isPlaying) {
            if (currentIndex < 0) return; // Obviously we do not display anything yet, so nothing to do
            EventQueue.invokeLater(() -> {
                try {
                    resetVolume();
                    clearCurrentEffectNames();
                    setActivePlayingRow(null);
                    setActiveEditingRow(currentIndex, null);
                } catch (Throwable ex) {
                    // Keep it!
                }
            });
        }
    }
}
