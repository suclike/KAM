package com.fast.access.kam.activities;

import android.Manifest;
import android.app.LoaderManager;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.fast.access.kam.AppController;
import com.fast.access.kam.R;
import com.fast.access.kam.activities.base.BaseActivity;
import com.fast.access.kam.global.adapter.AppsAdapter;
import com.fast.access.kam.global.helper.AppHelper;
import com.fast.access.kam.global.loader.AppsLoader;
import com.fast.access.kam.global.model.AppsModel;
import com.fast.access.kam.global.model.EventsModel;
import com.fast.access.kam.global.model.helper.OperationType;
import com.fast.access.kam.global.service.ExecutorService;
import com.fast.access.kam.global.util.IabHelper;
import com.fast.access.kam.global.util.IabResult;
import com.fast.access.kam.global.util.Inventory;
import com.fast.access.kam.global.util.Purchase;
import com.fast.access.kam.widget.impl.OnItemClickListener;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import butterknife.Bind;

public class Home extends BaseActivity implements SearchView.OnQueryTextListener, OnItemClickListener,
        NavigationView.OnNavigationItemSelectedListener, LoaderManager.LoaderCallbacks<List<AppsModel>>,
        ActionMode.Callback {
    private static final String TAG = "HOME";
    @Bind(R.id.toolbar)
    Toolbar toolbar;
    @Bind(R.id.appbar)
    AppBarLayout appbar;
    @Bind(R.id.recycler)
    RecyclerView recycler;
    @Bind(R.id.main_content)
    CoordinatorLayout mainContent;
    @Bind(R.id.nav_view)
    NavigationView navigationView;
    @Bind(R.id.drawer_layout)
    DrawerLayout mDrawerLayout;
    @Bind(R.id.progress)
    ProgressBar progress;
    private GridLayoutManager manager;
    private AppsAdapter adapter;
    private IabHelper mHelper;
    private List<String> productsList = new ArrayList<>();
    private ActionMode actionMode;
    private HashMap<Integer, AppsModel> selectedApps = new LinkedHashMap<>();
    public static final int REQUEST_STORAGE = 1;

    private static String[] EXTERNAL_STORAGE = {Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};

    @Override
    protected int layout() {
        return R.layout.main_activity;
    }

    @Override
    protected boolean canBack() {
        return false;
    }

    @Override
    protected boolean hasMenu() {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppController.getController().getBus().register(this);
        manager = new GridLayoutManager(this, getResources().getInteger(R.integer.num_row));
        recycler.setItemAnimator(new DefaultItemAnimator());
        recycler.setHasFixedSize(true);
        recycler.setLayoutManager(manager);
        mDrawerLayout.setStatusBarBackgroundColor(AppHelper.getPrimaryDarkColor(AppHelper.getPrimaryColor(this)));
        navigationView.setItemIconTintList(ColorStateList.valueOf(AppHelper.getAccentColor(this)));
        if (AppHelper.isDarkTheme(this)) {
            navigationView.setItemTextColor(ColorStateList.valueOf(AppHelper.getAccentColor(this)));
        }
        adapter = new AppsAdapter(this, new ArrayList<AppsModel>());
        recycler.setAdapter(adapter);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (navigationView != null) {
            setupDrawerContent(navigationView);
        }
        getLoaderManager().restartLoader(0, null, this);
        productsList.addAll(Arrays.asList(getResources().getStringArray(R.array.in_app_billing)));
        mHelper = new IabHelper(this, getString(R.string.base64));
        mHelper.startSetup(mPurchaseFinishedListener);
        showWhatsNew();
        countBackup();
    }

    private void countBackup() {
        if (navigationView.getMenu() != null) {
            if (navigationView.getMenu().findItem(R.id.restore) != null) {
                int count = AppHelper.getRestoreApksCount();
                if (count != 0) {
                    navigationView.getMenu().findItem(R.id.restore).setTitle(getString(R.string.restore_title) + " ( " + count + " )");
                } else {
                    navigationView.getMenu().findItem(R.id.restore).setTitle(getString(R.string.restore_title));
                }
            }
        }
    }

    private void onSuccessPaid() {
        mHelper.queryInventoryAsync(true, productsList, mReceivedInventoryListener);
    }

    private void showWhatsNew() {
        if (!AppHelper.hasSeenWhatsNew(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Whats New")
                    .setMessage(getString(R.string.whats_new))
                    .setPositiveButton("Close", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            AppHelper.setHasSeenWhatsNew(Home.this);
                        }
                    })
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            AppHelper.setHasSeenWhatsNew(Home.this);
                        }
                    }).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
            if (searchView != null) {
                searchView.setOnQueryTextListener(this);
                SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
                searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                mDrawerLayout.openDrawer(GravityCompat.START);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupDrawerContent(NavigationView navigationView) {
        navigationView.setNavigationItemSelectedListener(this);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if (newText.isEmpty()) {
            adapter.getFilter().filter("");
        } else {
            adapter.getFilter().filter(newText.toLowerCase());
        }

        return false;
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem menuItem) {
        mDrawerLayout.closeDrawers();
        switch (menuItem.getItemId()) {
            case R.id.settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.team:
                startActivity(new Intent(this, TeamActivity.class));
                return true;
            case R.id.backup:
                startOperation(OperationType.BACKUP);
                return true;
            case R.id.restore:
                startOperation(OperationType.RESTORE);
                return true;
            case R.id.donate:
                final List<String> titles = new ArrayList<>();
                titles.add("Cup Of Coffee");
                titles.add("I'm Generous");
                ArrayAdapter arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, titles);
                new AlertDialog.Builder(Home.this)
                        .setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    mHelper.launchPurchaseFlow(Home.this, productsList.get(which), 2001, onIabPurchaseFinishedListener, titles.get
                                            (which));
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    Snackbar.make(mainContent, "Oops, Something went wrong", Snackbar.LENGTH_LONG).show();
                                }
                            }
                        }).setNegativeButton("Cancel", null).show();

                return true;

        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!mHelper.handleActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void startOperation(OperationType type) {
        if (!doNeedPermission()) {
            Intent intent = new Intent(this, ExecutorService.class);
            intent.putExtra("operationType", type.name());
            startService(intent);
        }
    }

    public void onEvent(final EventsModel eventsModel) {
        if (eventsModel != null) {
            if (eventsModel.getEventType() != null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        switch (eventsModel.getEventType()) {
                            case THEME:
                                recreate();
                                break;
                            case FOLDER_LISTENER:
                                countBackup();
                                break;
                        }
                    }
                });
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (AppController.getController().getBus().isRegistered(this)) {
            AppController.getController().getBus().unregister(this);
        }
        if (mHelper != null) mHelper.dispose();
        mHelper = null;
        super.onDestroy();
    }

    private IabHelper.OnIabSetupFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabSetupFinishedListener() {
        @Override
        public void onIabSetupFinished(IabResult result) {
            if (!result.isSuccess()) {
                Log.d(TAG, "In-app Billing setup failed: " + result);
            } else {
                Log.d(TAG, "In-app Billing is set up OK");
            }
        }
    };

    private IabHelper.OnIabPurchaseFinishedListener onIabPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        @Override
        public void onIabPurchaseFinished(IabResult result, Purchase info) {
            if (info != null && info.getSku() != null) {
                if (info.getSku().equals(productsList.get(0)) || info.getSku().equals(productsList.get(1))) {
                    onSuccessPaid();
                }
            }
        }
    };
    private IabHelper.QueryInventoryFinishedListener mReceivedInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            if (result.isSuccess()) {
                if (inventory != null && inventory.getSkuDetails(productsList.get(0)) != null) {
                    mHelper.consumeAsync(inventory.getPurchase(productsList.get(0)), mConsumeFinishedListener);
                } else if (inventory != null && inventory.getSkuDetails(productsList.get(1)) != null) {
                    mHelper.consumeAsync(inventory.getPurchase(productsList.get(1)), mConsumeFinishedListener);
                }
            }
        }
    };
    private IabHelper.OnConsumeFinishedListener mConsumeFinishedListener = new IabHelper.OnConsumeFinishedListener() {
        public void onConsumeFinished(Purchase purchase, IabResult result) {
            if (result.isSuccess()) {
                Toast.makeText(Home.this, "Thank You Very Much", Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    public Loader<List<AppsModel>> onCreateLoader(int id, Bundle args) {
        progress.setVisibility(View.VISIBLE);
        return new AppsLoader(this, AppController.getController().getIconCache());
    }

    @Override
    public void onLoadFinished(Loader<List<AppsModel>> loader, List<AppsModel> data) {
        progress.setVisibility(View.GONE);
        adapter.insert(data);
    }

    @Override
    public void onLoaderReset(Loader<List<AppsModel>> loader) {
        adapter.clearAll();
    }

    @Override
    public void onItemClickListener(View view, int position) {
        Bundle bundle = new Bundle();
        bundle.putParcelable("app", adapter.getModelList().get(position));
        Intent intent = new Intent(Home.this, AppDetailsActivity.class);
        intent.putExtras(bundle);
        startActivity(intent);
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
    }

    @Override
    public void onItemLongClickListener(View view, int position) {
        if (selectedApps.get(position) == null) {
            selectedApps.put(position, adapter.getModelList().get(position));
            adapter.setItemChecked(position, true);
        } else {
            selectedApps.remove(position);
            adapter.setItemChecked(position, false);
        }
        if (selectedApps.size() != 0) {
            if (actionMode == null) {
                actionMode = toolbar.startActionMode(this);
                AppHelper.actionModeColor(this);
            }
            if (selectedApps.size() > 1) {
                actionMode.setTitle("Backup ( " + selectedApps.size() + " Apps )");
            } else {
                actionMode.setTitle("Backup ( " + selectedApps.size() + " App )");
            }
            enableDisableMenuItem(false);
        } else {
            actionMode.finish();
            actionMode = null;
        }
    }

    private void enableDisableMenuItem(boolean enable) {
        if (enable) {
            navigationView.getMenu().findItem(R.id.restore).setTitle("Restore");
            navigationView.getMenu().findItem(R.id.backup).setTitle("Backup");
        } else {
            navigationView.getMenu().findItem(R.id.restore).setTitle("Restore (Disabled)");
            navigationView.getMenu().findItem(R.id.backup).setTitle("Backup (Disabled)");
        }
        navigationView.getMenu().findItem(R.id.restore).setEnabled(enable);
        navigationView.getMenu().findItem(R.id.backup).setEnabled(enable);
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.menu_action, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // i f***ing  hate android sometimes.
            getWindow().setStatusBarColor(AppHelper.getPrimaryDarkColor(AppHelper.getPrimaryColor(Home.this)));
        }
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        if (item.getItemId() == R.id.backupAction) {
            if (!doNeedPermission()) {
                Intent intent = new Intent(this, ExecutorService.class);
                intent.putExtra("operationType", OperationType.SELECTED_APPS.name());
                Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().setPrettyPrinting().create();
                Log.e(TAG, gson.toJson(selectedApps.values()));
                intent.putExtra("apps", gson.toJson(selectedApps.values()));
                startService(intent);
                actionMode.finish();
            }
            return true;
        }
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        actionMode = null;
        selectedApps.clear();
        adapter.clearSelection();
        enableDisableMenuItem(true);
    }

    private boolean doNeedPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermission();
            return true;
        }
        return false;
    }

    private void requestPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Snackbar.make(mainContent, "Permission is required to continue using this " +
                    "function!", Snackbar.LENGTH_INDEFINITE).setAction("Okay", new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ActivityCompat.requestPermissions(Home.this, EXTERNAL_STORAGE, REQUEST_STORAGE);
                }
            }).show();
        } else {
            ActivityCompat.requestPermissions(this, EXTERNAL_STORAGE, REQUEST_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_STORAGE) {
            if (verifyPermissions(grantResults)) {
                Snackbar.make(findViewById(R.id.toolbar), "Permission Granted, you may continue using" +
                        " this function now.", Snackbar.LENGTH_LONG).show();
            } else {
                Snackbar.make(findViewById(R.id.toolbar), "Permission Denied, you may not be able to " +
                        "use this functionality", Snackbar.LENGTH_LONG).show();
            }

        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    public static boolean verifyPermissions(int[] grantResults) {
        if (grantResults.length < 1) {
            return false;
        }
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

}
