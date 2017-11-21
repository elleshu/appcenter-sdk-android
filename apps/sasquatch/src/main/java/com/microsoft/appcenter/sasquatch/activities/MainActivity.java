package com.microsoft.appcenter.sasquatch.activities;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.test.espresso.idling.CountingIdlingResource;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.microsoft.appcenter.AppCenter;
import com.microsoft.appcenter.AppCenterService;
import com.microsoft.appcenter.analytics.Analytics;
import com.microsoft.appcenter.analytics.AnalyticsPrivateHelper;
import com.microsoft.appcenter.analytics.channel.AnalyticsListener;
import com.microsoft.appcenter.analytics.ingestion.models.EventLog;
import com.microsoft.appcenter.analytics.ingestion.models.PageLog;
import com.microsoft.appcenter.crashes.AbstractCrashesListener;
import com.microsoft.appcenter.crashes.Crashes;
import com.microsoft.appcenter.crashes.CrashesListener;
import com.microsoft.appcenter.crashes.ingestion.models.ErrorAttachmentLog;
import com.microsoft.appcenter.crashes.model.ErrorReport;
import com.microsoft.appcenter.distribute.Distribute;
import com.microsoft.appcenter.ingestion.models.LogWithProperties;
import com.microsoft.appcenter.push.Push;
import com.microsoft.appcenter.push.PushListener;
import com.microsoft.appcenter.push.PushNotification;
import com.microsoft.appcenter.sasquatch.R;
import com.microsoft.appcenter.sasquatch.SasquatchDistributeListener;
import com.microsoft.appcenter.sasquatch.features.TestFeatures;
import com.microsoft.appcenter.sasquatch.features.TestFeaturesListAdapter;
import com.microsoft.appcenter.sasquatch.listeners.SasquatchAnalyticsListener;
import com.microsoft.appcenter.sasquatch.listeners.SasquatchCrashesListener;
import com.microsoft.appcenter.sasquatch.listeners.SasquatchPushListener;
import com.microsoft.appcenter.utils.async.AppCenterConsumer;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {

    public static final String LOG_TAG = "AppCenterSasquatch";
    static final String APP_SECRET_KEY = "appSecret";
    static final String LOG_URL_KEY = "logUrl";
    static final String FIREBASE_ENABLED_KEY = "firebaseEnabled";
    static SharedPreferences sSharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sSharedPreferences = getSharedPreferences("Sasquatch", Context.MODE_PRIVATE);
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().build());
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll().build());

        /* Set custom log URL if one was configured in settings. */
        String logUrl = sSharedPreferences.getString(LOG_URL_KEY, getString(R.string.log_url));
        if (!TextUtils.isEmpty(logUrl)) {
            AppCenter.setLogUrl(logUrl);
        }

        /* Set listeners. */
        AnalyticsPrivateHelper.setListener(getAnalyticsListener());
        Crashes.setListener(getCrashesListener());
        Distribute.setListener(new SasquatchDistributeListener());
        Push.setListener(getPushListener());

        /* Set distribute urls. */
        String installUrl = getString(R.string.install_url);
        if (!TextUtils.isEmpty(installUrl)) {
            Distribute.setInstallUrl(installUrl);
        }
        String apiUrl = getString(R.string.api_url);
        if (!TextUtils.isEmpty(apiUrl)) {
            Distribute.setApiUrl(apiUrl);
        }

        /* Enable Firebase analytics if we enabled the setting previously. */
        if (sSharedPreferences.getBoolean(FIREBASE_ENABLED_KEY, false)) {
            Push.enableFirebaseAnalytics(this);
        }

        /* Start App Center. */
        AppCenter.start(getApplication(), sSharedPreferences.getString(APP_SECRET_KEY, getString(R.string.app_secret)), Analytics.class, Crashes.class, Distribute.class, Push.class);

        /* If rum available, use it. */
        try {
            @SuppressWarnings("unchecked")
            Class<? extends AppCenterService> rum = (Class<? extends AppCenterService>) Class.forName("com.microsoft.appcenter.rum.RealUserMeasurements");
            rum.getMethod("setRumKey", String.class).invoke(null, getString(R.string.rum_key));

            /* Start rum. */
            AppCenter.start(rum);
        } catch (Exception ignore) {
        }

        /* Use some App Center getters. */
        AppCenter.getInstallId().thenAccept(new AppCenterConsumer<UUID>() {

            @Override
            public void accept(UUID uuid) {
                Log.i(LOG_TAG, "InstallId=" + uuid);
            }
        });

        /* Print last crash. */
        Crashes.hasCrashedInLastSession().thenAccept(new AppCenterConsumer<Boolean>() {

            @Override
            public void accept(Boolean crashed) {
                Log.i(LOG_TAG, "Crashes.hasCrashedInLastSession=" + crashed);
            }
        });
        Crashes.getLastSessionCrashReport().thenAccept(new AppCenterConsumer<ErrorReport>() {

            @Override
            public void accept(ErrorReport data) {
                if (data != null) {
                    Log.i(LOG_TAG, "Crashes.getLastSessionCrashReport().getThrowable()=", data.getThrowable());
                }
            }
        });

        /* Populate UI. */
        ((TextView) findViewById(R.id.package_name)).setText(String.format(getString(R.string.sdk_source_format), getPackageName().substring(getPackageName().lastIndexOf(".") + 1)));
        TestFeatures.initialize(this);
        ListView listView = findViewById(R.id.list);
        listView.setAdapter(new TestFeaturesListAdapter(TestFeatures.getAvailableControls()));
        listView.setOnItemClickListener(TestFeatures.getOnItemClickListener());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
        }
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(LOG_TAG, "onNewIntent triggered");
        Push.checkLaunchedFromNotification(this, intent);
    }

    private AnalyticsListener getAnalyticsListener() {
        return new SasquatchAnalyticsListener(this);
    }

    @NonNull
    private CrashesListener getCrashesListener() {
        return new SasquatchCrashesListener(this);
    }

    @NonNull
    private PushListener getPushListener() {
        return new SasquatchPushListener();
    }
}
