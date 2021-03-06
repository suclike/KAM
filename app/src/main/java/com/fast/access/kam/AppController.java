package com.fast.access.kam;

import android.app.Application;

import com.crashlytics.android.Crashlytics;
import com.fast.access.kam.activities.Home;
import com.fast.access.kam.global.loader.cache.IconCache;

import cat.ereza.customactivityoncrash.CustomActivityOnCrash;
import de.greenrobot.event.EventBus;
import io.fabric.sdk.android.Fabric;

/**
 * Created by Kosh on 8/16/2015. copyrights are reserved
 */
public class AppController extends Application {

    private static AppController controller;
    private IconCache mIconCache;

    @Override
    public void onCreate() {
        super.onCreate();
        controller = this;
        CustomActivityOnCrash.setRestartActivityClass(Home.class);
        CustomActivityOnCrash.install(this);
        Crashlytics crashlytics = new Crashlytics.Builder().disabled(BuildConfig.DEBUG).build();
        Fabric.with(this, crashlytics);
        mIconCache = new IconCache(this);
    }

    public static AppController getController() {
        return controller;
    }

    public EventBus getBus() {
        return EventBus.getDefault();
    }

    public IconCache getIconCache() {
        return mIconCache;
    }

}