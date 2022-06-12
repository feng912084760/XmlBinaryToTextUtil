package com.example.mytestapplication.util;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.example.mytestapplication.util.FastDataInput;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Objects;

/**
 * Optimized implementation of {@link DataOutput} which buffers data in memory
 * before flushing to the underlying {@link OutputStream}.
 * <p>
 * Benchmarks have demonstrated this class is 2x more efficient than using a
 * {@link DataOutputStream} with a {@link BufferedOutputStream}.
 */
public class FastDataOutput implements DataOutput, Flushable, Closeable {
    private static final int MAX_UNSIGNED_SHORT = 65_535;

//    private final VMRuntime mRuntime;
    private final OutputStream mOut;

    private final byte[] mBuffer;
//    private final long mBufferPtr;
    private final int mBufferCap;

    private int mBufferPos;

    /**
     * Values that have been "interned" by {@link #writeInternedUTF(String)}.
     */
    private HashMap<String, Short> mStringRefs = new HashMap<>();

    public FastDataOutput(@NonNull OutputStream out, int bufferSize) {
//        mRuntime = VMRuntime.getRuntime();
        mOut = Objects.requireNonNull(out);
        if (bufferSize < 8) {
            throw new IllegalArgumentException();
        }

//        mBuffer = (byte[]) mRuntime.newNonMovableArray(byte.class, bufferSize);
        mBuffer = new byte[bufferSize];
//        mBufferPtr = mRuntime.addressOf(mBuffer);
        mBufferCap = mBuffer.length;
    }

    private void drain() throws IOException {
        if (mBufferPos > 0) {
            mOut.write(mBuffer, 0, mBufferPos);
            mBufferPos = 0;
        }
    }

    @Override
    public void flush() throws IOException {
        drain();
        mOut.flush();
    }

    @Override
    public void close() throws IOException {
        mOut.close();
    }

    @Override
    public void write(int b) throws IOException {
        writeByte(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (mBufferCap < len) {
            drain();
            mOut.write(b, off, len);
        } else {
            if (mBufferCap - mBufferPos < len) drain();
            System.arraycopy(b, off, mBuffer, mBufferPos, len);
            mBufferPos += len;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public void writeUTF(String s) throws IOException {
        // Attempt to write directly to buffer space if there's enough room,
        // otherwise fall back to chunking into place
        if (mBufferCap - mBufferPos < 2 + s.length()) drain();

        // Magnitude of this returned value indicates the number of bytes
        // required to encode the string; sign indicates success/failure
//        int len = CharsetUtils.toModifiedUtf8Bytes(s, mBufferPtr, mBufferPos + 2, mBufferCap);
        byte[] tmp = s.getBytes(StandardCharsets.UTF_8);
        int len = tmp.length;
        if (Math.abs(len) > MAX_UNSIGNED_SHORT) {
            throw new IOException("Modified UTF-8 length too large: " + len);
        }

        if (len >= 0) {
            // Positive value indicates the string was encoded into the buffer
            // successfully, so we only need to prefix with length
            writeShort(len);
            write(tmp, 0, len);
//            mBufferPos += len;
        } else { // 认为异常了
            // Negative value indicates buffer was too small and we need to
            // allocate a temporary buffer for encoding
//            len = -len;
//            final byte[] tmp = (byte[]) mRuntime.newNonMovableArray(byte.class, len + 1);
//            CharsetUtils.toModifiedUtf8Bytes(s, mRuntime.addressOf(tmp), 0, tmp.length);
//            writeShort(len);
//            write(tmp, 0, len);
        }
    }

    /**
     * Write a {@link String} value with the additional signal that the given
     * value is a candidate for being canonicalized, similar to
     * {@link String#intern()}.
     * <p>
     * Canonicalization is implemented by writing each unique string value once
     * the first time it appears, and then writing a lightweight {@code short}
     * reference when that string is written again in the future.
     *
     * @see FastDataInput#readInternedUTF()
     */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void writeInternedUTF(@NonNull String s) throws IOException {
        Short ref = mStringRefs.get(s);
        if (ref != null) {
            writeShort(ref);
        } else {
            writeShort(MAX_UNSIGNED_SHORT);
            writeUTF(s);

            // We can only safely intern when we have remaining values; if we're
            // full we at least sent the string value above
            ref = (short) mStringRefs.size();
            if (ref < MAX_UNSIGNED_SHORT) {
                mStringRefs.put(s, ref);
            }
        }
    }

    @Override
    public void writeBoolean(boolean v) throws IOException {
        writeByte(v ? 1 : 0);
    }

    @Override
    public void writeByte(int v) throws IOException {
        if (mBufferCap - mBufferPos < 1) drain();
        mBuffer[mBufferPos++] = (byte) ((v >>  0) & 0xff);
    }

    @Override
    public void writeShort(int v) throws IOException {
        if (mBufferCap - mBufferPos < 2) drain();
        mBuffer[mBufferPos++] = (byte) ((v >>  8) & 0xff);
        mBuffer[mBufferPos++] = (byte) ((v >>  0) & 0xff);
    }

    @Override
    public void writeChar(int v) throws IOException {
        writeShort((short) v);
    }

    @Override
    public void writeInt(int v) throws IOException {
        if (mBufferCap - mBufferPos < 4) drain();
        mBuffer[mBufferPos++] = (byte) ((v >> 24) & 0xff);
        mBuffer[mBufferPos++] = (byte) ((v >> 16) & 0xff);
        mBuffer[mBufferPos++] = (byte) ((v >>  8) & 0xff);
        mBuffer[mBufferPos++] = (byte) ((v >>  0) & 0xff);
    }

    @Override
    public void writeLong(long v) throws IOException {
        if (mBufferCap - mBufferPos < 8) drain();
        int i = (int) (v >> 32);
        mBuffer[mBufferPos++] = (byte) ((i >> 24) & 0xff);
        mBuffer[mBufferPos++] = (byte) ((i >> 16) & 0xff);
        mBuffer[mBufferPos++] = (byte) ((i >>  8) & 0xff);
        mBuffer[mBufferPos++] = (byte) ((i >>  0) & 0xff);
        i = (int) v;
        mBuffer[mBufferPos++] = (byte) ((i >> 24) & 0xff);
        mBuffer[mBufferPos++] = (byte) ((i >> 16) & 0xff);
        mBuffer[mBufferPos++] = (byte) ((i >>  8) & 0xff);
        mBuffer[mBufferPos++] = (byte) ((i >>  0) & 0xff);
    }

    @Override
    public void writeFloat(float v) throws IOException {
        writeInt(Float.floatToIntBits(v));
    }

    @Override
    public void writeDouble(double v) throws IOException {
        writeLong(Double.doubleToLongBits(v));
    }

    @Override
    public void writeBytes(String s) throws IOException {
        // Callers should use writeUTF()
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeChars(String s) throws IOException {
        // Callers should use writeUTF()
        throw new UnsupportedOperationException();
    }
}