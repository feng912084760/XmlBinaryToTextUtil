package com.example.mytestapplication.util;

import static com.example.mytestapplication.util.BinaryXmlWriter.PROTOCOL_MAGIC_VERSION_0;
import static com.example.mytestapplication.util.BinaryXmlWriter.ATTRIBUTE;
import static com.example.mytestapplication.util.BinaryXmlWriter.TYPE_BOOLEAN_FALSE;
import static com.example.mytestapplication.util.BinaryXmlWriter.TYPE_BOOLEAN_TRUE;
import static com.example.mytestapplication.util.BinaryXmlWriter.TYPE_BYTES_BASE64;
import static com.example.mytestapplication.util.BinaryXmlWriter.TYPE_BYTES_HEX;
import static com.example.mytestapplication.util.BinaryXmlWriter.TYPE_DOUBLE;
import static com.example.mytestapplication.util.BinaryXmlWriter.TYPE_FLOAT;
import static com.example.mytestapplication.util.BinaryXmlWriter.TYPE_INT;
import static com.example.mytestapplication.util.BinaryXmlWriter.TYPE_INT_HEX;
import static com.example.mytestapplication.util.BinaryXmlWriter.TYPE_LONG;
import static com.example.mytestapplication.util.BinaryXmlWriter.TYPE_LONG_HEX;
import static com.example.mytestapplication.util.BinaryXmlWriter.TYPE_NULL;
import static com.example.mytestapplication.util.BinaryXmlWriter.TYPE_STRING;
import static com.example.mytestapplication.util.BinaryXmlWriter.TYPE_STRING_INTERNED;

import static org.xmlpull.v1.XmlPullParser.CDSECT;
import static org.xmlpull.v1.XmlPullParser.COMMENT;
import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.ENTITY_REF;
import static org.xmlpull.v1.XmlPullParser.NO_NAMESPACE;
import static org.xmlpull.v1.XmlPullParser.PROCESSING_INSTRUCTION;
import static org.xmlpull.v1.XmlPullParser.START_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.START_TAG;
import static org.xmlpull.v1.XmlPullParser.TEXT;

import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class BinaryXmlReader {

//    int START_DOCUMENT = 0,
//            END_DOCUMENT = 1,
//            START_TAG = 2,
//            END_TAG = 3,
//            TEXT = 4,
//            CDSECT = 5,
//            ENTITY_REF = 6,
//            IGNORABLE_WHITESPACE = 7,
//            PROCESSING_INSTRUCTION = 8,
//            COMMENT = 9,
//            DOCDECL = 10;


    private static final int BUFFER_SIZE = 32_768;

    private FastDataInput mIn;

    private int mCurrentToken = START_DOCUMENT;
    private int mCurrentDepth = 0;
    private String mCurrentName;
    private String mCurrentText;

    private int mAttributeCount = 0;
    private Attribute[] mAttributes;

    public void setInput(InputStream is, String encoding) throws XmlPullParserException {
        if (encoding != null && !"UTF-8".equalsIgnoreCase(encoding)) {
            throw new UnsupportedOperationException();
        }

        mIn = new FastDataInput(is, BUFFER_SIZE);

        mCurrentToken = START_DOCUMENT;
        mCurrentDepth = 0;
        mCurrentName = null;
        mCurrentText = null;

        mAttributeCount = 0;
        mAttributes = new Attribute[8];
        for (int i = 0; i < mAttributes.length; i++) {
            mAttributes[i] = new Attribute();
        }

        try {
            final byte[] magic = new byte[4];
            mIn.readFully(magic);
            if (!Arrays.equals(magic, PROTOCOL_MAGIC_VERSION_0)) {
                throw new IOException("Unexpected magic " + bytesToHexString(magic));
            }

            // We're willing to immediately consume a START_DOCUMENT if present,
            // but we're okay if it's missing
            if (peekNextExternalToken() == START_DOCUMENT) {
                consumeToken();
            }
        } catch (IOException e) {
            throw new XmlPullParserException(e.toString());
        }
    }

    public int next() throws XmlPullParserException, IOException {
        while (true) {
            final int token = nextToken();
            switch (token) {
                case START_TAG:
                case END_TAG:
                case END_DOCUMENT:
                    return token;
                case TEXT:
                    consumeAdditionalText();
                    // Per interface docs, empty text regions are skipped
                    if (mCurrentText == null || mCurrentText.length() == 0) {
                        continue;
                    } else {
                        return TEXT;
                    }
            }
        }
    }
    public int nextToken() throws XmlPullParserException, IOException {
        if (mCurrentToken == END_TAG) {
            mCurrentDepth--;
        }

        int token;
        try {
            token = peekNextExternalToken();
            consumeToken();
        } catch (EOFException e) {
            token = END_DOCUMENT;
        }
        switch (token) {
            case START_TAG:
                // We need to peek forward to find the next external token so
                // that we parse all pending INTERNAL_ATTRIBUTE tokens
                peekNextExternalToken();
                mCurrentDepth++;
                break;
        }
        mCurrentToken = token;
        return token;
    }
    private int peekNextExternalToken() throws IOException, XmlPullParserException {
        while (true) {
            final int token = peekNextToken();
            switch (token) {
                case ATTRIBUTE:
                    consumeToken();
                    continue;
                default:
                    return token;
            }
        }
    }

    /**
     * Peek at the next token in the underlying stream without consuming it.
     */
    private int peekNextToken() throws IOException {
        return mIn.peekByte() & 0x0f;
    }

    /**
     * Parse and consume the next token in the underlying stream.
     */
    private void consumeToken() throws IOException, XmlPullParserException {
        final int event = mIn.readByte();
        final int token = event & 0x0f;
        final int type = event & 0xf0;
        switch (token) {
            case ATTRIBUTE: {
                final Attribute attr = obtainAttribute();
                attr.name = mIn.readInternedUTF();
                attr.type = type;
                switch (type) {
                    case TYPE_NULL:
                    case TYPE_BOOLEAN_TRUE:
                    case TYPE_BOOLEAN_FALSE:
                        // Nothing extra to fill in
                        break;
                    case TYPE_STRING:
                        attr.valueString = mIn.readUTF();
                        break;
                    case TYPE_STRING_INTERNED:
                        attr.valueString = mIn.readInternedUTF();
                        break;
                    case TYPE_BYTES_HEX:
                    case TYPE_BYTES_BASE64:
                        final int len = mIn.readUnsignedShort();
                        final byte[] res = new byte[len];
                        mIn.readFully(res);
                        attr.valueBytes = res;
                        break;
                    case TYPE_INT:
                    case TYPE_INT_HEX:
                        attr.valueInt = mIn.readInt();
                        break;
                    case TYPE_LONG:
                    case TYPE_LONG_HEX:
                        attr.valueLong = mIn.readLong();
                        break;
                    case TYPE_FLOAT:
                        attr.valueFloat = mIn.readFloat();
                        break;
                    case TYPE_DOUBLE:
                        attr.valueDouble = mIn.readDouble();
                        break;
                    default:
                        throw new IOException("Unexpected data type " + type);
                }
                break;
            }
            case START_DOCUMENT:
            case END_DOCUMENT: {
                mCurrentName = null;
                mCurrentText = null;
                if (mAttributeCount > 0) resetAttributes();
                break;
            }
            case START_TAG:
            case END_TAG: {
                mCurrentName = mIn.readInternedUTF();
                mCurrentText = null;
                if (mAttributeCount > 0) resetAttributes();
                break;
            }
            case TEXT:
            case CDSECT:
            case PROCESSING_INSTRUCTION:
            case COMMENT:
            case XmlPullParser.DOCDECL:
            case XmlPullParser.IGNORABLE_WHITESPACE: {
                mCurrentName = null;
                mCurrentText = mIn.readUTF();
                if (mAttributeCount > 0) resetAttributes();
                break;
            }
            case ENTITY_REF: {
                mCurrentName = mIn.readUTF();
                mCurrentText = resolveEntity(mCurrentName);
                if (mAttributeCount > 0) resetAttributes();
                break;
            }
            default: {
                throw new IOException("Unknown token " + token + " with type " + type);
            }
        }
    }

    private void consumeAdditionalText() throws IOException, XmlPullParserException {
        String combinedText = mCurrentText;
        while (true) {
            final int token = peekNextExternalToken();
            switch (token) {
                case COMMENT:
                case PROCESSING_INSTRUCTION:
                    // Quietly consumed
                    consumeToken();
                    break;
                case TEXT:
                case CDSECT:
                case ENTITY_REF:
                    // Additional text regions collected
                    consumeToken();
                    combinedText += mCurrentText;
                    break;
                default:
                    // Next token is something non-text, so wrap things up
                    mCurrentToken = TEXT;
                    mCurrentName = null;
                    mCurrentText = combinedText;
                    return;
            }
        }
    }
    private @NonNull Attribute obtainAttribute() {
        if (mAttributeCount == mAttributes.length) {
            final int before = mAttributes.length;
            final int after = before + (before >> 1);
            mAttributes = Arrays.copyOf(mAttributes, after);
            for (int i = before; i < after; i++) {
                mAttributes[i] = new Attribute();
            }
        }
        return mAttributes[mAttributeCount++];
    }
    /**
     * Clear any {@link Attribute} instances that have been allocated by
     * {@link #obtainAttribute()}, returning them into the pool for recycling.
     */
    private void resetAttributes() {
        for (int i = 0; i < mAttributeCount; i++) {
            mAttributes[i].reset();
        }
        mAttributeCount = 0;
    }

    static @NonNull String resolveEntity(@NonNull String entity)
            throws XmlPullParserException {
        switch (entity) {
            case "lt": return "<";
            case "gt": return ">";
            case "amp": return "&";
            case "apos": return "'";
            case "quot": return "\"";
        }
        if (entity.length() > 1 && entity.charAt(0) == '#') {
            final char c = (char) Integer.parseInt(entity.substring(1));
            return new String(new char[] { c });
        }
        throw new XmlPullParserException("Unknown entity " + entity);
    }
    public int getDepth() {
        return mCurrentDepth;
    }
    public String getName() {
        return mCurrentName;
    }
    public String getText() {
        return mCurrentText;
    }
    public int getAttributeCount() {
        return mAttributeCount;
    }
    public String getAttributeNamespace(int index) {
        // Namespaces are unsupported
        return NO_NAMESPACE;
    }

    public String getAttributeName(int index) {
        return mAttributes[index].name;
    }
    public String getAttributeValueString(int index) {
        return mAttributes[index].getValueString();
    }
    public String getAttributePrefix(int index) {
        // Prefixes are not supported
        return null;
    }

    public int getAttributeType(int index) {
        // Validation is not supported
        return mAttributes[index].type;
    }

    public boolean isAttributeDefault(int index) {
        // Validation is not supported
        return false;
    }

    public int getEventType() throws XmlPullParserException {
        return mCurrentToken;
    }
    /**
     * Holder representing a single attribute. This design enables object
     * recycling without resorting to autoboxing.
     * <p>
     * To support conversion between human-readable XML and binary XML, the
     * various accessor methods will transparently convert from/to
     * human-readable values when needed.
     */
    private static class Attribute {
        public String name;
        public int type;

        public String valueString;
        public byte[] valueBytes;
        public int valueInt;
        public long valueLong;
        public float valueFloat;
        public double valueDouble;

        public void reset() {
            name = null;
            valueString = null;
            valueBytes = null;
        }

        public @Nullable String getValueString() {
            switch (type) {
                case TYPE_NULL:
                    return null;
                case TYPE_STRING:
                case TYPE_STRING_INTERNED:
                    return valueString;
                case TYPE_BYTES_HEX:
                    return bytesToHexString(valueBytes);
                case TYPE_BYTES_BASE64:
                    return Base64.encodeToString(valueBytes, Base64.NO_WRAP);
                case TYPE_INT:
                    return Integer.toString(valueInt);
                case TYPE_INT_HEX:
                    return Integer.toString(valueInt, 16);
                case TYPE_LONG:
                    return Long.toString(valueLong);
                case TYPE_LONG_HEX:
                    return Long.toString(valueLong, 16);
                case TYPE_FLOAT:
                    return Float.toString(valueFloat);
                case TYPE_DOUBLE:
                    return Double.toString(valueDouble);
                case TYPE_BOOLEAN_TRUE:
                    return "true";
                case TYPE_BOOLEAN_FALSE:
                    return "false";
                default:
                    // Unknown data type; null is the best we can offer
                    return null;
            }
        }

        public @Nullable byte[] getValueBytesHex() throws XmlPullParserException {
            switch (type) {
                case TYPE_NULL:
                    return null;
                case TYPE_BYTES_HEX:
                case TYPE_BYTES_BASE64:
                    return valueBytes;
                case TYPE_STRING:
                case TYPE_STRING_INTERNED:
                    try {
                        return hexStringToBytes(valueString);
                    } catch (Exception e) {
                        throw new XmlPullParserException("Invalid attribute " + name + ": " + e);
                    }
                default:
                    throw new XmlPullParserException("Invalid conversion from " + type);
            }
        }


        public @Nullable byte[] getValueBytesBase64() throws XmlPullParserException {
            switch (type) {
                case TYPE_NULL:
                    return null;
                case TYPE_BYTES_HEX:
                case TYPE_BYTES_BASE64:
                    return valueBytes;
                case TYPE_STRING:
                case TYPE_STRING_INTERNED:
                    try {
                        return Base64.decode(valueString, Base64.NO_WRAP);
                    } catch (Exception e) {
                        throw new XmlPullParserException("Invalid attribute " + name + ": " + e);
                    }
                default:
                    throw new XmlPullParserException("Invalid conversion from " + type);
            }
        }

        public int getValueInt() throws XmlPullParserException {
            switch (type) {
                case TYPE_INT:
                case TYPE_INT_HEX:
                    return valueInt;
                case TYPE_STRING:
                case TYPE_STRING_INTERNED:
                    try {
                        return Integer.parseInt(valueString);
                    } catch (Exception e) {
                        throw new XmlPullParserException("Invalid attribute " + name + ": " + e);
                    }
                default:
                    throw new XmlPullParserException("Invalid conversion from " + type);
            }
        }

        public int getValueIntHex() throws XmlPullParserException {
            switch (type) {
                case TYPE_INT:
                case TYPE_INT_HEX:
                    return valueInt;
                case TYPE_STRING:
                case TYPE_STRING_INTERNED:
                    try {
                        return Integer.parseInt(valueString, 16);
                    } catch (Exception e) {
                        throw new XmlPullParserException("Invalid attribute " + name + ": " + e);
                    }
                default:
                    throw new XmlPullParserException("Invalid conversion from " + type);
            }
        }

        public long getValueLong() throws XmlPullParserException {
            switch (type) {
                case TYPE_LONG:
                case TYPE_LONG_HEX:
                    return valueLong;
                case TYPE_STRING:
                case TYPE_STRING_INTERNED:
                    try {
                        return Long.parseLong(valueString);
                    } catch (Exception e) {
                        throw new XmlPullParserException("Invalid attribute " + name + ": " + e);
                    }
                default:
                    throw new XmlPullParserException("Invalid conversion from " + type);
            }
        }

        public long getValueLongHex() throws XmlPullParserException {
            switch (type) {
                case TYPE_LONG:
                case TYPE_LONG_HEX:
                    return valueLong;
                case TYPE_STRING:
                case TYPE_STRING_INTERNED:
                    try {
                        return Long.parseLong(valueString, 16);
                    } catch (Exception e) {
                        throw new XmlPullParserException("Invalid attribute " + name + ": " + e);
                    }
                default:
                    throw new XmlPullParserException("Invalid conversion from " + type);
            }
        }

        public float getValueFloat() throws XmlPullParserException {
            switch (type) {
                case TYPE_FLOAT:
                    return valueFloat;
                case TYPE_STRING:
                case TYPE_STRING_INTERNED:
                    try {
                        return Float.parseFloat(valueString);
                    } catch (Exception e) {
                        throw new XmlPullParserException("Invalid attribute " + name + ": " + e);
                    }
                default:
                    throw new XmlPullParserException("Invalid conversion from " + type);
            }
        }

        public double getValueDouble() throws XmlPullParserException {
            switch (type) {
                case TYPE_DOUBLE:
                    return valueDouble;
                case TYPE_STRING:
                case TYPE_STRING_INTERNED:
                    try {
                        return Double.parseDouble(valueString);
                    } catch (Exception e) {
                        throw new XmlPullParserException("Invalid attribute " + name + ": " + e);
                    }
                default:
                    throw new XmlPullParserException("Invalid conversion from " + type);
            }
        }

        public boolean getValueBoolean() throws XmlPullParserException {
            switch (type) {
                case TYPE_BOOLEAN_TRUE:
                    return true;
                case TYPE_BOOLEAN_FALSE:
                    return false;
                case TYPE_STRING:
                case TYPE_STRING_INTERNED:
                    if ("true".equalsIgnoreCase(valueString)) {
                        return true;
                    } else if ("false".equalsIgnoreCase(valueString)) {
                        return false;
                    } else {
                        throw new XmlPullParserException(
                                "Invalid attribute " + name + ": " + valueString);
                    }
                default:
                    throw new XmlPullParserException("Invalid conversion from " + type);
            }
        }
    }

    // NOTE: To support unbundled clients, we include an inlined copy
    // of hex conversion logic from HexDump below
    private final static char[] HEX_DIGITS =
            { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    private static int toByte(char c) {
        if (c >= '0' && c <= '9') return (c - '0');
        if (c >= 'A' && c <= 'F') return (c - 'A' + 10);
        if (c >= 'a' && c <= 'f') return (c - 'a' + 10);
        throw new IllegalArgumentException("Invalid hex char '" + c + "'");
    }

    static String bytesToHexString(byte[] value) {
        final int length = value.length;
        final char[] buf = new char[length * 2];
        int bufIndex = 0;
        for (int i = 0; i < length; i++) {
            byte b = value[i];
            buf[bufIndex++] = HEX_DIGITS[(b >>> 4) & 0x0F];
            buf[bufIndex++] = HEX_DIGITS[b & 0x0F];
        }
        return new String(buf);
    }

    static byte[] hexStringToBytes(String value) {
        final int length = value.length();
        if (length % 2 != 0) {
            throw new IllegalArgumentException("Invalid hex length " + length);
        }
        byte[] buffer = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            buffer[i / 2] = (byte) ((toByte(value.charAt(i)) << 4)
                    | toByte(value.charAt(i + 1)));
        }
        return buffer;
    }
}
