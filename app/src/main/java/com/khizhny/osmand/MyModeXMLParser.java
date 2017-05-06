package com.khizhny.osmand;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Comparator;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import com.khizhny.osmand.model.Region;

import static android.content.ContentValues.TAG;

class MyModeXMLParser {

    Region parse(InputStream is) {
        XmlPullParserFactory factory;
        XmlPullParser parser;
        int currentLevel=0;
        Region root= new Region ();
        Region parent=root;

        try {
            factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            parser = factory.newPullParser();
            parser.setInput(is, null);

                int eventType = parser.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {

                String tagName = parser.getName();
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if (tagName.equalsIgnoreCase("regions_list"))
                        {
                            currentLevel=0;
                        } else if (tagName.equalsIgnoreCase("region")) {

                            Region newRegion = new Region();
                            parent.addRegion(newRegion);
                            getAttributes(newRegion,parser);
                            currentLevel=currentLevel+1;
                            parent = newRegion;
                        }
                        break;


                    case XmlPullParser.TEXT:
                        //  GETTING TEXT ENCLOSED BY ANY TAG
                        break;

                    case XmlPullParser.END_TAG:
                        if (tagName.equalsIgnoreCase("regions_list")) {
                            Collections.sort(root.getRegions());
                            return root;
                        } else if (tagName.equalsIgnoreCase("region"))
                        {
                            currentLevel=currentLevel-1;
                            Collections.sort(parent.getRegions());
                            parent=parent.getParent();
                        }

                        break;
                    default:
                        break;
                }
                eventType = parser.next();
            }

        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void getAttributes(Region region, XmlPullParser parser){
        int maxI = parser.getAttributeCount();
        for (int i=0; i<maxI; i++){
            String attributeName=parser.getAttributeName(i);
            String attributeValue=parser.getAttributeValue(i);
            switch (attributeName){
                case "name": region.name=attributeValue; break;
                case "type": region.type=attributeValue; break;
                case "inner_download_suffix": region.inner_download_suffix=attributeValue; break;
                case "inner_download_prefix": region.inner_download_prefix=attributeValue; break;
                case "download_suffix": region.download_suffix=attributeValue; break;
                case "download_prefix": region.download_prefix=attributeValue; break;
                case "map": region.map=attributeValue; break;
                case "translate": region.translate=attributeValue; break;
                case "srtm": region.srtm=attributeValue; break;
                case "hillshade": region.hillshade=attributeValue; break;
                case "wiki": region.wiki=attributeValue; break;
                case "roads": region.roads=attributeValue; break;
                case "boundary": region.boundary=attributeValue; break;
                case "join_map_files": region.join_map_files=attributeValue; break;
                default:
                    Log.d(TAG, "Unpareced attribute:"+attributeName+"="+attributeValue);
            }
        }
        region.validate();
    }
}