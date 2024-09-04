/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package de.quippy.javamod.multimedia.vgm;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.swing.JPanel;

import de.quippy.javamod.mixer.Mixer;
import de.quippy.javamod.multimedia.MultimediaContainer;


/**
 * VGMContainer.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2024-09-03 nsano initial version <br>
 */
public class VGMContainer extends MultimediaContainer {

    String name;

    @Override
    public void setFileURL(URL url) {
        super.setFileURL(url);
        try {
            name = Path.of(url.toURI()).toFile().toString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(url.toString());
        }
    }

    @Override
    public Map<String, Object> getSongInfosFor(URL url) {
        Map<String, Object> result = new HashMap<>();
        return result;
    }

    @Override
    public boolean canExport() {
        return false;
    }

    @Override
    public JPanel getInfoPanel() {
        return null;
    }

    @Override
    public JPanel getConfigPanel() {
        return null;
    }

    @Override
    public String[] getFileExtensionList() {
        return new String[] { "vgm", "vgz" };
    }

    @Override
    public String getName() {
        return "VGM-File";
    }

    @Override
    public void configurationChanged(Properties newProps) {

    }

    @Override
    public void configurationSave(Properties props) {

    }

    @Override
    public void cleanUp() {

    }

    @Override
    public Mixer createNewMixer() {
        return new VGMMixer(name);
    }
}
