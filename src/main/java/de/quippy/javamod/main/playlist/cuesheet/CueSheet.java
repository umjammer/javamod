/*
 * @(#) CueSheet.java
 *
 * Created on 14.02.2012 by Daniel Becker
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

package de.quippy.javamod.main.playlist.cuesheet;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

import de.quippy.javamod.system.Helpers;

import static java.lang.System.getLogger;


/**
 * @author Daniel Becker
 * This class will read a cue sheet and represent it
 */
public class CueSheet {

    private static final Logger logger = getLogger(CueSheet.class.getName());

    private static final String QUOTATION_MARK = "\"";

    private URL cueSheedFileName;

    private String title;
    private String performer;
    private String songwriter;
    private final List<CueFile> cueFiles;

    public CueSheet() {
        super();
        cueFiles = new ArrayList<>();
    }

    /**
     * @param file
     * @since 14.02.2012
     */
    public CueSheet(URL file) {
        this();
        cueSheedFileName = file;
        readCueSheet(file);
    }

    public CueSheet(File file) {
        this(Helpers.createURLfromFile(file.toPath()));
    }

    /**
     * @param file
     * @since 14.02.2012
     */
    public CueSheet(String file) {
        this(Helpers.createURLfromString(file));
    }

    /**
     * @return the cueSheedFileName
     * @since 14.02.2012
     */
    public URL getCueSheedFileName() {
        return cueSheedFileName;
    }

    public void setCueSheedFileName(URL fileName) {
        cueSheedFileName = fileName;
    }

    /**
     * @return the title
     * @since 14.02.2012
     */
    public String getTitle() {
        return title;
    }

    /**
     * @param title the title to set
     * @since 14.02.2012
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * @return the performer
     * @since 14.02.2012
     */
    public String getPerformer() {
        return performer;
    }

    /**
     * @param performer the performer to set
     * @since 14.02.2012
     */
    public void setPerformer(String performer) {
        this.performer = performer;
    }

    /**
     * @return the songwriter
     * @since 14.02.2012
     */
    public String getSongwriter() {
        return songwriter;
    }

    /**
     * @param songwriter the songwriter to set
     * @since 14.02.2012
     */
    public void setSongwriter(String songwriter) {
        this.songwriter = songwriter;
    }

    /**
     * @return the cueFiles
     * @since 14.02.2012
     */
    public List<CueFile> getCueFiles() {
        return cueFiles;
    }

    public void addFile(CueFile cueFile) {
        cueFiles.add(cueFile);
    }

    private static void writeCommentBlock(Writer writer, String title, String performer, String songWriter, int column) throws IOException {
        StringBuilder columnString = new StringBuilder();
        columnString.append("  ".repeat(Math.max(0, column)));
        if (performer != null && !performer.isEmpty())
            writer.write(columnString + "PERFORMER \"" + performer + "\"\r\n");
        if (title != null && !title.isEmpty()) writer.write(columnString + "TITLE \"" + title + "\"\r\n");
        if (songWriter != null && !songWriter.isEmpty())
            writer.write(columnString + "SONGWRITER \"" + songWriter + "\"\r\n");
    }

    public void writeCueSheet(File toFile) {
        String prefix = toFile.getAbsolutePath();
        prefix = prefix.substring(0, prefix.lastIndexOf(File.separatorChar) + 1).toLowerCase();
        int prefixLen = prefix.length();

        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(toFile)));
            bw.write("REM COMMENT \"" + Helpers.FULLVERSION + "\"\r\n");
            writeCommentBlock(bw, getTitle(), getPerformer(), getSongwriter(), 0);
            List<CueFile> cueFiles = getCueFiles();
            int filesSize = cueFiles.size();
            for (CueFile cueFile : cueFiles) {
                String fileString = Helpers.createLocalFileStringFromURL(cueFile.getFile(), true);
                if (fileString.toLowerCase().startsWith(prefix)) fileString = fileString.substring(prefixLen);

                bw.write("FILE \"" + fileString + "\" " + cueFile.getType() + "\r\n");

                List<CueTrack> cueTracks = cueFile.getTracks();
                int tracksSize = cueTracks.size();
                for (CueTrack cueTrack : cueTracks) {
                    int trackNo = cueTrack.getTrackNo();
                    String track = (trackNo < 10) ? "0" + trackNo : String.valueOf(trackNo);
                    bw.write("  TRACK " + track + " " + cueTrack.getFormat() + "\r\n");
                    writeCommentBlock(bw, cueTrack.getTitle(), cueTrack.getPerformer(), cueTrack.getSongwriter(), 2);

                    List<CueIndex> indexes = cueTrack.getIndexes();
                    int indexSize = indexes.size();
                    for (CueIndex cueIndex : indexes) {
                        int indexNo = cueIndex.getIndexNo();
                        String index = (indexNo < 10) ? "0" + indexNo : String.valueOf(indexNo);
                        long milliIndex = cueIndex.getMillisecondIndex();
                        Integer min = (int) (milliIndex / 60000L);
                        Integer sec = (int) ((milliIndex / 1000L) % 60L);
                        milliIndex -= (min.longValue() * 60L + sec.longValue()) * 1000;
                        Integer frame = (int) (((milliIndex * 75) + 500L) / 1000L);
                        String timeIndex = "%02d:%02d:%02d".formatted(min, sec, frame);
                        bw.write("    INDEX " + index + " " + timeIndex + "\r\n");
                    }
                }
            }
        } catch (Throwable ex) {
            logger.log(Level.ERROR, "[CueSheet]: Writing to \"" + toFile.getAbsolutePath() + "\" failed", ex);
        } finally {
            if (bw != null) try {
                bw.close();
            } catch (IOException ex) { /* logger.log(Level.ERROR, "IGNORED", ex); */ }
        }
    }

    public static CueSheet createCueSheet(String file) {
        return new CueSheet(file);
    }

    public static CueSheet createCueSheet(File file) {
        return new CueSheet(file);
    }

    public static CueSheet createCueSheet(URL file) {
        return new CueSheet(file);
    }

    private static String getStringFromQuotation(Scanner tok) {
        String nextToken = tok.next();

        if (nextToken.startsWith(QUOTATION_MARK)) {
            if (nextToken.endsWith(QUOTATION_MARK)) {
                nextToken = nextToken.substring(1, nextToken.length() - 1);
            } else {
                nextToken = nextToken.substring(1) + tok.useDelimiter(QUOTATION_MARK).next();
            }
        }
        return nextToken;
    }

    private void readCueSheet(URL fromFile) {
        String line;
        BufferedReader br = null;
        Scanner tok = null;

        CueFile cueFile = null;
        CueTrack cueTrack = null;
        try {
            br = new BufferedReader(new InputStreamReader(fromFile.openStream(), Helpers.CODING_M3U));
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) continue;
                tok = new Scanner(line);
                if (!tok.hasNext()) continue;
                String token = tok.next();

                // Skipping CATALOG, CDTEXTFILE, FLAGS, ISRC, POSTGAP, PREGAP, REM
                if (token.equalsIgnoreCase("FILE")) {
                    String f = CueSheet.getStringFromQuotation(tok);
                    addFile(cueFile = new CueFile());
                    cueFile.setFile(Helpers.createAbsolutePathForFile(fromFile, f));
                    cueFile.setType(tok.next());
                    cueTrack = null;
                } else if (token.equalsIgnoreCase("TRACK")) {
                    if (cueFile == null) {
                        tok.close();
                        throw new RuntimeException("Illegal cue sheet: track without file");
                    }
                    cueFile.addTrack(cueTrack = new CueTrack());
                    cueTrack.setTrackNo(tok.nextInt());
                    cueTrack.setFormat(tok.nextLine().trim().toUpperCase());
                } else if (token.equalsIgnoreCase("TITLE")) {
                    String title = CueSheet.getStringFromQuotation(tok);
                    if (cueTrack != null && cueTrack.getTitle() == null)
                        cueTrack.setTitle(title);
                    else
                        setTitle(title);
                } else if (token.equalsIgnoreCase("PERFORMER")) {
                    String performer = CueSheet.getStringFromQuotation(tok);
                    if (cueTrack != null && cueTrack.getPerformer() == null)
                        cueTrack.setPerformer(performer);
                    else
                        setPerformer(performer);
                } else if (token.equalsIgnoreCase("SONGWRITER")) {
                    String songWriter = CueSheet.getStringFromQuotation(tok);
                    if (cueTrack != null && cueTrack.getSongwriter() == null)
                        cueTrack.setSongwriter(songWriter);
                    else
                        setSongwriter(songWriter);
                } else if (token.equalsIgnoreCase("INDEX")) {
                    if (cueTrack == null) {
                        tok.close();
                        throw new RuntimeException("Illegal cue sheet: index without track");
                    }
                    int indexNo = tok.nextInt();
                    Scanner sc = tok.useDelimiter(Pattern.compile(":| "));
                    int minutes = sc.nextInt(), seconds = sc.nextInt(), frames = sc.nextInt();
                    CueIndex index = new CueIndex();
                    index.setIndexNo(indexNo);
                    index.setMillisecondIndex(((minutes * 60L + seconds) * 1000L) + (frames * 1000L / 75L));
                    cueTrack.addIndex(index);
                }
            }
        } catch (Throwable ex) {
            logger.log(Level.ERROR, "[CueSheet]: Loading failed", ex);
        } finally {
            if (br != null) try {
                br.close();
            } catch (IOException ex) { /* logger.log(Level.ERROR, "IGNORED", ex); */ }
            if (tok != null) tok.close();
        }
    }
}
