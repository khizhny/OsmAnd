package com.khizhny.osmand;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.khizhny.osmand.model.Region;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private static final String REGIONS_XML = "regions.xml";
    public static final String DOWNLOAD_MAPS = "Download Maps";
    private static String TAG = "osmand";
    private static final String FOLDER_NAME="TEST_APP_MAPS";
    private static final long REFRESH_INTERVAL_MSEC =2000;


    private ListView regionsListView;
    private Region root;
    private Region regionSelectedAsRoot;
    private RegionsListAdapter regionsListAdapter;
    private AlertListAdapter alertListAdapter;
    private List <Region> downloadQueue =new ArrayList<>();
    private MyLoader myLoader = new MyLoader(downloadQueue); // AsyncTask for networking

    //UI Views
    private ProgressBar mainProgressBar;
    private TextView mainProgressText;
    private TextView mainProgressParameterName;
    private TextView mainProgressParameterValue;
    private TextView mainProgressParameterUnits;
    private TextView delimiter;
    private ConstraintLayout mainProgressLayout;
    private AlertDialog alertDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        regionsListView = (ListView) findViewById(R.id.lvContinents);

        try { // Parsing XML from asserts to get the root tree
            MyModeXMLParser parser = new MyModeXMLParser();
            InputStream inputStream = getAssets().open(REGIONS_XML);
            root = parser.parse(inputStream);
            root.name= DOWNLOAD_MAPS;
            regionSelectedAsRoot=root;
            if (regionSelectedAsRoot.getRegions()!=null){
                regionsListAdapter = new RegionsListAdapter(regionSelectedAsRoot.getRegions());
                regionsListView.setAdapter(regionsListAdapter);
                //regionsListView.setOnItemClickListener(this);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        setTitle(regionSelectedAsRoot.getCapitalName());

        mainProgressBar = (ProgressBar) findViewById(R.id.free_space_progress);
        mainProgressText = (TextView) findViewById(R.id.progress_text);
        mainProgressParameterName = (TextView) findViewById(R.id.tvFree);
        mainProgressParameterValue = (TextView) findViewById(R.id.tvValue);
        mainProgressParameterUnits = (TextView) findViewById(R.id.tvUnits);
        mainProgressLayout = (ConstraintLayout) findViewById(R.id.progress_layout);
        mainProgressLayout.setOnClickListener(this);
        delimiter = (TextView) findViewById(R.id.tvContinents);

        alertListAdapter = new AlertListAdapter(downloadQueue);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshMainProgressBar();
    }

    private void refreshMainProgressBar() {
        if (regionSelectedAsRoot ==root) { // Display free space status
            long total_space =Environment.getExternalStorageDirectory().getTotalSpace();
            long total_free_space =Environment.getExternalStorageDirectory().getFreeSpace();
            ((View) mainProgressBar.getParent()).setVisibility(View.VISIBLE);
            int total_free_space_int = (int) (total_free_space / 1024 / 1024);
            mainProgressBar.setMax((int) (total_space / 1024 / 1024));
            mainProgressBar.setProgress((int) ((total_space - total_free_space) / 1024 / 1024));
            mainProgressText.setText(R.string.device_memory);
            mainProgressParameterName.setText(R.string.free);
            mainProgressParameterValue.setText("" + total_free_space_int);
            mainProgressParameterUnits.setText(R.string.mb);
            delimiter.setVisibility(View.VISIBLE);
            delimiter.setText("\n\n\n"+getResources().getString(R.string.world_regions));

        }else {  // Display load status
            delimiter.setVisibility(View.VISIBLE);
            delimiter.setText("\n");
            if (downloadQueue.size()>0) {
                ((View) mainProgressBar.getParent()).setVisibility(View.VISIBLE);

                if (downloadQueue.size()==1) {
                    mainProgressText.setText(getString(R.string.downloading)+ " "+ downloadQueue.get(0).getCapitalName());
                }else{
                    mainProgressText.setText(getString(R.string.downloading_maps)+ " ("+ downloadQueue.size() +")");
                }

                long totalSize=0;
                long loadedSize=0;
                for (Region r: downloadQueue) {
                    totalSize=totalSize+r.fileSize;
                    loadedSize=loadedSize+r.downloadProgress;
                }
                int progress=0;
                if (totalSize!=0) progress=(int)(100*loadedSize/totalSize);
                mainProgressBar.setMax(100);
                mainProgressBar.setProgress(progress);
                mainProgressBar.invalidate();
                mainProgressParameterName.setText("");
                mainProgressParameterValue.setText(""+progress);
                mainProgressParameterUnits.setText("%");
            }else{
                // hiding progress bar if not loading maps
                ((View) mainProgressBar.getParent()).setVisibility(View.GONE);
                delimiter.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.progress_layout:
                // Creating dialog with Download process
                AlertDialog.Builder builer = new AlertDialog.Builder(this);
                builer.setCancelable(true);
                builer.setTitle("Downloads");

                builer.setPositiveButton("Close", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        alertDialog.dismiss();
                    }
                });

                builer.setAdapter(alertListAdapter,null);
                alertDialog=builer.create();
                alertDialog.show();
                break;

            case R.id.iv_download:
                Region clickedRegion = (Region) v.getTag();
                if (clickedRegion.map.equals("yes")) {
                    // if Region has children in  handling click as for downloading
                    switch (clickedRegion.downloadState) {
                        case  NOT_STARTED:
                            clickedRegion.fileSize = 1;
                            clickedRegion.local_file_path = Environment.getExternalStorageDirectory().getPath()+"/"+FOLDER_NAME+"/"+clickedRegion.getRelativeFilePath();
                            clickedRegion.downloadProgress=0;
                            if (myLoader.getStatus() == AsyncTask.Status.RUNNING) {
                                clickedRegion.downloadState = Region.DownloadState.QUEUED;
                                downloadQueue.add(clickedRegion);
                            } else {
                                downloadQueue.add(clickedRegion);
                                myLoader = new MyLoader(downloadQueue);
                                myLoader.execute();
                            }
                            break;
                        case DOWNLOADING:
                        case QUEUED:
                            // remove item from from Queue
                            clickedRegion.downloadState= Region.DownloadState.NOT_STARTED;
                            downloadQueue.remove(clickedRegion);
                            break;
                        case COMPLETE:
                            break;
                    }
                    regionsListAdapter.notifyDataSetChanged();
                    refreshMainProgressBar();
                }

                break;

            case R.id.region_name: // navigating to selected region
                Region r=(Region) v.getTag();
                if (r.getRegions().size()>0) {
                    regionSelectedAsRoot = r;
                    regionsListAdapter = new RegionsListAdapter(regionSelectedAsRoot.getRegions());
                    regionsListView.setAdapter(regionsListAdapter);
                    setTitle(regionSelectedAsRoot.getCapitalName());
                    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                    if (regionSelectedAsRoot.getParent() == root) refreshMainProgressBar();
                }
                break;
        }
    }
    private class AlertListAdapter extends ArrayAdapter<Region>{
        AlertListAdapter(List<Region> regions) {
            super(MainActivity.this, R.layout.continents_row, regions);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View rowView = convertView;
            Region r = downloadQueue.get(position);

            if (rowView == null) {
                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                rowView = inflater.inflate(R.layout.dialog_layout_row, parent, false);
            }
            ((TextView)rowView.findViewById(R.id.dialog_map_name)).setText(r.getCapitalName());

            String mapInfo = r.getDownloadProgressMb() +" "+getString(R.string.from)+" " + r.getSizeMb();
            ((TextView)rowView.findViewById(R.id.dialog_progress_info)).setText(mapInfo);

            ProgressBar pb = (ProgressBar) rowView.findViewById(R.id.dialog_progress_bar);
            pb.setMax(r.fileSize);
            pb.setProgress(r.downloadProgress);

            rowView.setTag(r);
            rowView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Region r= (Region) v.getTag();
                    r.downloadState= Region.DownloadState.NOT_STARTED;
                    downloadQueue.remove(r);
                    notifyDataSetChanged();
                    regionsListAdapter.notifyDataSetChanged();
                    refreshMainProgressBar();
                }
            });
            return rowView;
        }
    }

    private class RegionsListAdapter extends ArrayAdapter<Region> {

        RegionsListAdapter(List<Region> continentsList) {
            super(MainActivity.this, R.layout.continents_row, continentsList);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView,  @NonNull ViewGroup parent) {
            View rowView = convertView;
            Region region = regionSelectedAsRoot.getRegions().get(position);

            if (rowView == null) {
                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                rowView = inflater.inflate(R.layout.continents_row, parent, false);
            }

            TextView tvName = (TextView) rowView.findViewById(R.id.region_name);
            tvName.setText(region.getCapitalName());
            tvName.setTag(region);
            tvName.setOnClickListener(MainActivity.this);

            ImageView rightIcon = (ImageView) rowView.findViewById(R.id.iv_download);
            rightIcon.setVisibility(View.INVISIBLE);
            rightIcon.setColorFilter(ContextCompat.getColor(MainActivity.this,R.color.colorIcon));
            if (region.map.equals("yes")) {
                rightIcon.setVisibility(View.VISIBLE);
            }else{
                rightIcon.setVisibility(View.INVISIBLE);
            }
            rightIcon.setTag(region);
            rightIcon.setOnClickListener(MainActivity.this);

            ImageView leftIcon = (ImageView) rowView.findViewById(R.id.iv_globe);
            leftIcon.setVisibility(View.VISIBLE);
            leftIcon.setColorFilter(ContextCompat.getColor(MainActivity.this,R.color.colorIcon));

            switch (region.type){
                case "continent":
                    leftIcon.setImageResource(R.drawable.ic_world_globe_dark);
                    break;
                case "map":
                    leftIcon.setImageResource(R.drawable.ic_map);
                    break;
                default:
                leftIcon.setImageResource(R.drawable.ic_map);
            }


            ProgressBar progressBar = (ProgressBar) rowView.findViewById(R.id.map_download_progress);
            if (region.fileSize>0) {
                progressBar.setMax(region.fileSize);
                progressBar.setProgress(region.downloadProgress);
            }
            switch (region.downloadState){
                case NOT_STARTED:
                    rightIcon.setImageResource(R.drawable.ic_action_import);
                    progressBar.setVisibility(View.INVISIBLE);
                    break;

                case QUEUED:
                case DOWNLOADING:
                    rightIcon.setImageResource(R.drawable.ic_action_remove_dark);
                    rightIcon.setVisibility(View.VISIBLE);
                    progressBar.setVisibility(View.VISIBLE);
                    break;

                case COMPLETE:
                    progressBar.setVisibility(View.INVISIBLE);
                    rightIcon.setVisibility(View.INVISIBLE);
                    leftIcon.setColorFilter(ContextCompat.getColor(MainActivity.this,R.color.colorIconGreen));
                    break;
            }

            return rowView;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId()==android.R.id.home) goBack();
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        goBack();
    }

    /**
     * Navigates back in tree topology
     */
    void goBack(){
        if (regionSelectedAsRoot !=root){
            regionSelectedAsRoot = regionSelectedAsRoot.getParent();
            regionsListAdapter = new RegionsListAdapter(regionSelectedAsRoot.getRegions());
            regionsListView.setAdapter(regionsListAdapter);
            setTitle(regionSelectedAsRoot.getCapitalName());
            if (regionSelectedAsRoot !=root){
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }else{
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            }
            if (regionSelectedAsRoot ==root) refreshMainProgressBar();
        } else {
            this.finish();
        }
    }

    /**
     * This task is used for notworking operations
     */
    private class MyLoader  extends AsyncTask<Void, Void, Void> {
        private  List <Region> downloadQueue;
        private Region current;

        MyLoader (List <Region> downloadList){
            this.downloadQueue =downloadList;
        }

        @Override
        protected Void doInBackground(Void... params) {
            while (downloadQueue.size()!=0) {
                current= downloadQueue.get(0);
                current.downloadState=Region.DownloadState.DOWNLOADING;
                current.downloadProgress=0;
                try {
                    current.fileSize=getFileSize(current.getDownloadURL());
                    URL url = new URL(current.getDownloadURL());
                    URLConnection connection = url.openConnection();
                    connection.setReadTimeout(5000);
                    connection.connect();
                    // input stream to read file - with 8k buffer
                    InputStream input = new BufferedInputStream(url.openStream(), 8192);

                    File file = new File(current.local_file_path);
                    if (!file.exists()) {
                        Log.d(TAG, "Creating file "+current.local_file_path);
                        if (!file.mkdirs()) Log.d(TAG, "Problems creating Folder");
                    }else{
                        //TODO It is bad to delete existing file, but for test app...sgoditsa.
                        Log.d(TAG, "File already exists. Deleting "+current.local_file_path);
                        file.delete();
                    }
                    // Output stream to write file
                    OutputStream output = new FileOutputStream(current.local_file_path);

                    byte data[] = new byte[1024];
                    int total = 0;
                    int count;
                    long beginTime=System.currentTimeMillis();
                    while ((count = input.read(data)) != -1) {
                        total += count;
                        if (System.currentTimeMillis()-beginTime>= REFRESH_INTERVAL_MSEC) {
                            // Updating file sizes if needed
                            for (Region r: downloadQueue) {
                                if (r.fileSize<=1) {
                                    r.fileSize=getFileSize(r.getDownloadURL());
                                }
                            }
                            beginTime=System.currentTimeMillis();
                            current.downloadProgress=total;
                            publishProgress();
                        }
                        output.write(data, 0, count);
                        // Check if file download was cancelled
                        if (!downloadQueue.contains(current))
                        {
                            Log.d(TAG,"Stop loading. Not in the list anymore.");
                            file = new File(current.local_file_path);
                            file.delete();
                            current.downloadState= Region.DownloadState.NOT_STARTED;
                            current.downloadProgress=0;
                            publishProgress();
                            break;
                        }
                        current.downloadProgress=total;
                    }

                    output.flush();
                    output.close();
                    input.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if(current.downloadProgress==current.fileSize) {
                    // file fully loaded
                    current.downloadProgress=current.fileSize;
                    current.downloadState=Region.DownloadState.COMPLETE;
                    downloadQueue.remove(current);
                    publishProgress();
                }

            }
            return null;
        }

        private int getFileSize(String httpURL){
            try {
                URL url = new URL(httpURL);
                URLConnection urlConnection = url.openConnection();
                urlConnection.connect();
                urlConnection.setReadTimeout(2000);
                return urlConnection.getContentLength();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        }

        @Override
        protected void onProgressUpdate(Void... params) {
            regionsListAdapter.notifyDataSetChanged();
            if (alertDialog!=null) {
                if (alertDialog.isShowing()) {
                    alertListAdapter.notifyDataSetChanged();
                }
            }
            refreshMainProgressBar();
            super.onProgressUpdate(params);
        }

    }

}
