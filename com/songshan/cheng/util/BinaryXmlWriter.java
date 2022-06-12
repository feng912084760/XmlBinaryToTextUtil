package com.example.mytestapplication.util;

import static org.xmlpull.v1.XmlPullParser.CDSECT;
import static org.xmlpull.v1.XmlPullParser.COMMENT;
import static org.xmlpull.v1.XmlPullParser.DOCDECL;
import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.ENTITY_REF;
import static org.xmlpull.v1.XmlPullParser.IGNORABLE_WHITESPACE;
import static org.xmlpull.v1.XmlPullParser.PROCESSING_INSTRUCTION;
import static org.xmlpull.v1.XmlPullParser.START_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.START_TAG;
import static org.xmlpull.v1.XmlPullParser.TEXT;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class BinaryXmlWriter  implements XmlSerializer {
    public static final byte[] PROTOCOL_MAGIC_VERSION_0 = new byte[] { 0x41, 0x42, 0x58, 0x00 };


    static final int ATTRIBUTE = 15;

    static final int TYPE_NULL = 1 << 4;
    static final int TYPE_STRING = 2 << 4;
    static final int TYPE_STRING_INTERNED = 3 << 4;
    static final int TYPE_BYTES_HEX = 4 << 4;
    static final int TYPE_BYTES_BASE64 = 5 << 4;
    static final int TYPE_INT = 6 << 4;
    static final int TYPE_INT_HEX = 7 << 4;
    static final int TYPE_LONG = 8 << 4;
    static final int TYPE_LONG_HEX = 9 << 4;
    static final int TYPE_FLOAT = 10 << 4;
    static final int TYPE_DOUBLE = 11 << 4;
    static final int TYPE_BOOLEAN_TRUE = 12 << 4;
    static final int TYPE_BOOLEAN_FALSE = 13 << 4;

    private static final int BUFFER_SIZE = 32_768;

    private FastDataOutput mOut;

    private int mTagCount = 0;
    private String[] mTagNames;
    @Override
    public void setOutput(@NonNull OutputStream os, @Nullable String encoding) throws IOException {
        if (encoding != null && !StandardCharsets.UTF_8.name().equalsIgnoreCase(encoding)) {
            throw new UnsupportedOperationException();
        }

        mOut = new FastDataOutput(os, BUFFER_SIZE);
        mOut.write(PROTOCOL_MAGIC_VERSION_0);

        mTagCount = 0;
        mTagNames = new String[8];
    }
    @Override
    public void setOutput(Writer writer) {
        throw new UnsupportedOperationException();
    }
    /**
     * Write the given token and optional {@link String} into our buffer.
     */
    private void writeToken(int token, @Nullable String text) throws IOException {
        if (text != null) {
            mOut.writeByte(token | TYPE_STRING);
            mOut.writeUTF(text);
        } else {
            mOut.writeByte(token | TYPE_NULL);
        }
    }

    public void flush() throws IOException {
        mOut.flush();
    }

    public void startDocument(@Nullable String encoding, @Nullable Boolean standalone)
            throws IOException {
        if (encoding != null && !StandardCharsets.UTF_8.name().equalsIgnoreCase(encoding)) {
            throw new UnsupportedOperationException();
        }
        if (standalone != null && !standalone) {
            throw new UnsupportedOperationException();
        }
        mOut.writeByte(START_DOCUMENT | TYPE_NULL);
    }

    public void endDocument() throws IOException {
        mOut.writeByte(END_DOCUMENT | TYPE_NULL);
        flush();
    }

    public int getDepth() {
        return mTagCount;
    }

    public String getNamespace() {
        // Namespaces are unsupported
        return XmlPullParser.NO_NAMESPACE;
    }

    public String getName() {
        return mTagNames[mTagCount - 1];
    }

    public XmlSerializer startTag(String namespace, String name) throws IOException {
        if (namespace != null && !namespace.isEmpty()) throw illegalNamespace();
        if (mTagCount == mTagNames.length) {
            mTagNames = Arrays.copyOf(mTagNames, mTagCount + (mTagCount >> 1));
        }
        mTagNames[mTagCount++] = name;
        mOut.writeByte(START_TAG | TYPE_STRING_INTERNED);
        mOut.writeInternedUTF(name);
        return this;
    }

    public XmlSerializer endTag(String namespace, String name) throws IOException {
        if (namespace != null && !namespace.isEmpty()) throw illegalNamespace();
        mTagCount--;
        mOut.writeByte(END_TAG | TYPE_STRING_INTERNED);
        mOut.writeInternedUTF(name);
        return this;
    }

    public XmlSerializer attribute(String namespace, String name, String value) throws IOException {
        if (namespace != null && !namespace.isEmpty()) throw illegalNamespace();
        mOut.writeByte(ATTRIBUTE | TYPE_STRING);
        mOut.writeInternedUTF(name);
        mOut.writeUTF(value);
        return this;
    }

    public XmlSerializer attributeInterned(String namespace, String name, String value)
            throws IOException {
        if (namespace != null && !namespace.isEmpty()) throw illegalNamespace();
        mOut.writeByte(ATTRIBUTE | TYPE_STRING_INTERNED);
        mOut.writeInternedUTF(name);
        mOut.writeInternedUTF(value);
        return this;
    }


    public XmlSerializer attributeBytesHex(String namespace, String name, byte[] value)
            throws IOException {
        if (namespace != null && !namespace.isEmpty()) throw illegalNamespace();
        mOut.writeByte(ATTRIBUTE | TYPE_BYTES_HEX);
        mOut.writeInternedUTF(name);
        mOut.writeShort(value.length);
        mOut.write(value);
        return this;
    }


    public XmlSerializer attributeBytesBase64(String namespace, String name, byte[] value)
            throws IOException {
        if (namespace != null && !namespace.isEmpty()) throw illegalNamespace();
        mOut.writeByte(ATTRIBUTE | TYPE_BYTES_BASE64);
        mOut.writeInternedUTF(name);
        mOut.writeShort(value.length);
        mOut.write(value);
        return this;
    }


    public XmlSerializer attributeInt(String namespace, String name, int value)
            throws IOException {
        if (namespace != null && !namespace.isEmpty()) throw illegalNamespace();
        mOut.writeByte(ATTRIBUTE | TYPE_INT);
        mOut.writeInternedUTF(name);
        mOut.writeInt(value);
        return this;
    }


    public XmlSerializer attributeIntHex(String namespace, String name, int value)
            throws IOException {
        if (namespace != null && !namespace.isEmpty()) throw illegalNamespace();
        mOut.writeByte(ATTRIBUTE | TYPE_INT_HEX);
        mOut.writeInternedUTF(name);
        mOut.writeInt(value);
        return this;
    }


    public XmlSerializer attributeLong(String namespace, String name, long value)
            throws IOException {
        if (namespace != null && !namespace.isEmpty()) throw illegalNamespace();
        mOut.writeByte(ATTRIBUTE | TYPE_LONG);
        mOut.writeInternedUTF(name);
        mOut.writeLong(value);
        return this;
    }


    public XmlSerializer attributeLongHex(String namespace, String name, long value)
            throws IOException {
        if (namespace != null && !namespace.isEmpty()) throw illegalNamespace();
        mOut.writeByte(ATTRIBUTE | TYPE_LONG_HEX);
        mOut.writeInternedUTF(name);
        mOut.writeLong(value);
        return this;
    }


    public XmlSerializer attributeFloat(String namespace, String name, float value)
            throws IOException {
        if (namespace != null && !namespace.isEmpty()) throw illegalNamespace();
        mOut.writeByte(ATTRIBUTE | TYPE_FLOAT);
        mOut.writeInternedUTF(name);
        mOut.writeFloat(value);
        return this;
    }


    public XmlSerializer attributeDouble(String namespace, String name, double value)
            throws IOException {
        if (namespace != null && !namespace.isEmpty()) throw illegalNamespace();
        mOut.writeByte(ATTRIBUTE | TYPE_DOUBLE);
        mOut.writeInternedUTF(name);
        mOut.writeDouble(value);
        return this;
    }


    public XmlSerializer attributeBoolean(String namespace, String name, boolean value)
            throws IOException {
        if (namespace != null && !namespace.isEmpty()) throw illegalNamespace();
        if (value) {
            mOut.writeByte(ATTRIBUTE | TYPE_BOOLEAN_TRUE);
            mOut.writeInternedUTF(name);
        } else {
            mOut.writeByte(ATTRIBUTE | TYPE_BOOLEAN_FALSE);
            mOut.writeInternedUTF(name);
        }
        return this;
    }

    @Override
    public XmlSerializer text(char[] buf, int start, int len) throws IOException {
        writeToken(TEXT, new String(buf, start, len));
        return this;
    }

    @Override
    public XmlSerializer text(String text) throws IOException {
        writeToken(TEXT, text);
        return this;
    }

    @Override
    public void cdsect(String text) throws IOException {
        writeToken(CDSECT, text);
    }

    @Override
    public void entityRef(String text) throws IOException {
        writeToken(ENTITY_REF, text);
    }

    @Override
    public void processingInstruction(String text) throws IOException {
        writeToken(PROCESSING_INSTRUCTION, text);
    }

    @Override
    public void comment(String text) throws IOException {
        writeToken(COMMENT, text);
    }

    @Override
    public void docdecl(String text) throws IOException {
        writeToken(DOCDECL, text);
    }

    @Override
    public void ignorableWhitespace(String text) throws IOException {
        writeToken(IGNORABLE_WHITESPACE, text);
    }

    @Override
    public void setFeature(String name, boolean state) {
        // Quietly handle no-op features
        if ("http://xmlpull.org/v1/doc/features.html#indent-output".equals(name)) {
            return;
        }
        // Features are not supported
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getFeature(String name) {
        // Features are not supported
        throw new UnsupportedOperationException();
    }

    @Override
    public void setProperty(String name, Object value) {
        // Properties are not supported
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getProperty(String name) {
        // Properties are not supported
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPrefix(String prefix, String namespace) {
        // Prefixes are not supported
        throw new UnsupportedOperationException();
    }

    @Override
    public String getPrefix(String namespace, boolean generatePrefix) {
        // Prefixes are not supported
        throw new UnsupportedOperationException();
    }
    private static IllegalArgumentException illegalNamespace() {
        throw new IllegalArgumentException("Namespaces are not supported");
    }
}
