/*
 * @(#) RenoiseTrackerMod.java
 *
 * Created on 11.07.2026 by Daniel Becker
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

package de.quippy.javamod.multimedia.mod.loader.tracker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;

import de.quippy.javamod.io.ModfileInputStream;
import de.quippy.javamod.io.RandomAccessInputStream;
import de.quippy.javamod.multimedia.mod.ModConstants;
import de.quippy.javamod.multimedia.mod.loader.Module;
import de.quippy.javamod.multimedia.mod.loader.instrument.Envelope;
import de.quippy.javamod.multimedia.mod.loader.instrument.Envelope.EnvelopeType;
import de.quippy.javamod.multimedia.mod.loader.instrument.Instrument;
import de.quippy.javamod.multimedia.mod.loader.instrument.InstrumentsContainer;
import de.quippy.javamod.multimedia.mod.loader.instrument.Sample;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternContainer;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternElement;
import de.quippy.javamod.multimedia.mod.midi.MidiMacros;
import de.quippy.javamod.multimedia.mod.mixer.BasicModMixer;
import de.quippy.javamod.multimedia.mod.mixer.ProTrackerMixer;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import org.kc7bfi.jflac.FLACDecoder;
import org.kc7bfi.jflac.FrameDecodeException;
import org.kc7bfi.jflac.frame.Frame;
import org.kc7bfi.jflac.util.ByteData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import static java.lang.System.getLogger;


/**
 * Renoise XRNS Song Loader.
 * <p>
 * An XRNS file is a zip archive containing "Song.xml" plus the instrument
 * samples as flac/ogg/wav files. As Renoise is way more capable than any
 * classic tracker, the song is converted to a FastTracker II (XM) alike
 * module, following the conversion strategy of the Xrns2XMod project
 * (https://github.com/fstarred/xrns2xmod).
 * <p>
 * The old binary ".rns" format of Renoise &lt;1.8 is proprietary and not
 * supported.
 *
 * @author Daniel Becker
 * @since 11.07.2026
 */
public class RenoiseTrackerMod extends Module {

    private static final Logger logger = getLogger(RenoiseTrackerMod.class.getName());

    private static final String[] MODFILEEXTENSION = { "xrns" };

    private static final String SONG_XML = "Song.xml";
    /** with Renoise 2.8 (doc_version 37) the numeric effect commands became letters */
    private static final int DOC_VERSION_LETTER_FX = 37;
    /** old numeric effects 00..0F in the order of their new letter representation */
    private static final char[] OLD_EFFECT_LETTERS = {
            'A', 'U', 'D', 'M', 'C', 'G', 'I', 'O', 'P', 'S', 'W', 'B', 'L', 'Q', 'R', 'V'
    };
    private static final String[] NOTE_NAMES = { "C-", "C#", "D-", "D#", "E-", "F-", "F#", "G-", "G#", "A-", "A#", "B-" };
    /** SampleEnvelopeModulationDevice (Renoise 2.8) stores envelope positions in a higher resolution */
    private static final double ENVELOPE_POSITION_SCALE = 10.665d;
    private static final float NTSC_C4_FREQUENCY = 8363.42289719626f;
    private static final int RENOISE_MAX_NOTES = 120;
    private static final int MOD_VERSION_COMPATIBLE = 1;

    private String songMessage;
    private MidiMacros midiMacros;

    private int docVersion;
    private int ticksPerRow;
    private int playbackEngineVersion;
    private boolean pitchCompatibilityMode;
    private boolean sampleOffsetCompatibilityMode;
    /** per instrument: renoise note (0..119) to local sample index */
    private int[][] keyMaps;
    /** per instrument: local sample index to frame count - for the sample offset effect */
    private int[][] sampleFrames;

    @Override
    public String[] getFileExtensionList() {
        return MODFILEEXTENSION;
    }

    @Override
    public BasicModMixer getModMixer(int sampleRate, int doISP, int doAmigaEmulation, int doNoLoops, int maxNNAChannels) {
        return new ProTrackerMixer(this, sampleRate, doISP, doAmigaEmulation, doNoLoops, maxNNAChannels);
    }

    @Override
    public int getPanningSeparation() {
        return 128;
    }

    @Override
    public int getPanningValue(int channel) {
        return ModConstants.PANNING_CENTER;
    }

    @Override
    public int getChannelVolume(int channel) {
        return 64;
    }

    @Override
    public int getFrequencyTable() {
        return ModConstants.XM_LINEAR_TABLE;
    }

    @Override
    public String getSongMessage() {
        return songMessage;
    }

    @Override
    public MidiMacros getMidiConfig() {
        return midiMacros;
    }

    @Override
    public boolean getFT2Tremolo() {
        return true;
    }

    @Override
    public boolean getModSpeedIsTicks() {
        return false;
    }

    @Override
    public boolean supportsAmigaFilter() {
        return false;
    }

    @Override
    public boolean checkLoadingPossible(ModfileInputStream inputStream) throws IOException {
        boolean result = false;
        if (inputStream.readIntelDWord() == 0x04034b50) { // "PK\3\4"
            inputStream.seek(0);
            byte[] all = new byte[(int) inputStream.getLength()];
            readFully(inputStream, all);
            result = zipContainsSongXML(all);
        }
        inputStream.seek(0);
        return result;
    }

    /**
     * With only the first 8K of the stream available we check the zip magic
     * and the name of the first local zip entry - Renoise archives start
     * with "Song.xml" or the "SampleData" folder.
     *
     * @since 11.07.2026
     */
    @Override
    public boolean checkLoadingPossible(InputStream inputStream) throws IOException {
        byte[] header = new byte[64];
        int read = inputStream.read(header);
        if (read < 34 ||
                header[0] != 'P' || header[1] != 'K' || header[2] != 3 || header[3] != 4)
            return false;
        int nameLength = (header[26] & 0xFF) | ((header[27] & 0xFF) << 8);
        if (nameLength <= 0 || 30 + nameLength > read) return false;
        String name = new String(header, 30, nameLength, java.nio.charset.StandardCharsets.ISO_8859_1);
        return name.equals(SONG_XML) || name.startsWith("SampleData/") || name.equals("Icon.png") || name.equals("CoverArt.png");
    }

    private static boolean zipContainsSongXML(byte[] zipData) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            int checked = 0;
            while ((entry = zip.getNextEntry()) != null && checked++ < 512) {
                if (entry.getName().equals(SONG_XML)) return true;
            }
        } catch (IOException ex) {
            /* NOOP */
        }
        return false;
    }

    private static void readFully(RandomAccessInputStream inputStream, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int read = inputStream.read(buffer, offset, buffer.length - offset);
            if (read < 0) throw new IOException("Unexpected end of stream");
            offset += read;
        }
    }

    @Override
    protected void loadModFileInternal(RandomAccessInputStream inputStream) throws IOException {
        byte[] all = new byte[(int) inputStream.getLength()];
        readFully(inputStream, all);

        // unpack the archive: Song.xml plus all sample files
        byte[] songXML = null;
        Map<String, byte[]> sampleFiles = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(all))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.equals(SONG_XML))
                    songXML = zip.readAllBytes();
                else if (name.startsWith("SampleData/"))
                    sampleFiles.put(name, zip.readAllBytes());
            }
        }
        if (songXML == null) throw new IOException("Unsupported XRNS: no " + SONG_XML + " found");

        Document document;
        try {
            document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(songXML));
        } catch (Exception ex) {
            throw new IOException("Unsupported XRNS: broken " + SONG_XML, ex);
        }

        Element song = document.getDocumentElement();
        if (!"RenoiseSong".equals(song.getTagName())) throw new IOException("Unsupported XRNS: not a RenoiseSong");
        try {
            docVersion = Integer.parseInt(song.getAttribute("doc_version"));
        } catch (NumberFormatException ex) {
            docVersion = 0;
        }

        setModType(ModConstants.MODTYPE_XM); // Renoise songs are converted to XM internally
        songFlags = ModConstants.SONG_LINEARSLIDES | ModConstants.SONG_ISSTEREO;
        setBaseVolume(ModConstants.MAXGLOBALVOLUME);
        setMixingPreAmp(ModConstants.MIN_MIXING_PREAMP);
        setModID("XRNS");
        setTrackerName("Renoise (doc_version " + docVersion + ')');
        midiMacros = new MidiMacros();

        Element globalSongData = child(song, "GlobalSongData");
        setSongName(getText(globalSongData, "SongName", ""));
        int bpm = getInt(globalSongData, "BeatsPerMin", 125);
        int linesPerBeat = getInt(globalSongData, "LinesPerBeat", 4);
        ticksPerRow = getInt(globalSongData, "TicksPerLine", 6);
        if (ticksPerRow < 1) ticksPerRow = 1;
        else if (ticksPerRow > 0x1F) ticksPerRow = 0x1F;
        playbackEngineVersion = getInt(globalSongData, "PlaybackEngineVersion", 0);
        sampleOffsetCompatibilityMode = getBoolean(globalSongData, "SampleOffsetCompatibilityMode", false);
        pitchCompatibilityMode = getBoolean(globalSongData, "PitchEffectsCompatibilityMode", false);
        songMessage = readSongComments(globalSongData);

        setTempo(ticksPerRow);
        // XM base: 4 lines per beat at 6 ticks per line
        int xmBPM = bpm * linesPerBeat / 4 * ticksPerRow / 6;
        setBPMSpeed(Math.max(32, xmBPM));

        // the note tracks - every visible note column becomes one channel
        Element tracks = child(song, "Tracks");
        List<Element> sequencerTracks = children(tracks, "SequencerTrack");
        int[] noteColumnsOfTrack = new int[sequencerTracks.size()];
        int numChannels = 0;
        for (int i = 0; i < sequencerTracks.size(); i++) {
            noteColumnsOfTrack[i] = Math.max(1, getInt(sequencerTracks.get(i), "NumberOfVisibleNoteColumns", 1));
            numChannels += noteColumnsOfTrack[i];
        }
        if (numChannels == 0) throw new IOException("Unsupported XRNS: no note tracks");
        setNChannels(numChannels);

        readArrangement(song);
        readInstruments(song, sampleFiles);
        readPatterns(song, noteColumnsOfTrack);

        removeEndOfArrangement();
    }

    /* ------------------------------- XML helpers ------------------------------- */

    private static Element child(Element parent, String name) {
        if (parent == null) return null;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && element.getTagName().equals(name)) return element;
        }
        return null;
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        if (parent == null) return result;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && element.getTagName().equals(name)) result.add(element);
        }
        return result;
    }

    private static String getText(Element parent, String name, String defaultValue) {
        Element element = child(parent, name);
        if (element == null) return defaultValue;
        String text = element.getTextContent();
        return (text == null) ? defaultValue : text;
    }

    private static int getInt(Element parent, String name, int defaultValue) {
        try {
            return (int) Double.parseDouble(getText(parent, name, Integer.toString(defaultValue)).trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private static float getFloat(Element parent, String name, float defaultValue) {
        try {
            return Float.parseFloat(getText(parent, name, Float.toString(defaultValue)).trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private static boolean getBoolean(Element parent, String name, boolean defaultValue) {
        return Boolean.parseBoolean(getText(parent, name, Boolean.toString(defaultValue)).trim());
    }

    private static String readSongComments(Element globalSongData) {
        Element comments = child(globalSongData, "SongComments");
        if (comments == null) return null;
        StringBuilder message = new StringBuilder();
        for (Element line : children(comments, "Comment")) {
            if (!message.isEmpty()) message.append('\n');
            message.append(line.getTextContent());
        }
        return message.isEmpty() ? null : message.toString();
    }

    /* ----------------------------- song arrangement ---------------------------- */

    private void readArrangement(Element song) {
        Element patternSequence = child(song, "PatternSequence");
        List<Integer> order = new ArrayList<>();
        Element sequenceEntries = child(patternSequence, "SequenceEntries");
        if (sequenceEntries != null) {
            for (Element sequenceEntry : children(sequenceEntries, "SequenceEntry"))
                order.add(getInt(sequenceEntry, "Pattern", 0));
        } else { // very old format: a nested "PatternSequence" element holds the pattern list
            Element oldSequence = child(patternSequence, "PatternSequence");
            if (oldSequence != null) {
                for (Element pattern : children(oldSequence, "Pattern")) {
                    try {
                        order.add(Integer.parseInt(pattern.getTextContent().trim()));
                    } catch (NumberFormatException ex) {
                        order.add(0);
                    }
                }
            }
        }
        if (order.isEmpty()) order.add(0);

        allocArrangement(256);
        int songLength = Math.min(order.size(), 256);
        for (int i = 0; i < songLength; i++) getArrangement()[i] = order.get(i);
        setSongLength(songLength);

        int restartPosition = getInt(child(patternSequence, "LoopSelection"), "CursorPos", 0);
        setSongRestart((restartPosition < 0 || restartPosition >= songLength) ? 0 : restartPosition);
    }

    /* ---------------------------- instruments/samples -------------------------- */

    private static Element getSamplesElement(Element instrument) {
        Element sampleGenerator = child(instrument, "SampleGenerator");
        if (sampleGenerator != null) return child(sampleGenerator, "Samples"); // Renoise 2.8+
        return child(instrument, "Samples"); // up to Renoise 2.7
    }

    private void readInstruments(Element song, Map<String, byte[]> sampleFiles) {
        List<Element> instruments = children(child(song, "Instruments"), "Instrument");
        int numInstruments = instruments.size();
        setNInstruments(numInstruments);

        int totalSamples = 0;
        for (Element instrument : instruments)
            totalSamples += children(getSamplesElement(instrument), "Sample").size();
        setNSamples(totalSamples);

        InstrumentsContainer instrumentContainer = new InstrumentsContainer(this, numInstruments, totalSamples);
        setInstrumentContainer(instrumentContainer);

        keyMaps = new int[numInstruments][];
        sampleFrames = new int[numInstruments][];

        int globalSampleIndex = 0;
        for (int ins = 0; ins < numInstruments; ins++) {
            Element instrumentElement = instruments.get(ins);
            List<Element> samples = children(getSamplesElement(instrumentElement), "Sample");

            Instrument currentIns = new Instrument();
            currentIns.name = getText(instrumentElement, "Name", "");
            currentIns.globalVolume = 128;
            currentIns.setPanning = false;
            currentIns.defaultPanning = 128;
            currentIns.pitchPanSeparation = -1;
            currentIns.NNA = -1;
            currentIns.initialFilterCutoff = 0;
            currentIns.initialFilterResonance = 0;
            currentIns.randomPanningVariation = -1;

            int[] keyMap = readKeyMap(instrumentElement, samples);
            keyMaps[ins] = keyMap;
            currentIns.sampleIndex = new int[RENOISE_MAX_NOTES];
            currentIns.noteIndex = new int[RENOISE_MAX_NOTES];
            for (int i = 0; i < RENOISE_MAX_NOTES; i++) {
                currentIns.noteIndex[i] = i;
                currentIns.sampleIndex[i] = (keyMap[i] < samples.size()) ? globalSampleIndex + keyMap[i] + 1 : 0;
            }

            readInstrumentEnvelopes(instrumentElement, currentIns);

            sampleFrames[ins] = new int[samples.size()];
            for (int smp = 0; smp < samples.size(); smp++) {
                Sample sample = readSample(samples.get(smp), ins, smp, sampleFiles);
                sampleFrames[ins][smp] = sample.sampleLength;
                instrumentContainer.setSample(globalSampleIndex + smp, sample);
            }
            globalSampleIndex += samples.size();

            instrumentContainer.setInstrument(ins, currentIns);
        }
    }

    private static int[] readKeyMap(Element instrumentElement, List<Element> samples) {
        int[] keyMap = new int[RENOISE_MAX_NOTES];
        Element splitMap = child(instrumentElement, "SplitMap");
        if (splitMap != null) { // up to Renoise 2.7
            List<Element> splits = children(splitMap, "Split");
            for (int i = 0; i < splits.size() && i < RENOISE_MAX_NOTES; i++) {
                try {
                    keyMap[i] = Integer.parseInt(splits.get(i).getTextContent().trim());
                } catch (NumberFormatException ex) {
                    /* NOOP */
                }
            }
        } else { // Renoise 2.8+: every sample has its own note mapping
            for (int smp = 0; smp < samples.size(); smp++) {
                Element mapping = child(samples.get(smp), "Mapping");
                if (mapping == null) continue;
                int noteStart = getInt(mapping, "NoteStart", 0);
                int noteEnd = getInt(mapping, "NoteEnd", RENOISE_MAX_NOTES - 1);
                for (int i = Math.max(0, noteStart); i <= noteEnd && i < RENOISE_MAX_NOTES; i++)
                    keyMap[i] = smp;
            }
        }
        return keyMap;
    }

    private Sample readSample(Element sampleElement, int instrumentIndex, int sampleIndex, Map<String, byte[]> sampleFiles) {
        Sample current = new Sample();
        current.name = getText(sampleElement, "Name", "").trim();
        current.globalVolume = ModConstants.MAXSAMPLEVOLUME;
        current.volume = 64;
        current.setPanning = true;
        float panning = getFloat(sampleElement, "Panning", 0.5f);
        current.defaultPanning = Math.min(255, Math.max(0, Math.round(panning * 255f)));

        int baseNote;
        Element mapping = child(sampleElement, "Mapping");
        if (mapping != null) baseNote = getInt(mapping, "BaseNote", 48); // Renoise 2.8+
        else baseNote = getInt(sampleElement, "BaseNote", 48); // up to Renoise 2.7
        int transpose = getInt(sampleElement, "Transpose", 0);
        int fineTune = getInt(sampleElement, "Finetune", 0);
        float volumeFactor = getFloat(sampleElement, "Volume", 1.0f);

        DecodedSample decoded = decodeSampleFile(instrumentIndex, sampleIndex, sampleFiles);
        int sampleRate = ModConstants.BASEFREQUENCY;
        if (decoded != null) {
            sampleRate = decoded.sampleRate;
            current.sampleLength = decoded.frames;
            current.byteLength = decoded.frames << 1;
            current.isStereo = decoded.channels > 1;
            current.sampleType = ModConstants.SM_16BIT | ((current.isStereo) ? ModConstants.SM_STEREO : 0);
            current.allocSampleData();
            System.arraycopy(decoded.left, 0, current.sampleL, 0, decoded.frames);
            if (current.isStereo) System.arraycopy(decoded.right, 0, current.sampleR, 0, decoded.frames);
            if (volumeFactor != 1.0f) scaleSampleData(current, volumeFactor);
        }

        setSampleProperties(current, baseNote, transpose, fineTune, sampleRate);

        String loopMode = getText(sampleElement, "LoopMode", "Off");
        int loopStart = getInt(sampleElement, "LoopStart", 0);
        int loopEnd = getInt(sampleElement, "LoopEnd", 0);
        if (!loopMode.equalsIgnoreCase("Off") && loopEnd > loopStart) {
            current.loopType = ModConstants.LOOP_ON;
            if (loopMode.equalsIgnoreCase("PingPong")) current.loopType |= ModConstants.LOOP_IS_PINGPONG;
            current.loopStart = loopStart;
            current.loopStop = loopEnd;
            current.loopLength = loopEnd - loopStart;
        }

        // Defaults for non-existent SustainLoop
        current.sustainLoopStart = 0;
        current.sustainLoopStop = 0;
        current.sustainLoopLength = 0;

        current.fixSampleLoops(getModType());
        return current;
    }

    private static void scaleSampleData(Sample sample, float factor) {
        for (int i = 0; i < sample.sampleLength; i++) {
            sample.sampleL[i] = clampSample((long) (sample.sampleL[i] * factor));
            if (sample.sampleR != null) sample.sampleR[i] = clampSample((long) (sample.sampleR[i] * factor));
        }
    }

    private static long clampSample(long value) {
        if (value > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (value < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return value;
    }

    /**
     * Converts the renoise base note, transpose, finetune and the real sample
     * rate to an XM relative note number plus finetune (-128..+127)
     *
     * @since 11.07.2026
     */
    private static void setSampleProperties(Sample sample, int renoiseBaseNote, int transpose, int renoiseFineTune, int sampleRate) {
        int f2t = (int) Math.round(1536d * (Math.log((double) sampleRate / (double) NTSC_C4_FREQUENCY) / Math.log(2d)));
        int transp = f2t >> 7;
        int ftune = (f2t & 0x7F) + renoiseFineTune;
        if (ftune > 80) {
            transp++;
            ftune -= 128;
        }
        if (transp > 127) transp = 127;
        else if (transp < -127) transp = -127;

        sample.fineTune = ftune;
        // 48 is C-4, the renoise default base note
        sample.transpose = transp + 48 - renoiseBaseNote + transpose;
        sample.baseFrequency = (int) Math.round(8363d * Math.pow(2d, (double) ((sample.transpose << 7) + sample.fineTune) / 1536d));
    }

    /* ------------------------------ envelopes ---------------------------------- */

    private void readInstrumentEnvelopes(Element instrumentElement, Instrument currentIns) {
        Envelope volumeEnvelope = null;
        Envelope panningEnvelope = null;
        int volumeFadeOut = 0;

        Element sampleGenerator = child(instrumentElement, "SampleGenerator");
        Element modulationSet = (sampleGenerator != null) ?
                child(child(sampleGenerator, "ModulationSets"), "ModulationSet") : null;
        if (modulationSet != null) { // Renoise 2.8+
            Element devices = child(modulationSet, "Devices");
            if (devices != null) {
                // envelopes of XM/IT imports are stored in a special compatibility device
                for (Element device : children(devices, "SampleCompatibilityModulationDevice")) {
                    boolean isVolume = getText(device, "Target", "").equals("Volume");
                    boolean isPanning = getText(device, "Target", "").equals("Panning");
                    if (!isVolume && !isPanning) continue;
                    Envelope envelope = convertEnvelope(
                            getFloat(child(device, "IsActive"), "Value", 0f) != 0f,
                            readEnvelopePoints(child(device, "EnvelopeNodes"), 1d),
                            getBoolean(device, "EnvelopeSustainIsActive", false), getInt(device, "EnvelopeSustainPos", 0),
                            !getText(device, "EnvelopeLoopMode", "Off").equalsIgnoreCase("Off"),
                            getInt(device, "EnvelopeLoopStart", 0), getInt(device, "EnvelopeLoopEnd", 0),
                            isVolume ? EnvelopeType.volume : EnvelopeType.panning);
                    if (isVolume) {
                        volumeEnvelope = envelope;
                        volumeFadeOut = (int) getFloat(child(device, "EnvelopeDecay"), "Value", 0f);
                    } else
                        panningEnvelope = envelope;
                }
                for (Element device : children(devices, "SampleEnvelopeModulationDevice")) {
                    boolean isVolume = getText(device, "Target", "").equals("Volume");
                    boolean isPanning = getText(device, "Target", "").equals("Panning");
                    if (!isVolume && !isPanning) continue;
                    Envelope envelope = convertEnvelope(
                            getFloat(child(device, "IsActive"), "Value", 0f) != 0f,
                            readEnvelopePoints(child(device, "Nodes"), ENVELOPE_POSITION_SCALE),
                            getBoolean(device, "SustainIsActive", false),
                            (int) (getInt(device, "SustainPos", 0) / ENVELOPE_POSITION_SCALE),
                            !getText(device, "LoopMode", "Off").equalsIgnoreCase("Off"),
                            (int) (getInt(device, "LoopStart", 0) / ENVELOPE_POSITION_SCALE),
                            (int) (getInt(device, "LoopEnd", 0) / ENVELOPE_POSITION_SCALE),
                            isVolume ? EnvelopeType.volume : EnvelopeType.panning);
                    if (isVolume) {
                        volumeEnvelope = envelope;
                        volumeFadeOut = (int) getFloat(child(device, "Decay"), "Value", 0f);
                    } else
                        panningEnvelope = envelope;
                }
            }
        } else { // up to Renoise 2.7
            Element envelopes = child(instrumentElement, "Envelopes");
            if (envelopes != null) {
                Element volume = child(envelopes, "Volume");
                if (volume != null) {
                    volumeEnvelope = convertEnvelope(
                            getBoolean(volume, "IsActive", false),
                            readEnvelopePoints(child(volume, "Nodes"), 1d),
                            getBoolean(volume, "SustainIsActive", false), getInt(volume, "SustainPos", 0),
                            !getText(volume, "LoopMode", "Off").equalsIgnoreCase("Off"),
                            getInt(volume, "LoopStart", 0), getInt(volume, "LoopEnd", 0),
                            EnvelopeType.volume);
                    volumeFadeOut = getInt(volume, "Decay", 0);
                }
                Element pan = child(envelopes, "Pan");
                if (pan != null) {
                    panningEnvelope = convertEnvelope(
                            getBoolean(pan, "IsActive", false),
                            readEnvelopePoints(child(pan, "Nodes"), 1d),
                            getBoolean(pan, "SustainIsActive", false), getInt(pan, "SustainPos", 0),
                            !getText(pan, "LoopMode", "Off").equalsIgnoreCase("Off"),
                            getInt(pan, "LoopStart", 0), getInt(pan, "LoopEnd", 0),
                            EnvelopeType.panning);
                }
            }
        }

        currentIns.volumeEnvelope = (volumeEnvelope != null) ? volumeEnvelope : emptyEnvelope(EnvelopeType.volume);
        currentIns.panningEnvelope = (panningEnvelope != null) ? panningEnvelope : emptyEnvelope(EnvelopeType.panning);
        currentIns.volumeFadeOut = volumeFadeOut;
    }

    /** the mixer expects the position/value arrays to exist, even with an unused envelope */
    private static Envelope emptyEnvelope(EnvelopeType envelopeType) {
        Envelope envelope = new Envelope(envelopeType);
        envelope.positions = new int[12];
        envelope.value = new int[12];
        envelope.setNumberOfPoints(0);
        envelope.setXMType(0);
        envelope.sanitize(64);
        return envelope;
    }

    /**
     * @return list of {position, value 0..63} pairs read from "x,y" point strings
     * @since 11.07.2026
     */
    private static List<int[]> readEnvelopePoints(Element nodes, double positionScale) {
        List<int[]> points = new ArrayList<>();
        Element pointsElement = child(nodes, "Points");
        if (pointsElement == null) return points;
        for (Element point : children(pointsElement, "Point")) {
            String[] xy = point.getTextContent().trim().split(",");
            if (xy.length < 2) continue;
            try {
                int x = (int) Math.round(Integer.parseInt(xy[0]) / positionScale);
                int y = (int) Math.abs(127f * Float.parseFloat(xy[1])) >> 1;
                points.add(new int[] { x, y });
            } catch (NumberFormatException ex) {
                /* NOOP */
            }
        }
        points.sort((a, b) -> Integer.compare(a[0], b[0]));
        return points;
    }

    /**
     * Renoise stores sustain and loop as tick positions - XM needs envelope
     * point indices, so missing points are inserted with interpolated values.
     *
     * @since 11.07.2026
     */
    private static Envelope convertEnvelope(boolean isActive, List<int[]> points, boolean sustainOn, int sustainPos,
                                            boolean loopOn, int loopStart, int loopEnd, EnvelopeType envelopeType) {
        if (points.isEmpty()) return emptyEnvelope(envelopeType);
        Envelope envelope = new Envelope(envelopeType);

        if (sustainOn) insertEnvelopePoint(points, sustainPos);
        if (loopOn) {
            insertEnvelopePoint(points, loopStart);
            insertEnvelopePoint(points, loopEnd);
        }

        int numPoints = Math.min(points.size(), 12); // an XM envelope has 12 points maximum
        envelope.positions = new int[numPoints];
        envelope.value = new int[numPoints];
        for (int i = 0; i < numPoints; i++) {
            envelope.positions[i] = points.get(i)[0];
            envelope.value[i] = points.get(i)[1];
        }
        envelope.setNumberOfPoints(numPoints);
        envelope.setSustainPoint_XM(indexOfEnvelopePoint(points, sustainPos, numPoints));
        envelope.loopStartPoint = indexOfEnvelopePoint(points, loopStart, numPoints);
        envelope.loopEndPoint = indexOfEnvelopePoint(points, loopEnd, numPoints);
        envelope.setXMType((isActive ? 0x01 : 0) | (sustainOn ? 0x02 : 0) | (loopOn ? 0x04 : 0));
        envelope.sanitize(64);
        return envelope;
    }

    private static void insertEnvelopePoint(List<int[]> points, int x) {
        int index = 0;
        while (index < points.size() && points.get(index)[0] < x) index++;
        if (index < points.size() && points.get(index)[0] == x) return; // already there

        int y;
        if (index == 0)
            y = points.get(0)[1];
        else if (index >= points.size())
            y = points.get(points.size() - 1)[1];
        else {
            double x1 = points.get(index - 1)[0], y1 = points.get(index - 1)[1];
            double x2 = points.get(index)[0], y2 = points.get(index)[1];
            y = (int) (((y2 - y1) / (x2 - x1)) * (x - x1) + y1);
        }
        points.add(index, new int[] { x, y });
    }

    private static int indexOfEnvelopePoint(List<int[]> points, int x, int numPoints) {
        for (int i = 0; i < numPoints; i++)
            if (points.get(i)[0] == x) return i;
        return 0;
    }

    /* ------------------------------- patterns ---------------------------------- */

    /** one note column cell plus the effect column values assigned to it */
    private static class Cell {

        String note, instrument, volume, panning, delay;
        String effectNumber, effectValue;

        boolean isEmpty() {
            return note == null && instrument == null && volume == null && panning == null && delay == null &&
                    effectNumber == null && effectValue == null;
        }
    }

    private void readPatterns(Element song, int[] noteColumnsOfTrack) {
        List<Element> patterns = children(child(child(song, "PatternPool"), "Patterns"), "Pattern");
        // very old versions store the patterns without a pool:
        if (patterns.isEmpty()) patterns = children(child(song, "Patterns"), "Pattern");

        int numPatterns = patterns.size();
        setNPattern(numPatterns);
        PatternContainer patternContainer = new PatternContainer(this, numPatterns);
        setPatternContainer(patternContainer);

        // collect the note track elements of all patterns first, so pattern
        // track aliases (Renoise 2.8+) can be resolved
        List<List<Element>> patternTracks = new ArrayList<>();
        List<Element> masterTracks = new ArrayList<>();
        for (Element pattern : patterns) {
            Element patternTracksElement = child(pattern, "Tracks");
            patternTracks.add(children(patternTracksElement, "PatternTrack"));
            masterTracks.add(child(patternTracksElement, "PatternMasterTrack"));
        }

        for (int pattNum = 0; pattNum < numPatterns; pattNum++) {
            int rows = getInt(patterns.get(pattNum), "NumberOfLines", 64);
            if (rows < 1) rows = 1;
            else if (rows > 512) rows = 512;

            Cell[][] cells = readPatternCells(pattNum, rows, patternTracks, noteColumnsOfTrack);
            String[][] masterRow = readMasterTrackRows(masterTracks.get(pattNum), rows);

            patternContainer.createPattern(pattNum, rows);
            for (int row = 0; row < rows; row++) {
                patternContainer.createPatternRow(pattNum, row, getNChannels());
                updateTicksPerRow(cells[row]);

                int[] masterCommand = null;
                if (masterRow[row] != null) {
                    masterCommand = convertMasterTrackEffect(masterRow[row][0], masterRow[row][1]);
                }

                for (int channel = 0; channel < getNChannels(); channel++) {
                    PatternElement element = patternContainer.createPatternElement(pattNum, row, channel);
                    Cell cell = cells[row][channel];
                    if (cell == null || (cell.isEmpty() && masterCommand == null)) continue;
                    masterCommand = setPatternElement(element, cell, masterCommand);
                }
            }
        }
    }

    private Cell[][] readPatternCells(int pattNum, int rows, List<List<Element>> patternTracks, int[] noteColumnsOfTrack) {
        Cell[][] cells = new Cell[rows][getNChannels()];
        List<Element> trackElements = patternTracks.get(pattNum);

        int channelIndex = 0;
        for (int track = 0; track < noteColumnsOfTrack.length; track++) {
            Element patternTrack = (track < trackElements.size()) ? trackElements.get(track) : null;
            // resolve pattern track aliases (Renoise 2.8+)
            int guard = 0;
            while (patternTrack != null && guard++ < 16) {
                int alias = getInt(patternTrack, "AliasPatternIndex", -1);
                if (alias < 0 || alias >= patternTracks.size()) break;
                patternTrack = (track < patternTracks.get(alias).size()) ? patternTracks.get(alias).get(track) : null;
            }

            Element lines = (patternTrack != null) ? child(patternTrack, "Lines") : null;
            if (lines != null) {
                for (Element line : children(lines, "Line")) {
                    int row;
                    try {
                        row = Integer.parseInt(line.getAttribute("index"));
                    } catch (NumberFormatException ex) {
                        continue;
                    }
                    if (row < 0 || row >= rows) continue;

                    Element noteColumns = child(line, "NoteColumns");
                    if (noteColumns != null) {
                        int column = 0;
                        for (Element noteColumn : children(noteColumns, "NoteColumn")) {
                            if (column >= noteColumnsOfTrack[track]) break;
                            String note = getText(noteColumn, "Note", null);
                            String instrument = getText(noteColumn, "Instrument", null);
                            String volume = getText(noteColumn, "Volume", null);
                            String panning = getText(noteColumn, "Panning", null);
                            String delay = getText(noteColumn, "Delay", null);
                            if (note != null || instrument != null || volume != null || panning != null || delay != null) {
                                Cell cell = getCell(cells, row, channelIndex + column);
                                cell.note = note;
                                cell.instrument = instrument;
                                cell.volume = volume;
                                cell.panning = panning;
                                cell.delay = delay;
                            }
                            column++;
                        }
                    }
                    Element effectColumns = child(line, "EffectColumns");
                    if (effectColumns != null) {
                        List<Element> effectColumn = children(effectColumns, "EffectColumn");
                        if (!effectColumn.isEmpty()) { // only the first effect column is converted
                            String number = getText(effectColumn.get(0), "Number", null);
                            String value = getText(effectColumn.get(0), "Value", null);
                            if (number != null || value != null) {
                                // spread the track effect to all note columns of this track
                                for (int column = 0; column < noteColumnsOfTrack[track]; column++) {
                                    Cell cell = getCell(cells, row, channelIndex + column);
                                    cell.effectNumber = (number == null) ? "00" : number;
                                    cell.effectValue = (value == null) ? "00" : value;
                                }
                            }
                        }
                    }
                }
            }
            channelIndex += noteColumnsOfTrack[track];
        }
        return cells;
    }

    private static Cell getCell(Cell[][] cells, int row, int channel) {
        Cell cell = cells[row][channel];
        if (cell == null) cell = cells[row][channel] = new Cell();
        return cell;
    }

    /** @return per row the first master track effect column as {number, value} */
    private String[][] readMasterTrackRows(Element masterTrack, int rows) {
        String[][] result = new String[rows][];
        Element lines = (masterTrack != null) ? child(masterTrack, "Lines") : null;
        if (lines == null) return result;
        for (Element line : children(lines, "Line")) {
            int row;
            try {
                row = Integer.parseInt(line.getAttribute("index"));
            } catch (NumberFormatException ex) {
                continue;
            }
            if (row < 0 || row >= rows) continue;
            List<Element> effectColumn = children(child(line, "EffectColumns"), "EffectColumn");
            if (!effectColumn.isEmpty()) {
                String number = getText(effectColumn.get(0), "Number", null);
                String value = getText(effectColumn.get(0), "Value", null);
                if (number != null || value != null)
                    result[row] = new String[] { (number == null) ? "00" : number, (value == null) ? "00" : value };
            }
        }
        return result;
    }

    /** a "set ticks per line" effect anywhere in the row changes the conversion of all following slide values */
    private void updateTicksPerRow(Cell[] rowCells) {
        for (Cell cell : rowCells) {
            if (cell == null || cell.effectNumber == null) continue;
            String effect = normalizeEffectNumber(cell.effectNumber);
            if (effect == null || effect.charAt(0) != 'Z') continue;
            char commandForTicks = (playbackEngineVersion == MOD_VERSION_COMPATIBLE) ? 'L' : 'K';
            if (effect.charAt(1) == commandForTicks) {
                int value = parseHex(cell.effectValue);
                if (value > 0) ticksPerRow = Math.min(value, 0x1F);
            }
        }
    }

    /**
     * Fills one pattern element from the renoise cell.
     *
     * @param masterCommand a pending master track command for this row, or null
     * @return the master track command, if it is still pending, null if consumed
     * @since 11.07.2026
     */
    private int[] setPatternElement(PatternElement element, Cell cell, int[] masterCommand) {
        int xmNote = 0;
        if (cell.note != null) {
            xmNote = convertNote(cell.note);
            if (xmNote == ModConstants.KEY_OFF) {
                element.setNoteIndex(ModConstants.KEY_OFF);
                element.setPeriod(ModConstants.KEY_OFF);
                xmNote = 0;
            } else if (xmNote > 0) {
                element.setNoteIndex(xmNote);
                element.setPeriod(ModConstants.noteValues[xmNote - 1]);
            }
        }

        int xmInstrument = 0;
        if (cell.instrument != null) {
            int instrument = parseHex(cell.instrument);
            if (instrument >= 0 && instrument != 0xFF) {
                xmInstrument = instrument + 1;
                element.setInstrument(xmInstrument);
            }
        }

        boolean effectUsed = false;
        if (cell.effectNumber != null) {
            int[] effect = convertEffect(cell.effectNumber, cell.effectValue, xmNote, xmInstrument);
            if (effect != null) {
                element.setEffect(effect[0]);
                element.setEffectOp(effect[1]);
                effectUsed = true;
            }
        }

        boolean volumeUsed = false;
        if (cell.volume != null && cell.volume.length() >= 2) {
            int xmVolume = convertVolumeColumn(cell.volume);
            volumeUsed = xmVolume > 0;
            if (volumeUsed)
                setVolumeColumn(element, xmVolume);
            else if (!effectUsed) {
                int[] effect = transposeVolumeToEffect(cell.volume);
                if (effect != null) {
                    element.setEffect(effect[0]);
                    element.setEffectOp(effect[1]);
                    effectUsed = true;
                }
            }
        }

        if (cell.delay != null && !effectUsed) {
            int delay = (int) Math.round((double) (parseHex(cell.delay) * ticksPerRow) / 255d);
            if (delay == ticksPerRow) delay--;
            if (delay > 0) {
                element.setEffect(0x0E);
                element.setEffectOp(0xD0 | Math.min(delay, 0x0F));
                effectUsed = true;
            }
        }

        if (cell.panning != null && cell.panning.length() >= 2) {
            boolean panningUsed = false;
            if (!volumeUsed) {
                int xmVolume = convertPanningColumn(cell.panning);
                panningUsed = xmVolume > 0;
                if (panningUsed) setVolumeColumn(element, xmVolume);
            }
            if (!panningUsed && !effectUsed) {
                int[] effect = transposePanningToEffect(cell.panning);
                if (effect != null) {
                    element.setEffect(effect[0]);
                    element.setEffectOp(effect[1]);
                    effectUsed = true;
                }
            }
        }

        if (masterCommand != null && !effectUsed) {
            element.setEffect(masterCommand[0]);
            element.setEffectOp(masterCommand[1]);
            return null; // consumed
        }
        return masterCommand;
    }

    /**
     * @return XM note (1..96), KEY_OFF or 0 for no/unsupported note
     * @since 11.07.2026
     */
    private static int convertNote(String note) {
        if (note.equals("OFF")) return ModConstants.KEY_OFF;
        if (note.length() < 3) return 0;
        String name = note.substring(0, 2);
        for (int index = 0; index < NOTE_NAMES.length; index++) {
            if (NOTE_NAMES[index].equals(name)) {
                int octave = note.charAt(2) - '0';
                if (octave < 0 || octave > 9) return 0;
                int xmNote = (octave * 12) + index + 1;
                return (xmNote <= 96) ? xmNote : 0;
            }
        }
        return 0;
    }

    private static int parseHex(String value) {
        if (value == null) return 0;
        try {
            return Integer.parseInt(value.trim(), 16);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
    }

    /**
     * Before Renoise 2.8 the effect commands were plain hex numbers - they map
     * 1:1 to the letter commands used from then on.
     *
     * @return a two character letter style effect number or null
     * @since 11.07.2026
     */
    private String normalizeEffectNumber(String effectNumber) {
        if (effectNumber == null || effectNumber.length() < 2) return null;
        if (docVersion >= DOC_VERSION_LETTER_FX) return effectNumber;

        if (!isHexDigit(effectNumber.charAt(0)) || !isHexDigit(effectNumber.charAt(1))) return effectNumber;
        int number = parseHex(effectNumber);
        switch (number) {
            case 0xF0: return "ZT";
            case 0xF1: return "ZL";
            case 0xF2: return "ZK";
            case 0xF4: return "ZG";
            case 0xFB: return "ZB";
            case 0xFD: return "ZD";
            default:
                if (number >= 0 && number < OLD_EFFECT_LETTERS.length)
                    return "0" + OLD_EFFECT_LETTERS[number];
                return null;
        }
    }

    /**
     * @return {XM effect, XM effect op} or null, if the effect cannot be converted
     * @since 11.07.2026
     */
    private int[] convertEffect(String effectNumber, String effectValue, int xmNote, int xmInstrument) {
        String effect = normalizeEffectNumber(effectNumber);
        if (effect == null || effect.length() < 2) return null;
        int value = parseHex(effectValue);
        int[] result = null;
        if (effect.charAt(0) == '0')
            result = convertSampleEffect(effect.charAt(1), value, xmNote, xmInstrument);
        else if (effect.charAt(0) == 'Z')
            result = convertGlobalEffect(effect.charAt(1), value);
        return (result == null || (result[0] == 0 && result[1] == 0)) ? null : result;
    }

    private int[] convertSampleEffect(char command, int value, int xmNote, int xmInstrument) {
        switch (command) {
            case 'A': // Arpeggio (x = first note offset, y = second note offset)
                return new int[] { 0x00, value };
            case 'U': // pitch slide up (01 is 1/16th semitone, 10 is one semitone)
                return convertPortamento(0x01, value, false);
            case 'D': // pitch slide down
                return convertPortamento(0x02, value, false);
            case 'M': // set channel volume
                return new int[] { 0x0C, (value + 1) >> 2 };
            case 'G': { // glide to note with step xx
                int porta = getPortamentoValue(value, false);
                if (porta == 0 && value != 0) return null; // conversion would be inaudible
                return new int[] { 0x03, Math.min(porta, 0xFF) };
            }
            case 'I': // volume slide up with step xx
                return convertVolumeSlide(true, value);
            case 'O': // volume slide down with step xx
                return convertVolumeSlide(false, value);
            case 'P': // set panning (00 = full left, FF = full right)
                return new int[] { 0x08, value };
            case 'S': // trigger sample at slice/offset xx
                return new int[] { 0x09, getSampleOffsetValue(value, xmNote, xmInstrument) };
            case 'B': // play sample backwards
                return new int[] { 0x21, 0x9F };
            case 'Q': // delay all notes by xx ticks
                return new int[] { 0x0E, 0xD0 | Math.min(value, 0x0F) };
            case 'R': // retrigger a note every y ticks with volume x
                return convertMultiRetrig(value);
            case 'V': // vibrato (x = speed, y = depth)
                return new int[] { 0x04, value };
            case 'T': // tremolo (x = speed, y = depth)
                return new int[] { 0x07, value };
            default: // 0C volume slicer, 0W surround, 0L pre-mixer volume, 0N, 0E, 0J, 0X are not convertible
                return null;
        }
    }

    private int[] convertGlobalEffect(char command, int value) {
        switch (command) {
            case 'T': // set BPM (20 - FF, 00 = stop song)
                return (value < 0x20) ? null : new int[] { 0x0F, Math.min(value, 0xFF) };
            case 'L': // set lines per beat - only in the old timing model this is the tracker speed
                return (playbackEngineVersion == MOD_VERSION_COMPATIBLE) ? new int[] { 0x0F, Math.min(value, 0x1F) } : null;
            case 'K': // set ticks per line
                return new int[] { 0x0F, Math.min(value, 0x1F) };
            case 'B': { // pattern break to line xx - XM stores the line BCD like
                int line = Math.min(value, 99);
                return new int[] { 0x0D, ((line / 10) << 4) | (line % 10) };
            }
            case 'D': // pause pattern playback by xx lines
                return new int[] { 0x0E, 0xE0 | Math.min(value, 0x0F) };
            default: // ZG song groove is not convertible
                return null;
        }
    }

    private int[] convertMasterTrackEffect(String effectNumber, String effectValue) {
        String effect = normalizeEffectNumber(effectNumber);
        if (effect == null || effect.length() < 2) return null;
        int value = parseHex(effectValue);
        int[] result = null;
        if (effect.charAt(0) == '0') {
            switch (effect.charAt(1)) {
                case 'M': // set master volume
                    return new int[] { 0x10, Math.min(value, 0xC0) / 3 };
                case 'I': // master volume slide up
                    return new int[] { 0x11, Math.min(getVolumeSlideValue(value), 0x0F) << 4 };
                case 'O': // master volume slide down
                    return new int[] { 0x11, Math.min(getVolumeSlideValue(value), 0x0F) };
                default:
                    return null;
            }
        } else if (effect.charAt(0) == 'Z')
            result = convertGlobalEffect(effect.charAt(1), value);
        return (result == null || (result[0] == 0 && result[1] == 0)) ? null : result;
    }

    /* -------------------- pitch and volume slide conversion --------------------- */

    /**
     * In renoise every effect value unit is applied per tick, in XM the value
     * is applied (ticks-1) times per row - so values are divided, unless the
     * song runs in the XM compatible pitch effects mode.
     *
     * @since 11.07.2026
     */
    private int getPortamentoValue(int value, boolean ignorePitchCompatibilityMode) {
        if (ignorePitchCompatibilityMode || !pitchCompatibilityMode) {
            int divider = (ticksPerRow > 1) ? ticksPerRow - 1 : ticksPerRow;
            return (int) Math.round((double) value / (double) divider);
        }
        return value;
    }

    private int[] convertPortamento(int effect, int value, boolean ignorePitchCompatibilityMode) {
        if (!pitchCompatibilityMode && isFinePortamentoCloser(value))
            return new int[] { 0x0E, (effect << 4) | Math.min(value, 0x0F) };
        int porta = getPortamentoValue(value, ignorePitchCompatibilityMode);
        if (porta == 0 && value != 0) return null;
        return new int[] { effect, Math.min(porta, 0xFF) };
    }

    private boolean isFinePortamentoCloser(int value) {
        if (value <= 0) return false;
        int divider = (ticksPerRow > 1) ? ticksPerRow - 1 : ticksPerRow;
        int slideLoss = value % divider;
        if (slideLoss > (divider >> 1)) slideLoss -= divider;
        else if (value == slideLoss) slideLoss = divider - value;
        slideLoss = Math.abs(slideLoss);
        if (value < 19 && slideLoss > 0) // beyond a slide value of 19 the fine slide cannot keep up anyway
            return (value - 0x0F) < slideLoss;
        return false;
    }

    /** renoise volume values are 4 times more accurate than XM and applied per tick */
    private int getVolumeSlideValue(int value) {
        int divider = (ticksPerRow > 1) ? ticksPerRow - 1 : ticksPerRow;
        return (int) Math.round((double) (value >> 2) / (double) divider);
    }

    private int getPanningSlideValue(int value) {
        int divider = (ticksPerRow > 1) ? ticksPerRow - 1 : ticksPerRow;
        return value / divider;
    }

    private boolean isFineVolumeSlideCloser(int value) {
        int divider = (ticksPerRow > 1) ? ticksPerRow - 1 : ticksPerRow;
        int slideLoss = value % divider;
        if (slideLoss > (divider >> 1)) slideLoss -= divider;
        int maxFineVolume = 0x0F << 2;
        return slideLoss > 0 && value < (maxFineVolume << 1) && (value - maxFineVolume) < slideLoss;
    }

    private int[] convertVolumeSlide(boolean up, int value) {
        if (isFineVolumeSlideCloser(value))
            return new int[] { 0x0E, (up ? 0xA0 : 0xB0) | Math.min(value >> 2, 0x0F) };
        int slide = Math.min(getVolumeSlideValue(value), 0x0F);
        return new int[] { 0x0A, up ? slide << 4 : slide };
    }

    private int[] convertMultiRetrig(int value) {
        int volumeChange = (value >> 4) & 0x0F;
        int ticks = value & 0x0F;
        // renoise uses percentages, XM fixed steps - map to the nearest one
        switch (volumeChange) {
            case 2: case 3: case 4: case 0x0A: case 0x0B: case 0x0C:
                volumeChange++;
                break;
            case 5: case 0x0D:
                volumeChange += 2;
                break;
            case 8:
                volumeChange = 0;
                break;
            default:
                break;
        }
        return new int[] { 0x1B, (volumeChange << 4) | ticks };
    }

    /**
     * Converts a renoise sample offset (00 = start, FF = end of sample) to
     * the XM offset effect value (in 256 sample steps)
     *
     * @since 11.07.2026
     */
    private int getSampleOffsetValue(int value, int xmNote, int xmInstrument) {
        if (!sampleOffsetCompatibilityMode && xmNote > 0 && xmInstrument > 0 && xmInstrument <= keyMaps.length) {
            int sampleUsed = keyMaps[xmInstrument - 1][xmNote - 1];
            if (sampleUsed >= 0 && sampleUsed < sampleFrames[xmInstrument - 1].length) {
                int offset = (sampleFrames[xmInstrument - 1][sampleUsed] * value) >> 16;
                return Math.min(offset, 0xFF);
            }
        }
        return value & 0xFF;
    }

    /* --------------------------- volume/panning column -------------------------- */

    /**
     * @return the XM volume column value (0x10-0xFF) or 0
     * @since 11.07.2026
     */
    private int convertVolumeColumn(String volume) {
        char command = volume.charAt(0);
        if (command >= '0' && command <= '8' && isHexDigit(volume.charAt(1))) {
            // 00 - 80: set note volume
            return (parseHex(volume) >> 1) + 0x10;
        }
        int value = isHexDigit(volume.charAt(1)) ? Integer.parseInt(volume.substring(1, 2), 16) : 0;
        switch (command) {
            case 'I': // volume fade in with step x
                return (value < 0x05) ?
                        0x90 | Math.min(value << 2, 0x0F) :
                        0x70 | Math.min(getVolumeSlideValue(value << 4), 0x0F);
            case 'O': // volume fade out with step x
                return (value < 0x05) ?
                        0x80 | Math.min(value << 2, 0x0F) :
                        0x60 | Math.min(getVolumeSlideValue(value << 4), 0x0F);
            default: // G|U|D|B|Q|R|C are transposed to the effect column
                return 0;
        }
    }

    /**
     * @return the XM volume column value (0xC0-0xEF) or 0
     * @since 11.07.2026
     */
    private int convertPanningColumn(String panning) {
        char command = panning.charAt(0);
        if (command >= '0' && command <= '8' && isHexDigit(panning.charAt(1))) {
            // 00 = full left, 40 = center, 80 = full right
            return 0xC0 | Math.min(parseHex(panning) >> 3, 0x0F);
        }
        int value = isHexDigit(panning.charAt(1)) ? Integer.parseInt(panning.substring(1, 2), 16) : 0;
        switch (command) {
            case 'J': // panning slide left with step x
                return 0xD0 | Math.min(getPanningSlideValue(Math.min(value, 0x08) << 4), 0x0F);
            case 'K': // panning slide right with step x
                return 0xE0 | Math.min(getPanningSlideValue(Math.min(value, 0x08) << 4), 0x0F);
            default:
                return 0;
        }
    }

    /** translates an XM volume column byte to the internal volume effect representation */
    private static void setVolumeColumn(PatternElement element, int xmVolume) {
        if (xmVolume >= 0x10 && xmVolume <= 0x50) {
            element.setVolumeEffect(1);
            element.setVolumeEffectOp(xmVolume - 0x10);
        } else if (xmVolume >= 0x60) {
            element.setVolumeEffect((xmVolume >> 4) - 0x04);
            element.setVolumeEffectOp(xmVolume & 0x0F);
        }
    }

    private int[] transposeVolumeToEffect(String volume) {
        char command = volume.charAt(0);
        if (command >= '0' && command <= '8' && isHexDigit(volume.charAt(1)))
            return checkEffect(new int[] { 0x0C, parseHex(volume) >> 1 });
        int value = isHexDigit(volume.charAt(1)) ? Integer.parseInt(volume.substring(1, 2), 16) : 0;
        switch (command) {
            case 'I':
                return checkEffect(convertVolumeSlide(true, value << 4));
            case 'O': {
                int slide = Math.min(getVolumeSlideValue(value << 4), 0x0F);
                return checkEffect(new int[] { 0x0A, slide });
            }
            default:
                return checkEffect(transposeVolPanCommandToEffect(command, value));
        }
    }

    private int[] transposePanningToEffect(String panning) {
        char command = panning.charAt(0);
        if (command >= '0' && command <= '8' && isHexDigit(panning.charAt(1)))
            return checkEffect(new int[] { 0x08, Math.min(parseHex(panning) << 1, 0xFF) });
        int value = isHexDigit(panning.charAt(1)) ? Integer.parseInt(panning.substring(1, 2), 16) : 0;
        switch (command) {
            case 'J':
                return checkEffect(new int[] { 0x19, Math.min(getPanningSlideValue(value << 4), 0x0F) << 4 });
            case 'K':
                return checkEffect(new int[] { 0x19, Math.min(getPanningSlideValue(value << 4), 0x0F) });
            default:
                return checkEffect(transposeVolPanCommandToEffect(command, value));
        }
    }

    /** the volume/panning column letter commands shared by both columns */
    private int[] transposeVolPanCommandToEffect(char command, int value) {
        switch (command) {
            case 'U': // slide pitch up by x semitones
                return new int[] { 0x01, Math.min(getPortamentoValue(value << 4, true), 0xFF) };
            case 'D': // slide pitch down by x semitones
                return new int[] { 0x02, Math.min(getPortamentoValue(value << 4, true), 0xFF) };
            case 'G': // glide towards given note by x semitones
                return new int[] { 0x03, (value < 0x0F) ? Math.min(getPortamentoValue(value << 4, true), 0xFF) : 0xFF };
            case 'B': // play sample backwards (0) or forwards again (1)
                return new int[] { 0x21, 0x9F - value };
            case 'Q': // delay note by x ticks
                return new int[] { 0x0E, 0xD0 | Math.min(value, 0x0F) };
            case 'R': // retrigger note every x ticks
                return new int[] { 0x0E, 0x90 | (value & 0x0F) };
            case 'C': // cut note after x ticks
                return new int[] { 0x0E, 0xC0 | Math.min(value, 0x0F) };
            default:
                return null;
        }
    }

    private static int[] checkEffect(int[] effect) {
        return (effect == null || (effect[0] == 0 && effect[1] == 0)) ? null : effect;
    }

    /* ------------------------------ sample decoding ----------------------------- */

    private static class DecodedSample {

        int sampleRate;
        int channels;
        int frames;
        long[] left, right;
    }

    /** growable buffer for decoders that do not know the amount of frames upfront */
    private static class PCMBuffer {

        long[] left = new long[65536];
        long[] right = new long[65536];
        int frames;

        void addFrame(long leftSample, long rightSample) {
            if (frames >= left.length) {
                left = java.util.Arrays.copyOf(left, left.length << 1);
                right = java.util.Arrays.copyOf(right, right.length << 1);
            }
            left[frames] = leftSample;
            right[frames] = rightSample;
            frames++;
        }

        DecodedSample toDecodedSample(int sampleRate, int channels) {
            DecodedSample result = new DecodedSample();
            result.sampleRate = sampleRate;
            result.channels = channels;
            result.frames = frames;
            result.left = left;
            result.right = (channels > 1) ? right : null;
            return result;
        }
    }

    private DecodedSample decodeSampleFile(int instrumentIndex, int sampleIndex, Map<String, byte[]> sampleFiles) {
        Pattern entryPattern = Pattern.compile(
                String.format("SampleData/Instrument%02d.*/Sample%02d.*\\.(wav|aiff?|ogg|flac|mp3)$", instrumentIndex, sampleIndex),
                Pattern.CASE_INSENSITIVE);
        for (Map.Entry<String, byte[]> file : sampleFiles.entrySet()) {
            Matcher matcher = entryPattern.matcher(file.getKey());
            if (!matcher.find()) continue;
            String extension = matcher.group(1).toLowerCase();
            try {
                switch (extension) {
                    case "flac":
                        return decodeFlac(file.getValue());
                    case "ogg":
                        return decodeOgg(file.getValue());
                    case "mp3":
                        return decodeMp3(file.getValue());
                    default: // wav, aif, aiff
                        return decodeWithAudioSystem(file.getValue());
                }
            } catch (Throwable ex) {
                logger.log(Level.ERROR, "[RenoiseTrackerMod] failed decoding sample " + file.getKey(), ex);
                return null;
            }
        }
        return null; // sample without audio data
    }

    private static DecodedSample decodeFlac(byte[] data) throws IOException {
        FLACDecoder decoder = new FLACDecoder(new ByteArrayInputStream(data));
        decoder.readMetadata();
        int channels = decoder.getStreamInfo().getChannels();
        int bits = decoder.getStreamInfo().getBitsPerSample();
        int bytesPerSample = (bits + 7) >> 3;
        int frameSize = bytesPerSample * channels;

        PCMBuffer pcm = new PCMBuffer();
        while (!decoder.isEOF()) {
            ByteData byteData;
            try {
                decoder.findFrameSync();
                Frame frame = decoder.readFrame();
                byteData = decoder.decodeFrame(frame, null);
            } catch (FrameDecodeException ex) {
                continue;
            } catch (java.io.EOFException ex) {
                break;
            }
            if (byteData == null) break;
            byte[] buffer = byteData.getData();
            int length = byteData.getLen();
            for (int offset = 0; offset + frameSize <= length; offset += frameSize) {
                long leftSample = readPCMSample(buffer, offset, bits);
                long rightSample = (channels > 1) ? readPCMSample(buffer, offset + bytesPerSample, bits) : leftSample;
                pcm.addFrame(leftSample, rightSample);
            }
        }
        return pcm.toDecodedSample(decoder.getStreamInfo().getSampleRate(), channels);
    }

    /** little endian PCM to 32 bit signed - 8 bit data is unsigned */
    private static long readPCMSample(byte[] buffer, int offset, int bits) {
        return switch ((bits + 7) >> 3) {
            case 1 -> (long) ((buffer[offset] & 0xFF) - 0x80) << 24;
            case 2 -> (long) ((short) ((buffer[offset] & 0xFF) | (buffer[offset + 1] << 8))) << 16;
            case 3 -> (long) (((buffer[offset] & 0xFF) | ((buffer[offset + 1] & 0xFF) << 8) | (buffer[offset + 2] << 16))) << 8;
            default -> (buffer[offset] & 0xFF) | ((buffer[offset + 1] & 0xFF) << 8) | ((buffer[offset + 2] & 0xFF) << 16) | ((long) buffer[offset + 3] << 24);
        };
    }

    /** wav and aiff (8/16/24/32 bit and float) are handled by javax.sound */
    private static DecodedSample decodeWithAudioSystem(byte[] data) throws Exception {
        javax.sound.sampled.AudioInputStream audioInputStream =
                javax.sound.sampled.AudioSystem.getAudioInputStream(new ByteArrayInputStream(data));
        javax.sound.sampled.AudioFormat format = audioInputStream.getFormat();
        if (format.getEncoding() != javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED || format.getSampleSizeInBits() > 16) {
            javax.sound.sampled.AudioFormat targetFormat = new javax.sound.sampled.AudioFormat(
                    format.getSampleRate(), 16, format.getChannels(), true, false);
            audioInputStream = javax.sound.sampled.AudioSystem.getAudioInputStream(targetFormat, audioInputStream);
            format = targetFormat;
        }
        byte[] pcmData;
        try (java.io.InputStream stream = audioInputStream) {
            ByteArrayOutputStream collect = new ByteArrayOutputStream();
            stream.transferTo(collect);
            pcmData = collect.toByteArray();
        }
        int channels = format.getChannels();
        int bytesPerSample = (format.getSampleSizeInBits() + 7) >> 3;
        int frameSize = bytesPerSample * channels;
        boolean bigEndian = format.isBigEndian();

        PCMBuffer pcm = new PCMBuffer();
        for (int offset = 0; offset + frameSize <= pcmData.length; offset += frameSize) {
            long leftSample = readJavaSoundSample(pcmData, offset, bytesPerSample, bigEndian);
            long rightSample = (channels > 1) ? readJavaSoundSample(pcmData, offset + bytesPerSample, bytesPerSample, bigEndian) : leftSample;
            pcm.addFrame(leftSample, rightSample);
        }
        return pcm.toDecodedSample((int) format.getSampleRate(), channels);
    }

    private static long readJavaSoundSample(byte[] buffer, int offset, int bytesPerSample, boolean bigEndian) {
        if (bytesPerSample == 1) return (long) buffer[offset] << 24;
        short value = bigEndian ?
                (short) ((buffer[offset] << 8) | (buffer[offset + 1] & 0xFF)) :
                (short) ((buffer[offset] & 0xFF) | (buffer[offset + 1] << 8));
        return (long) value << 16;
    }

    private static DecodedSample decodeMp3(byte[] data) throws Exception {
        Bitstream bitstream = new Bitstream(new ByteArrayInputStream(data));
        javazoom.jl.decoder.Decoder decoder = new javazoom.jl.decoder.Decoder();

        PCMBuffer pcm = new PCMBuffer();
        int sampleRate = ModConstants.BASEFREQUENCY;
        int channels = 1;
        Header header;
        while ((header = bitstream.readFrame()) != null) {
            SampleBuffer sampleBuffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
            sampleRate = sampleBuffer.getSampleFrequency();
            channels = decoder.getOutputChannels();
            short[] buffer = sampleBuffer.getBuffer();
            int length = sampleBuffer.getBufferLength();
            if (channels == 2) {
                for (int i = 0; i + 1 < length; i += 2)
                    pcm.addFrame((long) buffer[i] << 16, (long) buffer[i + 1] << 16);
            } else {
                for (int i = 0; i < length; i++)
                    pcm.addFrame((long) buffer[i] << 16, (long) buffer[i] << 16);
            }
            bitstream.closeFrame();
        }
        return pcm.toDecodedSample(sampleRate, channels);
    }

    private static DecodedSample decodeOgg(byte[] data) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(data);
        com.jcraft.jogg.SyncState oggSyncState = new com.jcraft.jogg.SyncState();
        com.jcraft.jogg.StreamState oggStreamState = new com.jcraft.jogg.StreamState();
        com.jcraft.jogg.Page oggPage = new com.jcraft.jogg.Page();
        com.jcraft.jogg.Packet oggPacket = new com.jcraft.jogg.Packet();

        com.jcraft.jorbis.Info vorbisInfo = new com.jcraft.jorbis.Info();
        com.jcraft.jorbis.Comment vorbisComment = new com.jcraft.jorbis.Comment();
        com.jcraft.jorbis.DspState vorbisDSPState = new com.jcraft.jorbis.DspState();
        com.jcraft.jorbis.Block vorbisBlock = new com.jcraft.jorbis.Block(vorbisDSPState);

        oggSyncState.init();

        PCMBuffer pcm = new PCMBuffer();
        boolean eos = false;
        int state = 0;

        while (!eos) {
            int index = oggSyncState.buffer(4096);
            int bytes = input.read(oggSyncState.data, index, 4096);
            if (bytes <= 0) break;
            oggSyncState.wrote(bytes);

            while (oggSyncState.pageout(oggPage) == 1) {
                if (state == 0) {
                    oggStreamState.init(oggPage.serialno());
                    oggStreamState.reset();
                    if (oggStreamState.pagein(oggPage) < 0)
                        throw new IOException("Error reading first page of Ogg bitstream data");
                    if (oggStreamState.packetout(oggPacket) != 1)
                        throw new IOException("Error reading initial header packet");
                    vorbisInfo.init();
                    vorbisComment.init();
                    if (vorbisInfo.synthesis_headerin(vorbisComment, oggPacket) < 0)
                        throw new IOException("This Ogg bitstream does not contain Vorbis audio data");

                    int i = 0;
                    while (i < 2) {
                        int result = oggSyncState.pageout(oggPage);
                        if (result == 0) {
                            index = oggSyncState.buffer(4096);
                            bytes = input.read(oggSyncState.data, index, 4096);
                            if (bytes <= 0) throw new IOException("End of file before finding all Vorbis headers");
                            oggSyncState.wrote(bytes);
                            continue;
                        }
                        oggStreamState.pagein(oggPage);
                        while (i < 2) {
                            result = oggStreamState.packetout(oggPacket);
                            if (result == 0) break;
                            vorbisInfo.synthesis_headerin(vorbisComment, oggPacket);
                            i++;
                        }
                    }
                    vorbisDSPState.synthesis_init(vorbisInfo);
                    vorbisBlock.init(vorbisDSPState);
                    state = 1;
                } else {
                    if (oggStreamState.pagein(oggPage) < 0) continue;
                    while (oggStreamState.packetout(oggPacket) == 1) {
                        if (vorbisBlock.synthesis(oggPacket) == 0)
                            vorbisDSPState.synthesis_blockin(vorbisBlock);

                        float[][][] pcmFloat = new float[1][][];
                        int[] pcmIndex = new int[vorbisInfo.channels];
                        int samples;
                        while ((samples = vorbisDSPState.synthesis_pcmout(pcmFloat, pcmIndex)) > 0) {
                            for (int s = 0; s < samples; s++) {
                                long leftSample = oggFloatToSample(pcmFloat[0][0][pcmIndex[0] + s]);
                                long rightSample = (vorbisInfo.channels > 1) ?
                                        oggFloatToSample(pcmFloat[0][1][pcmIndex[1] + s]) : leftSample;
                                pcm.addFrame(leftSample, rightSample);
                            }
                            vorbisDSPState.synthesis_read(samples);
                        }
                    }
                    if (oggPage.eos() != 0) eos = true;
                }
            }
        }
        int sampleRate = vorbisInfo.rate;
        int channels = vorbisInfo.channels;
        oggStreamState.clear();
        vorbisBlock.clear();
        vorbisDSPState.clear();
        vorbisInfo.clear();
        return pcm.toDecodedSample(sampleRate, channels);
    }

    private static long oggFloatToSample(float value) {
        int sample = (int) (value * 32767f);
        if (sample > 32767) sample = 32767;
        else if (sample < -32768) sample = -32768;
        return (long) sample << 16;
    }
}
