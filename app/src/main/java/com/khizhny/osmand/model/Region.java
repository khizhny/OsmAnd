package com.khizhny.osmand.model;

import android.support.annotation.NonNull;

import java.util.ArrayList;

public class Region implements Comparable<Region>{

    private static final String EXTENSION = ".zip";

    @Override
    public int compareTo(@NonNull Region o) {
        try{
            return name.compareTo(o.name);
        } catch (Exception e)  {
            return 0;
        }
    }

    public enum DownloadState {
        NOT_STARTED,
        QUEUED,
        DOWNLOADING,
        COMPLETE
    }

    // parsed from xml values
    public String type;
    public String name;
    public String download_suffix;
    public String inner_download_suffix;
    public String download_prefix;
    public String inner_download_prefix;
    public String map;
    public String srtm;
    public String hillshade;
    public String wiki;
    public String roads;
    public String translate;
    public String join_map_files;
    public String boundary;

    // tree info
    private Region parent;
    private ArrayList<Region> regions=new ArrayList<>(); // Child regions collection

    // download info
    public volatile DownloadState downloadState = DownloadState.NOT_STARTED;
    public String local_file_path;
    public int downloadProgress=0;
    public int fileSize=0;

    public String getCapitalName() {
        return name.substring(0,1).toUpperCase()+name.substring(1);
    }

    public String getDownloadProgressMb() {
        return (downloadProgress/(1024*1024)) + " Mb";
    }

    public String getSizeMb() {
        return (fileSize/(1024*1024)) + " Mb";
    }

    public void addRegion(Region R){
        regions.add(R);
        R.parent=this;
    }

    public Region getParent() {
        return parent;
    }

    public ArrayList<Region> getRegions() {
        return regions;
    }

    public String getDownloadURL(){
        return "http://download.osmand.net/download.php?standard=yes&file="+getCapitalName()+"_" + parent.name+ "_2.obf.zip";
    }

    public void validate() {
        if (type==null) { //1. by default map=yes, srtm=yes, hillshade=yes (in case 'type' not specified)
            if (map==null) map="yes";
            if (srtm==null)srtm="yes";
            if (hillshade==null) hillshade="yes";
            type="map";
        } else { //in case type specified it takes precedence and sets all flags = no
            map="no";
            srtm="no";
            hillshade="no";
            switch (type) {
                case "srtm": srtm="yes"; break;
                case "hillshade": hillshade="yes"; break;
                case "continent": break;
            }
        }
        if (wiki==null) wiki=map;
        if (roads==null) roads=map;
    }

    public String getRelativeFilePath(){
        String result=name+ EXTENSION;
        Region t=parent;
        while (t!=null){
            result=t.name+"/"+result;
            t=t.parent;
        }
        return result;
    }

}
