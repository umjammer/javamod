/*
 * @(#) PlayListGUI.java
 *
 * Created on 30.01.2011 by Daniel Becker
 *
 *-----------------------------------------------------------------------
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 3 of the License, or
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

package de.quippy.javamod.main.gui.playlist;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetContext;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.Serial;
import java.io.Serializable;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.html.HTMLDocument;

import de.quippy.javamod.main.gui.tools.FileChooserResult;
import de.quippy.javamod.main.gui.tools.PlaylistDropListener;
import de.quippy.javamod.main.gui.tools.PlaylistDropListenerCallBack;
import de.quippy.javamod.main.playlist.PlayList;
import de.quippy.javamod.main.playlist.PlayListEntry;
import de.quippy.javamod.main.playlist.PlaylistChangedListener;
import de.quippy.javamod.system.Helpers;
import de.quippy.javamod.system.StringUtils;

import static java.lang.System.getLogger;


/**
 * @author Daniel Becker
 * @since 30.01.2011
 */
public class PlayListGUI extends JPanel implements PlaylistChangedListener, PlaylistDropListenerCallBack {

    private static final Logger logger = getLogger(PlayListGUI.class.getName());

    @Serial
    private static final long serialVersionUID = -7914306014081401144L;

    /** lines to show more when scrolling */
    private static final int PLUS_LINES_VISABLE = 2;

    public static final String BUTTONSAVE = "/de/quippy/javamod/main/gui/ressources/save.gif";
    public static final String BUTTONSHUFFLE = "/de/quippy/javamod/main/gui/ressources/shuffle.gif";
    public static final String BUTTONREPEAT = "/de/quippy/javamod/main/gui/ressources/repeat.gif";
    public static final String BUTTONREPEAT_ACTIVE = "/de/quippy/javamod/main/gui/ressources/repeat_active.gif";
    public static final String BUTTONREPEAT_NORMAL = "/de/quippy/javamod/main/gui/ressources/repeat_normal.gif";

    private JDialog parentFrame = null;
    private PlayList playList;
    private PlayListEntry lastClickedEntry; // This entry is set if the mouse is pressed in an selected Entry

    private javax.swing.ImageIcon buttonSave = null;
    private javax.swing.ImageIcon buttonShuffle = null;
    private javax.swing.ImageIcon buttonRepeat = null;
    private javax.swing.ImageIcon buttonRepeat_Active = null;
    private javax.swing.ImageIcon buttonRepeat_normal = null;

    private javax.swing.JButton button_Save = null;
    private javax.swing.JButton button_Shuffle = null;
    private javax.swing.JButton button_Repeat = null;

    private JScrollPane scrollPane = null;
    private JTextPane textArea = null;
    private JPopupMenu playListPopUp = null;
    private JMenuItem popUpEntryDeleteFromList = null;
    private JMenuItem popUpEntryCropFromList = null;
    private JMenuItem popUpEntryRefreshEntry = null;
    private JMenuItem popUpEntryEditEntry = null;

    private EditPlaylistEntry editPlayListEntryDialog = null;

    private List<DropTarget> dropTargetList;

    private PlayListUpdateThread playlistUpdateThread;

    private String unmarkColorBackground;
    private String unmarkColorForeground;
    private String markColorBackground;
    private String markColorForeground;

    private final List<PlaylistGUIChangeListener> listeners = new ArrayList<>();

    private final static class InvisiableCaret implements Caret {

        private Point magicCaretPosition;
        private int dot;
        private int rate;

        public InvisiableCaret() {
            super();
        }

        @Override
        public void setVisible(boolean v) { /*NOOP*/ }

        @Override
        public void setSelectionVisible(boolean v) { /*NOOP*/ }

        @Override
        public boolean isVisible() {
            return false;
        }

        @Override
        public boolean isSelectionVisible() {
            return false;
        }

        @Override
        public void setMagicCaretPosition(Point p) {
            magicCaretPosition = p;
        }

        @Override
        public Point getMagicCaretPosition() {
            return magicCaretPosition;
        }

        @Override
        public void setDot(int dot) {
            this.dot = dot;
        }

        @Override
        public int getDot() {
            return dot;
        }

        @Override
        public void setBlinkRate(int rate) {
            this.rate = rate;
        }

        @Override
        public int getBlinkRate() {
            return rate;
        }

        @Override
        public void removeChangeListener(ChangeListener l) { /*NOOP*/ }

        @Override
        public void addChangeListener(ChangeListener l) { /*NOOP*/ }

        @Override
        public void paint(Graphics g) { /*NOOP*/ }

        @Override
        public void moveDot(int dot) { /*NOOP*/ }

        @Override
        public void install(JTextComponent c) { /*NOOP*/ }

        @Override
        public void deinstall(JTextComponent c) { /*NOOP*/ }

        @Override
        public int getMark() {
            return dot;
        }
    }

    private final static class PlayListUpdateThread extends Thread implements Serializable {

        @Serial
        private static final long serialVersionUID = -8105723830268691249L;

        private final PlayListGUI parent;
        private volatile boolean stopIt;
        private volatile boolean isStopped;
        private final boolean finished;

        public PlayListUpdateThread(PlayListGUI parent) {
            super();
            this.setName("PlayListUpdateThread::" + this.getClass().getName());
            this.setDaemon(true);
            this.setPriority(Thread.MIN_PRIORITY);
            this.parent = parent;
            this.isStopped = true;
            this.stopIt = false;
            this.finished = false;
        }

        public void halt() {
            if (!isStopped) {
                stopIt = true;
                this.interrupt();
                while (!isStopped) try {
                    PlayListUpdateThread.sleep(1L);
                } catch (InterruptedException ex) { /*NOOP*/ }
                stopIt = false;
            }
        }

//        public void finish() {
//            halt();
//            finished = true;
//        }

        public void restart() {
            stopIt = false;
            isStopped = false;
        }

        /**
         * @see java.lang.Thread#run()
         */
        @Override
        public void run() {
            while (!finished) {
                while (isStopped && !finished) try {
                    PlayListUpdateThread.sleep(1000L);
                } catch (InterruptedException ex) { /*NOOP*/ }
                try {
                    if (parent.playList != null) {
                        int size = parent.playList.size();
                        for (int index = 0; (index < size && !stopIt); index++)
                            parent.updateLine(index);
                    }
                } finally {
                    this.stopIt = false;
                    this.isStopped = true;
                }
            }
        }
    }

    /**
     * Constructor for PlayListGUI
     */
    public PlayListGUI(JDialog parentFrame) {
        super();
        this.parentFrame = parentFrame;
        initialize();
    }

    private void initialize() {
        setName("PlayList");
        setLayout(new java.awt.GridBagLayout());
        add(getScrollPane(), Helpers.getGridBagConstraint(0, 0, 1, 0, java.awt.GridBagConstraints.BOTH, java.awt.GridBagConstraints.WEST, 1.0, 1.0));
        add(getButton_Save(), Helpers.getGridBagConstraint(0, 1, 1, 1, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, 1.0, 0.0));
        add(getButton_Shuffle(), Helpers.getGridBagConstraint(1, 1, 1, 1, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.EAST, 1.0, 0.0));
        add(getButton_Repeat(), Helpers.getGridBagConstraint(2, 1, 1, 0, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.EAST, 0.0, 0.0));

        dropTargetList = new ArrayList<>();
        PlaylistDropListener myListener = new PlaylistDropListener(this);
        Helpers.registerDropListener(dropTargetList, this, myListener);

        unmarkColorBackground = Helpers.getHTMLColorString(getPlaylistTextArea().getBackground());
        unmarkColorForeground = Helpers.getHTMLColorString(getPlaylistTextArea().getForeground());
        markColorBackground = Helpers.getHTMLColorString(getPlaylistTextArea().getSelectionColor());
        markColorForeground = Helpers.getHTMLColorString(getPlaylistTextArea().getSelectedTextColor());
        playlistUpdateThread = new PlayListUpdateThread(this);
        playlistUpdateThread.start();
    }

    /**
     * @return
     * @since 07.11.2023
     */
    private JButton getButton_Save() {
        if (button_Save == null) {
            buttonSave = new javax.swing.ImageIcon(getClass().getResource(BUTTONSAVE));

            button_Save = new JButton();
            button_Save.setName("button_Save");
            button_Save.setText(Helpers.EMPTY_STING);
            button_Save.setToolTipText("<ctrl>-s: save playlist");
            button_Save.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
            button_Save.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
            button_Save.setIcon(buttonSave);
            button_Save.setDisabledIcon(buttonSave);
            button_Save.setPressedIcon(buttonSave);
            button_Save.setMargin(new java.awt.Insets(4, 6, 4, 6));
            button_Save.setFont(Helpers.getDialogFont());
            button_Save.addActionListener(e -> doSavePlayList());
        }
        return button_Save;
    }

    /**
     * @return
     * @since 22.11.2011
     */
    private JButton getButton_Repeat() {
        if (button_Repeat == null) {
            buttonRepeat = new javax.swing.ImageIcon(getClass().getResource(BUTTONREPEAT));
            buttonRepeat_Active = new javax.swing.ImageIcon(getClass().getResource(BUTTONREPEAT_ACTIVE));
            buttonRepeat_normal = new javax.swing.ImageIcon(getClass().getResource(BUTTONREPEAT_NORMAL));

            button_Repeat = new JButton();
            button_Repeat.setName("button_Repeat");
            button_Repeat.setText(Helpers.EMPTY_STING);
            button_Repeat.setToolTipText("<ctrl>-l repeat playlist");
            button_Repeat.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
            button_Repeat.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
            button_Repeat.setIcon(buttonRepeat);
            button_Repeat.setDisabledIcon(buttonRepeat);
            button_Repeat.setPressedIcon(buttonRepeat_normal);
            button_Repeat.setMargin(new java.awt.Insets(4, 6, 4, 6));
            button_Repeat.setFont(Helpers.getDialogFont());
            button_Repeat.addActionListener(e -> doToggleRepeat());
        }
        return button_Repeat;
    }

    /**
     * @return
     * @since 22.11.2011
     */
    private JButton getButton_Shuffle() {
        if (button_Shuffle == null) {
            buttonShuffle = new javax.swing.ImageIcon(getClass().getResource(BUTTONSHUFFLE));

            button_Shuffle = new JButton();
            button_Shuffle.setName("button_Shuffle");
            button_Shuffle.setText(Helpers.EMPTY_STING);
            button_Shuffle.setToolTipText("<ctrl>-r: shuffle playlist");
            button_Shuffle.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
            button_Shuffle.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
            button_Shuffle.setIcon(buttonShuffle);
            button_Shuffle.setDisabledIcon(buttonShuffle);
            button_Shuffle.setPressedIcon(buttonShuffle);
            button_Shuffle.setMargin(new java.awt.Insets(4, 6, 4, 6));
            button_Shuffle.setFont(Helpers.getDialogFont());
            button_Shuffle.addActionListener(e -> doShufflePlayList());
        }
        return button_Shuffle;
    }

    private javax.swing.JScrollPane getScrollPane() {
        if (scrollPane == null) {
            scrollPane = new javax.swing.JScrollPane();
            scrollPane.setName("scrollPane_TextField");
            scrollPane.setViewportView(getPlaylistTextArea());
            scrollPane.setDoubleBuffered(true);
        }
        return scrollPane;
    }

    private JTextPane getPlaylistTextArea() {
        if (textArea == null) {
            textArea = new JTextPane(new HTMLDocument());
            textArea.setContentType("text/html");
            textArea.setName("textArea");
            textArea.setEditable(false);
            textArea.setCaret(new InvisiableCaret());
            textArea.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.isConsumed() || playList == null) return;

                    if (e.isControlDown()) { // All CTRL-Combinations:
                        switch (e.getKeyCode()) {
                            case KeyEvent.VK_S:
                                doSavePlayList();
                                e.consume();
                                break;
                            case KeyEvent.VK_E:
                                doEditSelectedEntry();
                                e.consume();
                                break;
                            case KeyEvent.VK_U:
                                doUpdateSelectedEntryFromList();
                                e.consume();
                                break;
                            case KeyEvent.VK_L:
                                doToggleRepeat();
                                e.consume();
                                break;
                            case KeyEvent.VK_R:
                                doShufflePlayList();
                                e.consume();
                                break;
                            case KeyEvent.VK_A:
                                doSelectAll();
                                e.consume();
                                break;
                            case KeyEvent.VK_DELETE:
                                doCropSelectedEntryFromList();
                                e.consume();
                                break;
                        }
                    } else if (e.isShiftDown()) { // All SHIFT-Combinations
                        switch (e.getKeyCode()) {
                            case KeyEvent.VK_UP:
                                doChangeSelectionInList(-1);
                                e.consume();
                                break;
                            case KeyEvent.VK_DOWN:
                                doChangeSelectionInList(+1);
                                e.consume();
                                break;
                        }
                    } else if (e.isAltDown()) { // All ALT-Combinations
                        switch (e.getKeyCode()) {
                            case KeyEvent.VK_UP:
                                doMoveSelectedEntriesInList(-1);
                                e.consume();
                                break;
                            case KeyEvent.VK_DOWN:
                                doMoveSelectedEntriesInList(+1);
                                e.consume();
                                break;

                        }
                    } else if (!e.isAltGraphDown() && !e.isMetaDown()) {
                        switch (e.getKeyCode()) {
                            case KeyEvent.VK_DELETE:
                                doDeleteSelectedEntryFromList();
                                e.consume();
                                break;
                            case KeyEvent.VK_ESCAPE:
                                playList.setSelectedElement(-1);
                                e.consume();
                                break;
                            case KeyEvent.VK_HOME:
                                playList.setSelectedElement(0);
                                doMakeIndexVisible(0);
                                e.consume();
                                break;
                            case KeyEvent.VK_END:
                                int ende = playList.size() - 1;
                                playList.setSelectedElement(ende);
                                doMakeIndexVisible(ende);
                                e.consume();
                                break;
                            case KeyEvent.VK_UP:
                                doMoveSelectionInList(-1);
                                e.consume();
                                break;
                            case KeyEvent.VK_DOWN:
                                doMoveSelectionInList(+1);
                                e.consume();
                                break;
                            case KeyEvent.VK_PAGE_UP:
                                doMoveSelectionInList(-(getMaxVisableRows() - 1));
                                e.consume();
                                break;
                            case KeyEvent.VK_PAGE_DOWN:
                                doMoveSelectionInList(getMaxVisableRows() - 1);
                                e.consume();
                                break;
                            case KeyEvent.VK_ENTER:
                                doPlaySelectedPiece();
                                e.consume();
                                break;
                        }
                    }
                }
            });
            textArea.addMouseMotionListener(new MouseMotionListener() {
                @Override
                public void mouseMoved(MouseEvent e) {
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (e.isConsumed() || playList == null) return;

                    if (SwingUtilities.isLeftMouseButton(e)) {
                        int index = getSelectedIndexFromPoint(e.getPoint(), false);
                        if (index == -1) return;

                        PlayListEntry entry = playList.getEntry(index);
                        if (entry.isSelected() && lastClickedEntry == null)
                            lastClickedEntry = entry;
                        else {
                            if (lastClickedEntry != null) {
                                int moveBy = index - lastClickedEntry.getIndexInPlaylist();
                                if (moveBy == 0) return;

                                doMoveSelectedEntriesInList(moveBy);
                            }
                        }
                        e.consume();
                    }
                }
            });
            textArea.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.isConsumed() || playList == null) return;

                    lastClickedEntry = null;
                    int index = getSelectedIndexFromPoint(e.getPoint(), false);
                    if (index != -1) {
                        PlayListEntry entry = playList.getEntry(index);
                        if (e.isShiftDown()) { // Select multiple (area)
                            PlayListEntry[] alreadySelectedEntries = playList.getSelectedEntries();
                            if (alreadySelectedEntries == null)
                                playList.setSelectedElement(index);
                            else {
                                int fromIndex = alreadySelectedEntries[0].getIndexInPlaylist();
                                playList.setSelectedElements(fromIndex, index);
                            }
                            lastClickedEntry = entry;
                        } else if (e.isControlDown()) { // select multiple (individual)
                            playList.toggleSelectedElement(index);
                            lastClickedEntry = entry;
                        } else if (!entry.isSelected()) {
                            playList.setSelectedElement(index);
                            lastClickedEntry = entry;
                        }
                    } else {
                        playList.setSelectedElement(index);
                    }

                    if (SwingUtilities.isRightMouseButton(e)) {
                        getPopup().show(textArea, e.getX(), e.getY());
                    }
                    e.consume();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.isConsumed() || playList == null) return;

                    int index = getSelectedIndexFromPoint(e.getPoint(), false);
                    if (index != -1) {
                        if (SwingUtilities.isLeftMouseButton(e)) {
                            PlayListEntry entry = playList.getEntry(index);
                            if (e.getClickCount() > 1) {
                                doPlaySelectedPiece();
                            } else {
                                if (entry != null && lastClickedEntry == null)
                                    playList.setSelectedElement(index);
                            }
                            e.consume();
                        }
                    }
                }
            });
            createList(0);
        }
        return textArea;
    }

    private JPopupMenu getPopup() {
        if (playListPopUp == null) {
            playListPopUp = new javax.swing.JPopupMenu();
            playListPopUp.setName("playListPopUp");
            playListPopUp.add(getPopUpEntryDeleteFromList());
            playListPopUp.add(getPopUpEntryCropFromList());
            playListPopUp.add(new javax.swing.JSeparator());
            playListPopUp.add(getPopUpEntryRefreshEntry());
            playListPopUp.add(getPopUpEntryEditEntry());
        }
        boolean noEmptyList = (playList != null && playList.size() > 0);
        PlayListEntry[] selectedEntries = (noEmptyList) ? playList.getSelectedEntries() : null;
        boolean elementSpecificEntriesEnabled = (noEmptyList && selectedEntries != null);
        getPopUpEntryDeleteFromList().setEnabled(elementSpecificEntriesEnabled);
        getPopUpEntryRefreshEntry().setEnabled(elementSpecificEntriesEnabled);
        getPopUpEntryEditEntry().setEnabled(elementSpecificEntriesEnabled && selectedEntries != null && selectedEntries.length == 1);
        return playListPopUp;
    }

    private JMenuItem getPopUpEntryDeleteFromList() {
        if (popUpEntryDeleteFromList == null) {
            popUpEntryDeleteFromList = new javax.swing.JMenuItem();
            popUpEntryDeleteFromList.setName("JPopUpMenu_DeleteFromList");
            popUpEntryDeleteFromList.setText("<del> delete entry from list");
            popUpEntryDeleteFromList.addActionListener(
                    e -> doDeleteSelectedEntryFromList());
        }
        return popUpEntryDeleteFromList;
    }

    private JMenuItem getPopUpEntryCropFromList() {
        if (popUpEntryCropFromList == null) {
            popUpEntryCropFromList = new javax.swing.JMenuItem();
            popUpEntryCropFromList.setName("JPopUpMenu_CropFromList");
            popUpEntryCropFromList.setText("<ctrl-del> crop entry from list");
            popUpEntryCropFromList.addActionListener(
                    e -> doCropSelectedEntryFromList());
        }
        return popUpEntryCropFromList;
    }

    private JMenuItem getPopUpEntryRefreshEntry() {
        if (popUpEntryRefreshEntry == null) {
            popUpEntryRefreshEntry = new javax.swing.JMenuItem();
            popUpEntryRefreshEntry.setName("JPopUpMenu_RefreshEntry");
            popUpEntryRefreshEntry.setText("<ctrl-u> Refresh entry");
            popUpEntryRefreshEntry.addActionListener(
                    e -> doUpdateSelectedEntryFromList());
        }
        return popUpEntryRefreshEntry;
    }

    private JMenuItem getPopUpEntryEditEntry() {
        if (popUpEntryEditEntry == null) {
            popUpEntryEditEntry = new javax.swing.JMenuItem();
            popUpEntryEditEntry.setName("JPopUpMenu_EditEntry");
            popUpEntryEditEntry.setText("<ctrl-e> Edit entry");
            popUpEntryEditEntry.addActionListener(
                    e -> doEditSelectedEntry());
        }
        return popUpEntryEditEntry;
    }

    private EditPlaylistEntry getEditDialog() {
        if (editPlayListEntryDialog == null) {
            editPlayListEntryDialog = new EditPlaylistEntry(parentFrame, true);
            editPlayListEntryDialog.setSize(650, 150);
            editPlayListEntryDialog.setPreferredSize(editPlayListEntryDialog.getSize());
            editPlayListEntryDialog.pack();
            editPlayListEntryDialog.setLocation(Helpers.getFrameCenteredLocation(editPlayListEntryDialog, parentFrame));
        }
        return editPlayListEntryDialog;
    }

    private void doDeleteSelectedEntryFromList() {
        PlayListEntry[] selectedEntries = playList.getSelectedEntries();
        if (selectedEntries != null) {
            int lastIndex = selectedEntries.length - 1;
            if (lastIndex >= 0) {
                int markAfterDeletion = selectedEntries[0].getIndexInPlaylist();
                playlistUpdateThread.halt();
                try {
                    for (int i = 0; i <= lastIndex; i++) {
                        playList.remove(selectedEntries[i].getIndexInPlaylist());
                    }
                    createList(getFirstVisableIndex());
                    firePlaylistChanged();
                    playList.setSelectedElement((markAfterDeletion < 0) ? 0 : markAfterDeletion);
                    doMakeIndexVisible(markAfterDeletion);
                } finally {
                    playlistUpdateThread.restart();
                }
            }
        }
    }

    private void doCropSelectedEntryFromList() {
        PlayListEntry[] allEntries = playList.getAllEntries();
        if (allEntries != null) {
            playlistUpdateThread.halt();
            try {
                for (PlayListEntry allEntry : allEntries) {
                    if (!allEntry.isSelected())
                        playList.remove(allEntry.getIndexInPlaylist());
                }
                createList(getFirstVisableIndex());
                firePlaylistChanged();
            } finally {
                playlistUpdateThread.restart();
            }
        }
    }

    private void doUpdateSelectedEntryFromList() {
        PlayListEntry[] selectedEntries = playList.getSelectedEntries();
        if (selectedEntries != null) {
            playlistUpdateThread.halt();
            try {
                for (PlayListEntry selectedEntry : selectedEntries) {
                    selectedEntry.setSongName(null);
                    selectedEntry.setDuration(null);
                    updateLine(selectedEntry.getIndexInPlaylist());
                }
            } finally {
                playlistUpdateThread.restart();
            }
        }
    }

    private void doEditSelectedEntry() {
        PlayListEntry[] selectedEntries = playList.getSelectedEntries();
        if (selectedEntries != null) {
            playlistUpdateThread.halt();
            try {
                PlayListEntry entry = selectedEntries[0];
                EditPlaylistEntry editor = getEditDialog();
                editor.setValue(Helpers.createLocalFileStringFromURL(entry.getFile(), false));
                editor.setVisible(true);
                String newValue = editor.getValue();
                if (newValue != null) {
                    try {
                        URL url = Helpers.createURLfromString(newValue);
                        if (url != null) {
                            entry.setFile(url);
                            entry.setSongName(null);
                            entry.setDuration(null);
                            updateLine(entry.getIndexInPlaylist());
                        }
                    } catch (Throwable ex) {
                        JOptionPane.showMessageDialog(PlayListGUI.this, "Changing entry failed", "Failed", JOptionPane.ERROR_MESSAGE);
                    }
                }
            } finally {
                playlistUpdateThread.restart();
            }
        }
    }

    private void doSavePlayList() {
        if (playList != null) {
            do {
                String suggestedPath = Helpers.createLocalFileStringFromURL(playList.getLoadedFromURL(), true);
                FileChooserResult selectedFile = Helpers.selectFileNameFor(PlayListGUI.this, suggestedPath, "Save playlist to", new FileFilter[] {PlayList.PLAYLIST_FILE_FILTER}, false, 1, false, false);
                if (selectedFile != null) {
                    File f = selectedFile.getSelectedFile();
                    if (f != null) {
                        String filename = f.getAbsolutePath();
                        String fileNameLow = filename.toLowerCase();
                        if (!fileNameLow.endsWith("pls") && !fileNameLow.endsWith("m3u8") && !fileNameLow.endsWith("m3u") && !fileNameLow.endsWith("cue"))
                            f = new File(filename + ".M3U");

                        if (f.exists()) {
                            int result = JOptionPane.showConfirmDialog(PlayListGUI.this, "File already exists! Overwrite?", "Overwrite confirmation", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                            if (result == JOptionPane.CANCEL_OPTION) return;
                            if (result == JOptionPane.NO_OPTION) continue; // Reselect
                            boolean ok = f.delete();
                            if (!ok) {
                                JOptionPane.showMessageDialog(PlayListGUI.this, "Overwrite failed. Is file write protected or in use?", "Failed", JOptionPane.ERROR_MESSAGE);
                                return;
                            }
                        }
                        try {
                            playList.savePlayListTo(f);
                        } catch (Exception ex) {
                            logger.log(Level.ERROR, "Save playlist", ex);
                        }
                    }
                }
                return;
            }
            while (true);
        }
    }

    /**
     * @since 07.11.2023
     */
    private void doToggleRepeat() {
        if (playList != null) {
            playList.setRepeat(!playList.isRepeat());
            getButton_Repeat().setIcon(playList.isRepeat() ? buttonRepeat_Active : buttonRepeat);
        } else
            getButton_Repeat().setIcon(buttonRepeat);

        firePlaylistChanged();
    }

    /**
     * @since 04.09.2011
     */
    private void doShufflePlayList() {
        if (playList != null) {
            playlistUpdateThread.halt();
            try {
                playList.doShuffle();
                createList(getFirstVisableIndex());
            } finally {
                playlistUpdateThread.restart();
            }
        }
    }

    /**
     * @since 04.09.2011
     */
    private void doSelectAll() {
        playList.setSelectedElements(0, playList.size() - 1);
    }

    /**
     * Expands the selection (moving cursor with shift)
     *
     * @param moveBy
     * @since 15.12.2011
     */
    private void doChangeSelectionInList(int moveBy) {
        PlayListEntry[] selected = playList.getSelectedEntries();
        if (selected == null || selected.length == 0) {
            playList.setSelectedElement(0);
            doMakeIndexVisible(0);
        } else {
            int firstIndex = selected[0].getIndexInPlaylist();
            int lastIndex = selected[selected.length - 1].getIndexInPlaylist();
            if (moveBy < 0) {
                firstIndex += moveBy;
                if (firstIndex < 0) firstIndex = 0;
            } else {
                lastIndex += moveBy;
                if (lastIndex >= playList.size()) lastIndex = playList.size() - 1;
            }
            playList.setSelectedElements(firstIndex, lastIndex);
            if (moveBy < 0) doMakeIndexVisible(firstIndex);
            else doMakeIndexVisible(lastIndex);
        }
    }

    /**
     * Moves the selection (cursor)
     *
     * @param moveBy
     * @since 15.12.2011
     */
    private void doMoveSelectionInList(int moveBy) {
        PlayListEntry[] selected = playList.getSelectedEntries();
        if (selected == null || selected.length == 0) {
            playList.setSelectedElement(0);
            doMakeIndexVisible(0);
        } else {
            int index = selected[0].getIndexInPlaylist();
            index += moveBy;
            if (index < 0) index = 0;
            else if (index >= playList.size()) index = playList.size() - 1;
            playList.setSelectedElement(index);
            if (moveBy < 0) index -= PLUS_LINES_VISABLE;
            else index += PLUS_LINES_VISABLE;
            doMakeIndexVisible(index);
        }
    }

    /**
     * Move the selected entries (alt cursor)
     *
     * @param moveBy
     * @since 15.12.2011
     */
    private void doMoveSelectedEntriesInList(int moveBy) {
        PlayListEntry[] selected = playList.getSelectedEntries();
        if (selected != null) {
            playlistUpdateThread.halt();
            try {
                if (moveBy < 0) {
                    // Moveup --> Still possible?
                    if ((selected[0].getIndexInPlaylist() + moveBy) < 0)
                        moveBy = selected[0].getIndexInPlaylist();
                    if (moveBy == 0) return;

                    for (PlayListEntry playListEntry : selected) {
                        int fromIndex = playListEntry.getIndexInPlaylist();
                        playList.move(fromIndex, fromIndex + moveBy);
                    }
                } else {
                    // Movedown --> Still possible?
                    int lastIndex = selected.length - 1;
                    if ((selected[lastIndex].getIndexInPlaylist() + moveBy) >= playList.size())
                        moveBy = playList.size() - 1 - selected[lastIndex].getIndexInPlaylist();
                    if (moveBy == 0) return;

                    for (int i = lastIndex; i >= 0; i--) {
                        int fromIndex = selected[i].getIndexInPlaylist();
                        playList.move(fromIndex, fromIndex + moveBy);
                    }
                }
                int showIndex = (moveBy < 0) ? selected[0].getIndexInPlaylist() - PLUS_LINES_VISABLE : selected[selected.length - 1].getIndexInPlaylist() + PLUS_LINES_VISABLE;
                createList(showIndex);
                firePlaylistChanged();
            } finally {
                playlistUpdateThread.restart();
            }
        }
    }

    private void doPlaySelectedPiece() {
        PlayListEntry[] selected = playList.getSelectedEntries();
        if (selected != null) {
            int index = selected[0].getIndexInPlaylist();
            playList.setSelectedElement(index);
            playList.setCurrentElement(index);
            fireActiveElementChanged();
        }
    }

    private Element getTableRowDocumentElementForIndex(int index) {
        if (index > -1) {
            HTMLDocument doc = (HTMLDocument) getPlaylistTextArea().getDocument();
            if (doc != null) {
                return doc.getElement(getTableRowID(index));
            }
        }
        return null;
    }

    private Element getTextDocumentElementForIndex(int index) {
        if (index > -1) {
            HTMLDocument doc = (HTMLDocument) getPlaylistTextArea().getDocument();
            if (doc != null) {
                return doc.getElement(getTextID(index));
            }
        }
        return null;
    }

    private Element getDurationDocumentElementForIndex(int index) {
        if (index > -1) {
            HTMLDocument doc = (HTMLDocument) getPlaylistTextArea().getDocument();
            if (doc != null) {
                return doc.getElement(getDurationID(index));
            }
        }
        return null;
    }

    private int getMaxVisableRows() {
        Rectangle size = getScrollPane().getVisibleRect();
        Point p = new Point(0, (int) size.getHeight());
        return getSelectedIndexFromPoint(p, true) - 1;
    }

    private int getFirstVisableIndex() {
        int size = playList.size();
        for (int i = 0; i < size; i++) {
            Element element = getTableRowDocumentElementForIndex(i);
            if (element != null) {
                try {
                    Rectangle2D r1 = getPlaylistTextArea().modelToView2D(element.getStartOffset());
                    Rectangle2D r2 = getPlaylistTextArea().modelToView2D(element.getEndOffset() - 1);
                    Rectangle r = new Rectangle((int) r1.getX(), (int) r1.getY(), (int) (r2.getX() - r1.getX()), (int) r1.getHeight());
                    Rectangle intersect = r.intersection(getPlaylistTextArea().getVisibleRect());
                    if (!intersect.isEmpty() && intersect.height == r.height) return i;
                } catch (BadLocationException ex) {
                }
            }
        }
        return -1;
    }

    /**
     * @param position
     * @param returnNearest
     * @return -1, if unselectable Index, otherwise index of clicked Element
     * @since 23.03.2011
     */
    private int getSelectedIndexFromPoint(Point position, boolean returnNearest) {
        int modelPos = getPlaylistTextArea().viewToModel2D(position);

        HTMLDocument doc = (HTMLDocument) getPlaylistTextArea().getDocument();
        Element table = (doc != null) ? doc.getElement("TABLE") : null;
        if (table != null) {
            // get the index of the *nearest* element
            int index = table.getElementIndex(modelPos);
            // now lets check, if the user really hit the element or somewhere outside
            Element selectedElement = table.getElement(index);
            try {
                Rectangle2D r1 = getPlaylistTextArea().modelToView2D(selectedElement.getStartOffset());
                Rectangle2D r2 = getPlaylistTextArea().modelToView2D(selectedElement.getEndOffset() - 1);
                Rectangle r = new Rectangle((int) r1.getX(), (int) r1.getY(), (int) (r2.getX() - r1.getX()), (int) r1.getHeight());
                if (!returnNearest && !r.contains(position))
                    return -1;
                else if (returnNearest) {
                    if (position.getY() < r1.getY()) index--;
                    else if (position.getY() > r1.getY() + r1.getHeight()) index++;
                }
            } catch (BadLocationException ex) {
            }
            return index;
        }
        return -1;
    }

    /**
     * @param entry
     * @since 04.09.2011
     */
    private void doMakeIndexVisible(PlayListEntry entry) {
        if (entry != null) {
            try {
                Element element = getTextDocumentElementForIndex(entry.getIndexInPlaylist());
                if (element != null) {
                    try {
                        Rectangle2D r1 = getPlaylistTextArea().modelToView2D(element.getStartOffset());
                        Rectangle2D r2 = getPlaylistTextArea().modelToView2D(element.getEndOffset() - 1);

                        if (r1 != null && r2 != null) {
                            Rectangle r = new Rectangle((int) r1.getX(), (int) r1.getY(), (int) (r2.getX() - r1.getX()), (int) r1.getHeight());
                            getPlaylistTextArea().scrollRectToVisible(r);
                        }
                    } catch (BadLocationException e) {
                    }
                }
            } catch (Throwable ex) {
                logger.log(Level.ERROR, "PlayListGui::doMakeIndexVisible", ex);
            }
        }
    }

    /**
     * @param index
     * @since 04.09.2011
     */
    private void doMakeIndexVisible(int index) {
        if (playList != null) {
            if (index < 0) index = 0;
            else if (index >= playList.size()) index = playList.size() - 1;
            doMakeIndexVisible(playList.getEntry(index));
        }
    }

    /**
     * @param dtde
     * @param dropResult
     * @param addToLastLoaded
     * @see de.quippy.javamod.main.gui.tools.PlaylistDropListenerCallBack#playlistRecieved(java.awt.dnd.DropTargetDropEvent, de.quippy.javamod.main.playlist.PlayList, java.net.URL)
     * @since 08.03.2011
     */
    @Override
    public void playlistRecieved(DropTargetDropEvent dtde, PlayList dropResult, URL addToLastLoaded) {
        if (playList == null) {
            setNewPlaylist(dropResult);
            firePlaylistChanged();
        } else {
            playlistUpdateThread.halt();
            try {
                int index = 0;
                Point dropCoordinates = dtde.getLocation();
                DropTargetContext targetContext = dtde.getDropTargetContext();
                Component targetComponent = targetContext.getComponent();
                if (!targetComponent.equals(getPlaylistTextArea())) {
                    int middle = targetComponent.getHeight() >> 1;
                    if (dropCoordinates.getY() < middle) index = 0;
                    else index = playList.size();
                } else {
                    index = getSelectedIndexFromPoint(dropCoordinates, true);
                    if (index < 0) index = 0; // more Top than top is not OK ;)
                    else if (index > playList.size()) index = playList.size();
                }
                playList.addAllAt(index, dropResult);
                createList(getFirstVisableIndex());
                firePlaylistChanged();
            } finally {
                playlistUpdateThread.restart();
            }
        }
    }

    public synchronized void addPlaylistGUIChangeListener(PlaylistGUIChangeListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public synchronized void removePlaylistGUIChangeListener(PlaylistGUIChangeListener listener) {
        listeners.remove(listener);
    }

    private synchronized void fireActiveElementChanged() {
        int size = listeners.size();
        for (PlaylistGUIChangeListener listener : listeners) {
            listener.userSelectedPlaylistEntry();
        }
    }

    private synchronized void firePlaylistChanged() {
        int size = listeners.size();
        for (PlaylistGUIChangeListener listener : listeners) {
            listener.playListChanged(playList);
        }
    }

    private static String getTextID(int index) {
        return "TEXT_" + index;
    }

    private static String getDurationID(int index) {
        return "DURA_" + index;
    }

    private static String getTableRowID(int index) {
        return "ROW_" + index;
    }

    private String getFormattedSongName(PlayListEntry entry, boolean quick) {
        int digits = Integer.toString(playList.size()).length();
        String songName = (quick) ? entry.getQuickSongName() : entry.getFormattedName();
        String index = Integer.toString(entry.getIndexInPlaylist() + 1);
        String sb = "0".repeat(Math.max(0, digits - index.length())) +
                index + ". " + songName;
        return sb;
    }

    private String getHTMLString(PlayListEntry entry, int index, String songname, String duration) {
        String html = "<TR ID=\"" + getTableRowID(index) + "\" style=\"" +
                "background:#" + (entry.isSelected() ? markColorBackground : unmarkColorBackground) + "; " +
                "color:#" + (entry.isSelected() ? markColorForeground : unmarkColorForeground) + "; " +
                "font-family:" + Helpers.getTextAreaFont().getFamily() + "; " +
                "font-size:" + Helpers.getTextAreaFont().getSize() + ';' +
                "font-weight:" + (entry.isActive() ? "bold" : "normal") + ';' +
                "\"><TD ID=\"" + getTextID(index) + "\" align=\"left\" nowrap>" + StringUtils.encodeHtml(songname) +
                "</TD><TD ID=\"" + getDurationID(index) + "\" align=\"right\" nowrap>" + StringUtils.encodeHtml(duration) + "</TD></TR>";
        return html;
    }

    private void createList(int makeThisIndexVisable) {
        StringBuilder fullText = new StringBuilder("<HTML><HEAD><TITLE>PlayList</TITLE></HEAD><BODY><TABLE ID=\"TABLE\" WIDTH=\"100%\" BORDER=\"0\" CELLPADDING=\"0\" CELLSPACING=\"0\">");

        if (playList != null) {
            Iterator<PlayListEntry> iter = playList.getIterator();
            while (iter.hasNext()) {
                PlayListEntry entry = iter.next();
                fullText.append(getHTMLString(entry, entry.getIndexInPlaylist(), getFormattedSongName(entry, true), entry.getQuickDuration()));
            }
            getButton_Repeat().setIcon(playList.isRepeat() ? buttonRepeat_Active : buttonRepeat);
        } else
            getButton_Repeat().setIcon(buttonRepeat);

        fullText.append("</TABLE></FONT></BODY></HTML>");
        EventQueue.invokeLater(() -> {
            try {
                getPlaylistTextArea().setText(fullText.toString());
                getPlaylistTextArea().select(0, 0);
                doMakeIndexVisible((makeThisIndexVisable < 0) ? 0 : makeThisIndexVisable);
            } catch (Throwable ex) {
                logger.log(Level.ERROR, "PlayListGui::createList", ex);
            }
        });
    }

    public void setNewPlaylist(PlayList playList) {
        playlistUpdateThread.halt();
        try {
            this.playList = playList;
            this.playList.addPlaylistChangedListener(this);
            createList(0);
        } finally {
            playlistUpdateThread.restart();
        }
    }

    /**
     * @param index
     * @since 03.04.2011
     */
    private void updateLine(int index) {
        PlayListEntry entry = playList.getEntry(index);
        String text = getFormattedSongName(entry, false);
        String duration = entry.getDurationString();
        EventQueue.invokeLater(() -> setTextDecorationAndColorsFor(entry, text, duration));
    }

    /**
     * @param element
     * @return
     * @throws BadLocationException
     * @since 18.02.2011
     */
    private static String getText(Element element) throws BadLocationException {
        String text = element.getDocument().getText(element.getStartOffset(), element.getEndOffset() - element.getStartOffset());
        if (text.endsWith("\n"))
            return text.substring(0, text.length() - 1);
        else
            return text;
    }

    /**
     * @param entry
     * @param fast
     * @since 18.02.2011
     */
    private void setTextDecorationAndColorsFor(PlayListEntry entry, boolean fast) {
        try {
            int index = entry.getIndexInPlaylist();

            Element textElement = getTextDocumentElementForIndex(index);
            String text = (textElement != null && fast) ? getText(textElement) : getFormattedSongName(entry, false);

            Element durationElement = getDurationDocumentElementForIndex(index);
            String duration = (durationElement != null && fast) ? getText(durationElement) : entry.getDurationString();

            setTextDecorationAndColorsFor(entry, text, duration);
        } catch (Throwable ex) {
            logger.log(Level.ERROR, "PlayListGui::setTextDecorationAndColorsFor", ex);
        }
    }

    private void setTextDecorationAndColorsFor(PlayListEntry entry, String text, String duration) {
        try {
            int index = entry.getIndexInPlaylist();
            Element tableRowElement = getTableRowDocumentElementForIndex(index);
            if (tableRowElement != null) {
                HTMLDocument doc = (HTMLDocument) tableRowElement.getDocument();
                doc.setOuterHTML(tableRowElement, getHTMLString(entry, index, text, duration));
            }
        } catch (Throwable ex) {
            logger.log(Level.ERROR, "PlayListGui::setTextDecorationAndColorsFor", ex);
        }
    }

    /**
     * @param oldActiveElement
     * @param newActiveElement
     * @see de.quippy.javamod.main.playlist.PlaylistChangedListener#activeElementChanged(de.quippy.javamod.main.playlist.PlayListEntry, de.quippy.javamod.main.playlist.PlayListEntry)
     */
    @Override
    public void activeElementChanged(PlayListEntry oldActiveElement, PlayListEntry newActiveElement) {
        EventQueue.invokeLater(() -> {
            try {
                if (oldActiveElement != null) setTextDecorationAndColorsFor(oldActiveElement, true);
                if (newActiveElement != null) {
                    setTextDecorationAndColorsFor(newActiveElement, true);
                    doMakeIndexVisible(newActiveElement);
                }
            } catch (Throwable ex) {
                logger.log(Level.ERROR, "PlayListGui::activeElementChanged", ex);
            }
        });
    }

    /**
     * @param oldSelectedElement
     * @param newSelectedElement
     * @see de.quippy.javamod.main.playlist.PlaylistChangedListener#selectedElementChanged(de.quippy.javamod.main.playlist.PlayListEntry, de.quippy.javamod.main.playlist.PlayListEntry)
     * @since 23.03.2011
     */
    @Override
    public void selectedElementChanged(PlayListEntry oldSelectedElement, PlayListEntry newSelectedElement) {
        EventQueue.invokeLater(() -> {
            try {
                if (oldSelectedElement != null) setTextDecorationAndColorsFor(oldSelectedElement, true);
                if (newSelectedElement != null) {
                    setTextDecorationAndColorsFor(newSelectedElement, true);
                    doMakeIndexVisible(newSelectedElement);
                }
            } catch (Throwable ex) {
                logger.log(Level.ERROR, "PlayListGui::selectedElementChanged", ex);
            }
        });
    }

    /**
     * @return the Playlist in the gui
     * @since 08.11.2019
     */
    public PlayList getPlayList() {
        return playList;
    }
}