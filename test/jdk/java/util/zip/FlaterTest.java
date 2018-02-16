/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @bug 6348045
 * @summary GZipOutputStream/InputStream goes critical(calls JNI_Get*Critical)
 * and causes slowness.  This test uses Deflater and Inflater directly.
 * @key randomness
 */

import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.zip.*;

/**
 * This test runs Inflater and Defalter in a number of simultaneous threads,
 * validating that the deflated & then inflated data matches the original
 * data.
 */
public class FlaterTest extends Thread {
    private static final int DATA_LEN = 1024 * 128;
    private static ByteBuffer data;

    // If true, print extra info.
    private static final boolean debug = false;

    // Set of Flater threads running.
    private static Set flaters =
        Collections.synchronizedSet(new HashSet());

    /** Fill in {@code data} with random values. */
    static void createData(boolean direct) {
        ByteBuffer bb = direct ? ByteBuffer.allocateDirect(DATA_LEN * 8) : ByteBuffer.allocate(DATA_LEN * 8);
        for (int i = 0; i < DATA_LEN * 8; i += 8) {
            bb.putDouble(i, Math.random());
        }
        data = bb;
        if (debug) System.out.println("data length is " + data.capacity());
    }

    /** @return the length of the deflated {@code data}. */
    private static int getDeflatedLength() throws Throwable {
        int rc = 0;
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        byte[] out = new byte[data.capacity()];
        rc = deflater.deflate(out);
        deflater.end();
        if (debug) System.out.println("deflatedLength is " + rc);
        return rc;
    }

    /** Compares given bytes with those in {@code data}.
     * @throws Exception if given bytes don't match {@code data}.
     */
    private static void validate(ByteBuffer buf, int offset, int len) throws Exception {
        for (int i = 0; i < len; i++ ) {
            if (buf.get(i) != data.get(offset+i)) {
                throw new Exception("mismatch at " + (offset + i));
            }
        }
    }

    public static void realMain(String[] args) throws Throwable {
        int numThreads = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        createData(false);
        new FlaterTest().go(numThreads, false);
        new FlaterTest().go(numThreads, true);
        createData(true);
        new FlaterTest().go(numThreads, false);
        new FlaterTest().go(numThreads, true);
    }

    private synchronized void go(int numThreads, boolean direct) throws Throwable {
        int deflatedLength = getDeflatedLength();

        long time = System.currentTimeMillis();
        for (int i = 0; i < numThreads; i++) {
            Flater f = new Flater(deflatedLength, direct);
            flaters.add(f);
            f.start();
        }
        synchronized (flaters) {
            while (flaters.size() != 0) {
                try {
                    flaters.wait();
                } catch (InterruptedException ex) {
                    unexpected(ex);
                }
            }
        }
        time = System.currentTimeMillis() - time;
        System.out.println("Time needed for " + numThreads
                           + " threads to deflate/inflate: " + time + " ms.");
    }

    /** Deflates and inflates data. */
    static class Flater extends Thread {
        private final int deflatedLength;
        private final boolean direct;

        private Flater(int length, final boolean direct) {
            this.deflatedLength = length;
            this.direct = direct;
        }

        /** Deflates and inflates {@code data}. */
        public void run() {
            if (debug) System.out.println(getName() + " starting run()");
            try {
                ByteBuffer deflated = DeflateData(deflatedLength);
                InflateData(deflated);
            } catch (Throwable t) {
                t.printStackTrace();
                fail(getName() + " failed");
            } finally {
                synchronized (flaters) {
                    flaters.remove(this);
                    if (flaters.isEmpty()) {
                        flaters.notifyAll();
                    }
                }
            }
        }

        /** Returns a copy of {@code data} in deflated form. */
        private ByteBuffer DeflateData(int length) throws Throwable {
            Deflater deflater = new Deflater();
            data.clear();
            deflater.setInput(data);
            deflater.finish();
            ByteBuffer out = direct ? ByteBuffer.allocateDirect(length) : ByteBuffer.allocate(length);
            deflater.deflate(out);
            out.flip();
            return out;
        }

        /** Inflate a byte array, comparing it with {@code data} during
         * inflation.
         * @throws Exception if inflated bytes don't match {@code data}.
         */
        private void InflateData(ByteBuffer bytes) throws Throwable {
            Inflater inflater = new Inflater();
            inflater.setInput(bytes);
            int len = 1024 * 8;
            int offset = 0;
            ByteBuffer buf = direct ? ByteBuffer.allocateDirect(len) : ByteBuffer.allocate(len);
            while (inflater.getRemaining() > 0) {
                buf.clear();
                int inflated = inflater.inflate(buf);
                validate(buf, offset, inflated);
                offset += inflated;
            }
        }
    }

    //--------------------- Infrastructure ---------------------------
    static volatile int passed = 0, failed = 0;
    static void pass() {passed++;}
    static void fail() {failed++; Thread.dumpStack();}
    static void fail(String msg) {System.out.println(msg); fail();}
    static void unexpected(Throwable t) {failed++; t.printStackTrace();}
    static void check(boolean cond) {if (cond) pass(); else fail();}
    static void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        try {realMain(args);} catch (Throwable t) {unexpected(t);}
        System.out.println("\nPassed = " + passed + " failed = " + failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}
