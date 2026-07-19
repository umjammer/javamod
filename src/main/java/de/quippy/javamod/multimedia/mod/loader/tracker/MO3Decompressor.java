/*
 * Load_mo3.cpp
 * ------------
 * Purpose: MO3 module loader.
 * Notes  : (currently none)
 * Authors: Johannes Schultz / OpenMPT Devs
 *          Based on documentation and the decompression routines from the
 *          open-source UNMO3 project (https://github.com/lclevy/unmo3).
 *          The modified decompression code has been relicensed to the BSD
 *          license with permission from Laurent Clévy.
 * The OpenMPT source code is released under the BSD license. Read LICENSE for more details.
 */

package de.quippy.javamod.multimedia.mod.loader.tracker;

import de.quippy.javamod.multimedia.mod.ModConstants;


/**
 * Decompressor helper for MO3 compressed headers/sequences/patterns and samples.
 *
 * @author Johannes Schultz / OpenMPT Devs
 * @since 10.07.2026
 */
public class MO3Decompressor {

    private static class BitState {
        int data = 0;
        int carry = 0;
        int srcIdx = 0;
    }

    private static void readCtrlBit(byte[] src, BitState state) {
        state.data <<= 1;
        state.carry = (state.data >= 256) ? 1 : 0;
        state.data &= 255;
        if (state.data == 0) {
            state.data = src[state.srcIdx++] & 0xFF;
            state.data = (state.data << 1) + 1;
            state.carry = (state.data >= 256) ? 1 : 0;
            state.data &= 255;
        }
    }

    public static byte[] unpack(byte[] src, int[] srcOffset, int initSize) {
        byte[] dst = new byte[initSize];
        int dstIdx = 0;
        BitState state = new BitState();
        state.srcIdx = srcOffset[0];

        int size = initSize;
        dst[dstIdx++] = src[state.srcIdx++];
        size--;

        int strLen = 0;
        int previousPtr = 0;

        while (size > 0) {
            readCtrlBit(src, state);
            if (state.carry == 0) { // literal
                dst[dstIdx++] = src[state.srcIdx++];
                size--;
            } else {
                int ebp = 0;
                // DECODE_CTRL_BITS
                strLen++;
                do {
                    readCtrlBit(src, state);
                    strLen = (strLen << 1) + state.carry;
                    readCtrlBit(src, state);
                } while (state.carry != 0);

                strLen -= 3;
                int strOffset = 0;
                if (strLen < 0) {
                    strOffset = previousPtr;
                    strLen++;
                } else {
                    int nextByte = src[state.srcIdx++] & 0xFF;
                    strOffset = (strLen << 8) | nextByte;
                    strLen = 0;
                    strOffset = ~strOffset;
                    if (strOffset < -1280) {
                        ebp++;
                    }
                    ebp++;
                    if (strOffset < -32000) {
                        ebp++;
                    }
                    previousPtr = strOffset;
                }

                // read next 2 bits
                readCtrlBit(src, state);
                strLen = (strLen << 1) + state.carry;
                readCtrlBit(src, state);
                strLen = (strLen << 1) + state.carry;

                if (strLen == 0) {
                    // DECODE_CTRL_BITS
                    strLen++;
                    do {
                        readCtrlBit(src, state);
                        strLen = (strLen << 1) + state.carry;
                        readCtrlBit(src, state);
                    } while (state.carry != 0);
                    strLen += 2;
                }

                strLen += ebp;
                if (size >= strLen && strLen > 0) {
                    int stringIdx = dstIdx + strOffset;
                    if (strOffset >= 0 || stringIdx < 0) {
                        break; // corrupted
                    }
                    size -= strLen;
                    for (; strLen > 0; strLen--) {
                        dst[dstIdx++] = dst[stringIdx++];
                    }
                } else {
                    break; // malformed
                }
            }
        }
        srcOffset[0] = state.srcIdx;
        return dst;
    }

    public static void unpackDelta8(byte[] src, int[] srcOffset, long[] dstL, long[] dstR, int length, int numChannels, boolean prediction) {
        BitState state = new BitState();
        state.srcIdx = srcOffset[0];

        int dh = 4;
        int cl;
        int previous = 0; // int8 semantics, carried over between channels like OpenMPT
        int next = 0;

        for (int chn = 0; chn < numChannels; chn++) {
            long[] dst = (chn == 0) ? dstL : dstR;

            for (int s = 0; s < length; s++) {
                int val = 0;
                // Decode - all intermediate values are uint8 in the reference code
                do {
                    readCtrlBit(src, state);
                    val = ((val << 1) + state.carry) & 0xFF;
                    readCtrlBit(src, state);
                } while (state.carry != 0);

                cl = dh;
                for (int i = 0; i < cl; i++) {
                    readCtrlBit(src, state);
                    val = ((val << 1) + state.carry) & 0xFF;
                }

                cl = 1;
                if (val >= 4) {
                    cl = 7;
                    while (((1 << cl) & val) == 0 && cl > 1) {
                        cl--;
                    }
                }
                dh = dh + cl;
                dh >>= 1;
                state.carry = val & 1;
                val >>= 1;
                if (state.carry == 0) {
                    val = (~val) & 0xFF;
                }

                if (prediction) {
                    int delta = (byte) val;
                    val = (val + (next & 0xFF)) & 0xFF;
                    dst[s] = ModConstants.promoteSigned8BitToSigned32Bit((byte) val);
                    int sval = (byte) val;
                    next = (sval * 2) + (delta >> 1) - previous;
                    if (next > 127) next = 127;
                    else if (next < -128) next = -128;
                    previous = sval;
                } else {
                    val = (val + previous) & 0xFF;
                    dst[s] = ModConstants.promoteSigned8BitToSigned32Bit((byte) val);
                    previous = (byte) val;
                }
            }
        }
        srcOffset[0] = state.srcIdx;
    }

    public static void unpackDelta16(byte[] src, int[] srcOffset, long[] dstL, long[] dstR, int length, int numChannels, boolean prediction) {
        BitState state = new BitState();
        state.srcIdx = srcOffset[0];

        int dh = 8;
        int cl;
        int previous = 0; // int16 semantics, carried over between channels like OpenMPT
        int next = 0;

        for (int chn = 0; chn < numChannels; chn++) {
            long[] dst = (chn == 0) ? dstL : dstR;

            for (int s = 0; s < length; s++) {
                int val = 0;
                // Decode - all intermediate values are uint16 in the reference code
                if (dh < 5) {
                    do {
                        readCtrlBit(src, state);
                        val = ((val << 1) + state.carry) & 0xFFFF;
                        readCtrlBit(src, state);
                        val = ((val << 1) + state.carry) & 0xFFFF;
                        readCtrlBit(src, state);
                    } while (state.carry != 0);
                } else {
                    do {
                        readCtrlBit(src, state);
                        val = ((val << 1) + state.carry) & 0xFFFF;
                        readCtrlBit(src, state);
                    } while (state.carry != 0);
                }

                cl = dh;
                for (int i = 0; i < cl; i++) {
                    readCtrlBit(src, state);
                    val = ((val << 1) + state.carry) & 0xFFFF;
                }

                cl = 1;
                if (val >= 4) {
                    cl = 15;
                    while (((1 << cl) & val) == 0 && cl > 1) {
                        cl--;
                    }
                }
                dh = dh + cl;
                dh >>= 1;
                state.carry = val & 1;
                val >>= 1;
                if (state.carry == 0) {
                    val = (~val) & 0xFFFF;
                }

                if (prediction) {
                    int delta = (short) val;
                    val = (val + (next & 0xFFFF)) & 0xFFFF;
                    dst[s] = ModConstants.promoteSigned16BitToSigned32Bit((short) val);
                    int sval = (short) val;
                    next = (sval * 2) + (delta >> 1) - previous;
                    if (next > 32767) next = 32767;
                    else if (next < -32768) next = -32768;
                    previous = sval;
                } else {
                    val = (val + previous) & 0xFFFF;
                    dst[s] = ModConstants.promoteSigned16BitToSigned32Bit((short) val);
                    previous = (short) val;
                }
            }
        }
        srcOffset[0] = state.srcIdx;
    }
}
