/*
 * @(#) JavaMod.java
 *
 * Created on 22.06.2006 by Daniel Becker
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

package de.quippy.javamod.main;

import java.awt.EventQueue;
import java.io.File;

import de.quippy.javamod.main.gui.MainForm;
import de.quippy.javamod.system.Helpers;


/**
 * @author Daniel Becker
 * @since 22.06.2006
 */
public class JavaMod extends JavaModMainBase {

    /**
     * Constructor for JavaMod
     */
    public JavaMod() {
        super(true);
    }

    /**
     * parses through the parameter - params (starting with '-') are not
     * allowed here - we filter them out
     *
     * @param args
     * @return the given filename
     * @since 31.12.2010
     */
    private static String getFileName(String[] args) {
        String fileName = null;
        for (String arg : args) {
            if (!arg.startsWith("-")) {
                fileName = arg;
                break;
            }
        }
        return fileName;
    }

    /**
     * @param args
     * @since 22.06.2006
     */
    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            MainForm mainForm = new MainForm();
            Helpers.setCoding(true);
            mainForm.setVisible(true);
            if (args.length > 0) {
                String fileName = getFileName(args);
                if (fileName != null) {
                    File f = new File(fileName);
                    if (f.exists())
                        mainForm.doOpenFile(new File[] {f});
                    else
                        mainForm.doOpenURL(fileName);
                    mainForm.doStartPlaying();
                }
            }
        });
    }
}
