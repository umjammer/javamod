/*
 * @(#) PitchShiftGUI.java
 *
 * Created on 21.01.2012 by Daniel Becker
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

package de.quippy.javamod.mixer.dsp.pitchshift;

import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.Serial;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.border.TitledBorder;

import de.quippy.javamod.system.Helpers;


/**
 * @author Daniel Becker
 * @since 21.01.2012
 */
public class PitchShiftGUI extends JPanel {

    @Serial
    private static final long serialVersionUID = 4300230957744752568L;

    private static final int SHIFT = 100;
    private static final int SLIDER_MAX = +1 * SHIFT;
    private static final int SLIDER_MIN = -1 * SHIFT;
    private static final double factor = Math.log10(2d);

    private final PitchShift thePitcher;

    private JCheckBox pitchShiftActive = null;
    private JPanel pitchShiftPanel = null;
    private JPanel scaleShiftPanel = null;
    private JSlider pitchSlider = null;
    private JLabel pitchMinLabel = null;
    private JLabel pitchCenterLabel = null;
    private JLabel pitchMaxLabel = null;
    private JSlider sampleScaleSlider = null;
    private JLabel scaleMinLabel = null;
    private JLabel scaleCenterLabel = null;
    private JLabel scaleMaxLabel = null;
    private JLabel presetOversamplingLabel = null;
    private JComboBox<Integer> presetOversampling = null;
    private JLabel presetFrameSizeLabel = null;
    private JComboBox<Integer> presetFrameSize = null;

    private static final Integer[] OVERSAMPLINGS = {
            1,
            5,
            10,
            15,
            20
    };
    private static final Integer[] FRAMESIZE = {
            512,
            1024,
            2048,
            4096,
            8192
    };

    /**
     * Constructor for PitchShiftGUI
     */
    public PitchShiftGUI(PitchShift pitchShift) {
        super();
        thePitcher = pitchShift;
        initialize();
    }

    private void initialize() {
        setName("PitchShift");
        setLayout(new java.awt.GridBagLayout());
        add(getPitchShiftActive(), Helpers.getGridBagConstraint(0, 0, 1, 1, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, 1.0, 0.0));
        add(getPresetOversamplingLabel(), Helpers.getGridBagConstraint(1, 0, 1, 1, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, 0.0, 0.0));
        add(getPresetOversampling(), Helpers.getGridBagConstraint(2, 0, 1, 1, java.awt.GridBagConstraints.HORIZONTAL, java.awt.GridBagConstraints.WEST, 1.0, 0.0));
        add(getPresetFramesizeLabel(), Helpers.getGridBagConstraint(3, 0, 1, 1, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, 0.0, 0.0));
        add(getPresetFramesize(), Helpers.getGridBagConstraint(4, 0, 1, 0, java.awt.GridBagConstraints.HORIZONTAL, java.awt.GridBagConstraints.WEST, 1.0, 0.0));
        add(getPitchShiftPanel(), Helpers.getGridBagConstraint(0, 1, 1, 0, java.awt.GridBagConstraints.BOTH, java.awt.GridBagConstraints.WEST, 1.0, 1.0));
        add(getScaleShiftPanel(), Helpers.getGridBagConstraint(0, 2, 1, 0, java.awt.GridBagConstraints.BOTH, java.awt.GridBagConstraints.WEST, 1.0, 1.0));
    }

    private JLabel getPresetOversamplingLabel() {
        if (presetOversamplingLabel == null) {
            presetOversamplingLabel = new JLabel("Oversampling:");
            presetOversamplingLabel.setFont(Helpers.getDialogFont());
        }
        return presetOversamplingLabel;
    }

    private JComboBox<Integer> getPresetOversampling() {
        if (presetOversampling == null) {
            presetOversampling = new JComboBox<>();
            presetOversampling.setName("presetOversampling");

            DefaultComboBoxModel<Integer> theModel = new DefaultComboBoxModel<>(OVERSAMPLINGS);
            presetOversampling.setModel(theModel);
            presetOversampling.setFont(Helpers.getDialogFont());
            presetOversampling.setEditable(true);
            if (thePitcher != null)
                presetOversampling.setSelectedItem(thePitcher.getFFTOversampling());
            presetOversampling.addItemListener(e -> {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    int oversampling = (Integer) getPresetOversampling().getSelectedItem();
                    if (thePitcher != null) thePitcher.setFFTOversampling(oversampling);
                }
            });
        }
        return presetOversampling;
    }

    private JLabel getPresetFramesizeLabel() {
        if (presetFrameSizeLabel == null) {
            presetFrameSizeLabel = new JLabel("Frame size:");
            presetFrameSizeLabel.setFont(Helpers.getDialogFont());
        }
        return presetFrameSizeLabel;
    }

    private JComboBox<Integer> getPresetFramesize() {
        if (presetFrameSize == null) {
            presetFrameSize = new JComboBox<>();
            presetFrameSize.setName("presetFrameSize");

            DefaultComboBoxModel<Integer> theModel = new DefaultComboBoxModel<>(FRAMESIZE);
            presetFrameSize.setModel(theModel);
            presetFrameSize.setFont(Helpers.getDialogFont());
            presetFrameSize.setEditable(true);
            if (thePitcher != null) presetFrameSize.setSelectedItem(thePitcher.getFftFrameSize());
            presetFrameSize.addItemListener(e -> {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    int framesize = (Integer) getPresetFramesize().getSelectedItem();
                    if (thePitcher != null) thePitcher.setFFTFrameSize(framesize);
                }
            });
        }
        return presetFrameSize;
    }

    private JPanel getPitchShiftPanel() {
        if (pitchShiftPanel == null) {
            pitchShiftPanel = new JPanel();
            pitchShiftPanel.setName("pitchShiftPanel");
            pitchShiftPanel.setLayout(new java.awt.GridBagLayout());
            pitchShiftPanel.setBorder(new TitledBorder(null, "Pitch", TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, Helpers.getDialogFont(), null));
            pitchShiftPanel.add(getPitchSlider(), Helpers.getGridBagConstraint(0, 0, 1, 3, java.awt.GridBagConstraints.HORIZONTAL, java.awt.GridBagConstraints.NORTH, 1.0, 0.0));
            pitchShiftPanel.add(getPitchMinLabel(), Helpers.getGridBagConstraint(0, 1, 1, 1, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, 1.0, 0.0));
            pitchShiftPanel.add(getPitchCenterLabel(), Helpers.getGridBagConstraint(1, 1, 1, 1, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.CENTER, 1.0, 0.0));
            pitchShiftPanel.add(getPitchMaxLabel(), Helpers.getGridBagConstraint(2, 1, 1, 1, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.EAST, 1.0, 0.0));
        }
        return pitchShiftPanel;
    }

    private JPanel getScaleShiftPanel() {
        if (scaleShiftPanel == null) {
            scaleShiftPanel = new JPanel();
            scaleShiftPanel.setName("scaleShiftPanel");
            scaleShiftPanel.setLayout(new java.awt.GridBagLayout());
            scaleShiftPanel.setBorder(new TitledBorder(null, "Tempo", TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, Helpers.getDialogFont(), null));
            scaleShiftPanel.add(getScaleSlider(), Helpers.getGridBagConstraint(0, 0, 1, 3, java.awt.GridBagConstraints.HORIZONTAL, java.awt.GridBagConstraints.NORTH, 1.0, 0.0));
            scaleShiftPanel.add(getScaleMinLabel(), Helpers.getGridBagConstraint(0, 1, 1, 1, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, 1.0, 0.0));
            scaleShiftPanel.add(getScaleCenterLabel(), Helpers.getGridBagConstraint(1, 1, 1, 1, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.CENTER, 1.0, 0.0));
            scaleShiftPanel.add(getScaleMaxLabel(), Helpers.getGridBagConstraint(2, 1, 1, 1, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.EAST, 1.0, 0.0));
        }
        return scaleShiftPanel;
    }

    private JCheckBox getPitchShiftActive() {
        if (pitchShiftActive == null) {
            pitchShiftActive = new javax.swing.JCheckBox();
            pitchShiftActive.setName("pitchShiftActive");
            pitchShiftActive.setText("activate pitch shift");
            pitchShiftActive.setFont(Helpers.getDialogFont());
            if (thePitcher != null) pitchShiftActive.setSelected(thePitcher.isActive());
            pitchShiftActive.addItemListener(e -> {
                if (e.getStateChange() == ItemEvent.SELECTED || e.getStateChange() == ItemEvent.DESELECTED) {
                    if (thePitcher != null) thePitcher.setIsActive(getPitchShiftActive().isSelected());
                }
            });
        }
        return pitchShiftActive;
    }

    private JSlider getPitchSlider() {
        if (pitchSlider == null) {
            int value = (int) (Math.log10(thePitcher.getPitchScale()) / factor * (double) SHIFT);
            if (value > SLIDER_MAX) value = SLIDER_MAX;
            else if (value < SLIDER_MIN) value = SLIDER_MIN;
            pitchSlider = new JSlider(JSlider.HORIZONTAL, SLIDER_MIN, SLIDER_MAX, value);
            pitchSlider.setFont(Helpers.getDialogFont());
            pitchSlider.setMinorTickSpacing((int) (0.1f * SHIFT));
            pitchSlider.setMajorTickSpacing((int) (0.5f * SHIFT));
            pitchSlider.setPaintTicks(true);
            pitchSlider.setSnapToTicks(false);
            pitchSlider.setPaintLabels(false);
            pitchSlider.setPaintTrack(true);
            pitchSlider.setToolTipText(Float.toString(Math.round(value * 10f) / (SHIFT * 10f)));
            pitchSlider.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() > 1) {
                        getPitchSlider().setValue(0);
                        e.consume();
                    }
                }
            });
            pitchSlider.addChangeListener(e -> {
                double value1 = (double) getPitchSlider().getValue() / (double) SHIFT;
                thePitcher.setPitchScale((float) Math.pow(10d, value1 * factor));
                pitchSlider.setToolTipText(Float.toString(Math.round(value1 * 10f) / 10f));
            });
        }
        return pitchSlider;
    }

    public javax.swing.JLabel getPitchMinLabel() {
        if (pitchMinLabel == null) {
            pitchMinLabel = new JLabel("one octave down");
            pitchMinLabel.setFont(Helpers.getDialogFont());
        }
        return pitchMinLabel;
    }

    public javax.swing.JLabel getPitchCenterLabel() {
        if (pitchCenterLabel == null) {
            pitchCenterLabel = new JLabel("center");
            pitchCenterLabel.setFont(Helpers.getDialogFont());
        }
        return pitchCenterLabel;
    }

    public javax.swing.JLabel getPitchMaxLabel() {
        if (pitchMaxLabel == null) {
            pitchMaxLabel = new JLabel("one octave up");
            pitchMaxLabel.setFont(Helpers.getDialogFont());
        }
        return pitchMaxLabel;
    }

    private JSlider getScaleSlider() {
        if (sampleScaleSlider == null) {
            int value = (int) (Math.log10(thePitcher.getSampleScale()) / factor * (double) SHIFT);
            if (value > SLIDER_MAX) value = SLIDER_MAX;
            else if (value < SLIDER_MIN) value = SLIDER_MIN;
            sampleScaleSlider = new JSlider(JSlider.HORIZONTAL, SLIDER_MIN, SLIDER_MAX, value);
            sampleScaleSlider.setFont(Helpers.getDialogFont());
            sampleScaleSlider.setMinorTickSpacing((int) (0.1f * SHIFT));
            sampleScaleSlider.setMajorTickSpacing((int) (0.5f * SHIFT));
            sampleScaleSlider.setPaintTicks(true);
            sampleScaleSlider.setSnapToTicks(false);
            sampleScaleSlider.setPaintLabels(false);
            sampleScaleSlider.setPaintTrack(true);
            sampleScaleSlider.setToolTipText(Float.toString(Math.round(value * 10f) / (SHIFT * 10f)));
            sampleScaleSlider.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() > 1) {
                        getScaleSlider().setValue(0);
                        e.consume();
                    }
                }
            });
            sampleScaleSlider.addChangeListener(e -> {
                double value1 = (double) getScaleSlider().getValue() / (double) SHIFT;
                thePitcher.setPitchAndSampleScale((float) Math.pow(10d, -value1 * factor), (float) Math.pow(10d, value1 * factor));
                sampleScaleSlider.setToolTipText(Float.toString(Math.round(value1 * 10f) / 10f));
                getPitchSlider().setValue((int) ((-value1) * (double) SHIFT));
            });
        }
        return sampleScaleSlider;
    }

    public javax.swing.JLabel getScaleMinLabel() {
        if (scaleMinLabel == null) {
            scaleMinLabel = new JLabel("half speed");
            scaleMinLabel.setFont(Helpers.getDialogFont());
        }
        return scaleMinLabel;
    }

    public javax.swing.JLabel getScaleCenterLabel() {
        if (scaleCenterLabel == null) {
            scaleCenterLabel = new JLabel("normal");
            scaleCenterLabel.setFont(Helpers.getDialogFont());
        }
        return scaleCenterLabel;
    }

    public javax.swing.JLabel getScaleMaxLabel() {
        if (scaleMaxLabel == null) {
            scaleMaxLabel = new JLabel("double speed");
            scaleMaxLabel.setFont(Helpers.getDialogFont());
        }
        return scaleMaxLabel;
    }
}
