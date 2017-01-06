/*
 * MIT License
 *
 * Copyright (c) 2016 Santiago Hollmann
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.shollmann.igcparser.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.shollmann.android.igcparser.Parser;
import com.shollmann.android.igcparser.model.IGCFile;
import com.shollmann.android.igcparser.util.Logger;
import com.shollmann.android.igcparser.util.Utilities;
import com.shollmann.igcparser.R;
import com.shollmann.igcparser.events.FileClickEvent;
import com.shollmann.igcparser.tracking.TrackerHelper;
import com.shollmann.igcparser.ui.adapter.FilesAdapter;
import com.shollmann.igcparser.util.Comparators;
import com.shollmann.igcparser.util.Constants;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class IGCFilesActivity extends AppCompatActivity implements MenuItem.OnMenuItemClickListener {

    private LinearLayout layoutLoading;
    private RecyclerView recyclerView;
    private TextView txtLoading;
    private ProgressBar progress;
    private FilesAdapter adapter;
    private LinearLayoutManager layoutManager;
    private List<IGCFile> listFiles = new ArrayList<>();
    private File lastSearchedPath;
    private boolean isSearching = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_igc_files);
        findViews();

        setupFilesList();
        new FindIGCFilesAsynkTask().execute(Utilities.getXCSoarDataFolder());

    }

    private void setupFilesList() {
        recyclerView.setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new FilesAdapter(listFiles);
        recyclerView.setAdapter(adapter);
    }

    private void findViews() {
        recyclerView = (RecyclerView) findViewById(R.id.files_recyclerview);
        layoutLoading = (LinearLayout) findViewById(R.id.files_layout_loading);
        txtLoading = (TextView) findViewById(R.id.files_loading_text);
        progress = (ProgressBar) findViewById(R.id.files_loading_progress);
    }

    private List<IGCFile> getListIGCFiles(File parentDir) {
        List<IGCFile> inFiles = new ArrayList<>();
        Queue<File> files = new LinkedList<>();
        try {
            files.addAll(Arrays.asList(parentDir.listFiles()));
            while (!files.isEmpty()) {
                File file = files.remove();
                if (file != null && file.isDirectory()) {
                    files.addAll(Arrays.asList(file.listFiles()));
                } else if (file != null && (file.getName().toLowerCase().endsWith(".igc"))) {
                    inFiles.add(Parser.quickParse(Uri.parse(file.getAbsolutePath())));
                }
            }
        } catch (Throwable t) {
            final String message = "Couldn't open files";
            Crashlytics.log(message);
            Crashlytics.logException(t);
            Logger.logError(message);
        }

        Collections.sort(inFiles, Comparators.compareByDate);

        return inFiles;
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(FileClickEvent event) {
        TrackerHelper.trackTapFile();
        Intent intent = new Intent(this, FlightInformationActivity.class);
        intent.putExtra(Constants.FILE_TO_LOAD_PATH, event.getFile().getAbsoluteFile().toString());
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.files_menu, menu);
        MenuItem menuSearchEntireSdCard = menu.findItem(R.id.menu_search_sdcard);
        MenuItem menuRefresh = menu.findItem(R.id.menu_refresh);
        MenuItem menuSortByPilot = menu.findItem(R.id.menu_sort_pilot);
        MenuItem menuSortByGliderId = menu.findItem(R.id.menu_sort_glider);
        MenuItem menuSortByDate = menu.findItem(R.id.menu_sort_date);
        MenuItem menuAbout = menu.findItem(R.id.menu_about);

        menuSearchEntireSdCard.setOnMenuItemClickListener(this);
        menuSortByDate.setOnMenuItemClickListener(this);
        menuSortByGliderId.setOnMenuItemClickListener(this);
        menuRefresh.setOnMenuItemClickListener(this);
        menuSortByPilot.setOnMenuItemClickListener(this);
        menuAbout.setOnMenuItemClickListener(this);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        if (isSearching) {
            Toast.makeText(this, R.string.search_flights_wait, Toast.LENGTH_LONG).show();
            return false;
        }
        switch (menuItem.getItemId()) {
            case R.id.menu_refresh:
                TrackerHelper.trackRefresh();
                searchForFiles(lastSearchedPath);
                break;
            case R.id.menu_search_sdcard:
                TrackerHelper.trackSearchSdCard();
                searchForFiles(Utilities.getSdCardFolder());
                break;
            case R.id.menu_sort_glider:
                TrackerHelper.trackSortByGlider();
                sortBy(Comparators.compareByGlider);
                break;
            case R.id.menu_sort_pilot:
                TrackerHelper.trackSortByPilot();
                sortBy(Comparators.compareByPilot);
                break;
            case R.id.menu_sort_date:
                TrackerHelper.trackSortByDate();
                sortBy(Comparators.compareByDate);
                break;
            case R.id.menu_about:
                TrackerHelper.trackAbout();
                Intent intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                break;
        }
        return false;
    }

    private void searchForFiles(File path) {
        listFiles.clear();
        adapter.notifyDataSetChanged();
        layoutLoading.setVisibility(View.VISIBLE);
        new FindIGCFilesAsynkTask().execute(path);
    }

    private void sortBy(Comparator<IGCFile> comparator) {
        if (!isSearching) {
            Collections.sort(listFiles, comparator);
            adapter.notifyDataSetChanged();
        }
    }

    private class FindIGCFilesAsynkTask extends AsyncTask<File, Void, Boolean> {

        protected Boolean doInBackground(File... file) {
            isSearching = true;
            lastSearchedPath = file[0];
            listFiles = getListIGCFiles(file[0]);
            return file[0].getAbsolutePath().equals(Utilities.getSdCardFolder().getAbsolutePath());
        }

        protected void onProgressUpdate(Void... something) {
        }

        protected void onPostExecute(Boolean isEntireFolder) {
            if (!listFiles.isEmpty()) {
                layoutLoading.setVisibility(RecyclerView.GONE);
                adapter.setDataset(listFiles);
                adapter.notifyDataSetChanged();
            } else {
                if (!isEntireFolder) {
                    final String message = "No IGC files found on XCSoar folder. Searching on other folders";
                    Logger.log(message);
                    Crashlytics.log(message);
                    txtLoading.setText(getString(R.string.searching_igc_all_sdcard));
                    new FindIGCFilesAsynkTask().execute(Utilities.getSdCardFolder());
                } else {
                    progress.setVisibility(View.GONE);
                    txtLoading.setText(getString(R.string.no_files_found_with_explanation));
                    TrackerHelper.trackNoFilesFound();
                }
            }
            isSearching = false;
        }
    }
}
