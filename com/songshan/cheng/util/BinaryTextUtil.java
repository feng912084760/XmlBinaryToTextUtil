package com.example.mytestapplication.util;

import android.util.Log;

import androidx.core.util.AtomicFile;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class BinaryTextUtil {

    public static void changeBinaryToText(File sourceF) {
        FileInputStream fis = null;
//        AtomicFile fl = new AtomicFile(new File(cont.getExternalFilesDir("temp").getPath() + "/0.xml"));
        AtomicFile fl = new AtomicFile(sourceF);
        try {
            fis = fl.openRead();
            String souPath = sourceF.getAbsolutePath();
            int loc = souPath.lastIndexOf("/");

            String pref = souPath.substring(0, loc);
            String after = souPath.substring(loc,souPath.length());
            String afPla = after.replaceAll("\\.","text.");
            String desPath = pref + afPla;
            Log.e("changeBinaryToText","目的路径：" + desPath);
            readData(fis, new File(desPath));
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (XmlPullParserException pe) {
            pe.printStackTrace();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                    Log.e("changeBinaryToText", "读取数据流关闭");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void readData(InputStream is, File wf)  throws IOException, XmlPullParserException {
        BinaryXmlReader bxr = new BinaryXmlReader();
        bxr.setInput(is, "UTF-8");
        int type;
        while ((type = bxr.next()) != XmlPullParser.START_TAG
                && type != XmlPullParser.END_DOCUMENT) {
            // Skip
        }

        if (type != XmlPullParser.START_TAG) {

            return;
        }
        if (type == XmlPullParser.START_TAG) {// 第一个 TAG
            FileOutputStream fos = null;
            AtomicFile aFile = new AtomicFile(wf);
            try {
                fos = aFile.startWrite();
//                writeUserLP(userData, fos);
                TextXmlWriter txw = new TextXmlWriter();
                txw.setOutput(fos, "UTF-8");
                txw.startDocument(null, true);
                txw.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
                String startendTag = bxr.getName();
                txw.startTag(null, startendTag);
                int ac = bxr.getAttributeCount();
                for (int i =0; i < ac; i++) {
                    txw.attribute(null, bxr.getAttributeName(i), bxr.getAttributeValueString(i));
                }
                int outerDepth = bxr.getDepth();
                while ((type = bxr.next()) != XmlPullParser.END_DOCUMENT
                        && (type != XmlPullParser.END_TAG || bxr.getDepth() > outerDepth)) {
                    switch (type) {
                        case XmlPullParser.END_TAG:
                            txw.endTag(null,bxr.getName());
                            break;
                        case XmlPullParser.TEXT:
                            txw.text(bxr.getText());
                            break;
                        case XmlPullParser.START_TAG:
                            txw.startTag(null, bxr.getName());
                            int acSec = bxr.getAttributeCount();
                            for (int i =0; i < acSec; i++) {
                                txw.attribute(null, bxr.getAttributeName(i), bxr.getAttributeValueString(i));
                            }
                            break;
                    }

                }
                txw.endTag(null, startendTag);
                txw.endDocument();
                aFile.finishWrite(fos);
                Log.e("changeBinaryToText", "write OK");
            } catch (Exception ioe) {
                Log.e("changeBinaryToText", "Error writing info ", ioe);
                aFile.failWrite(fos);
            }
        }

    }
}
