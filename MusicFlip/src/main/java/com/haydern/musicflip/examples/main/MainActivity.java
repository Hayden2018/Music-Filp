package com.haydern.musicflip.examples.main;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import android.os.FileUtils;
import android.provider.OpenableColumns;
import android.text.Html;
import android.util.Log;
import android.view.MenuItem;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.mediapipe.examples.facemesh.R;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.solutioncore.CameraInput;
import com.google.mediapipe.solutions.facemesh.FaceMesh;
import com.google.mediapipe.solutions.facemesh.FaceMeshOptions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static android.content.Intent.ACTION_VIEW;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;
import static java.lang.Math.abs;

public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {

    private FaceMesh facemesh;
    private CameraInput cameraInput;
    private SVMDetector detector;

    public boolean detectionEnable = true;
    public boolean blinkEnable = false;
    public boolean nodEnable = true;
    public boolean soundEffectEnable = true;

    private static final int DEFAULT_BLINK_SENSITIVITY = 50;
    private float blinkSensitivity = DEFAULT_BLINK_SENSITIVITY;

    private static final int DEFAULT_NOD_SENSITIVITY = 50;
    private float nodSensitivity = DEFAULT_NOD_SENSITIVITY;

    private long coolDown = System.currentTimeMillis();

    private boolean notificationEnable = true;

    public enum Shake {
        LEFT,
        NONE,
        RIGHT
    }

    public enum Blink {
        LEFT,
        NONE,
        RIGHT
    }

    public enum Nod {
        UP,
        NONE,
        DOWN
    }

    private enum Current {
        COLLECTION,
        VIEW,
        SETTING
    }

    Enum<Current> current = Current.VIEW;

    public ViewFragment viewFragment = new ViewFragment();
    public CollectionFragment collectionFragment = new CollectionFragment();
    public SettingsFragment settingFragment = new SettingsFragment();

    private BottomNavigationView bottomNavigationView;

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setupTheme();
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        boolean is_first_time_user = pref.getBoolean("IS_FIRST_TIME_USER",true);
        if (is_first_time_user) {
            displayWelcomeMessage(pref);
            setupNotification();
        }

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.view_button);
        bottomNavigationView.setOnNavigationItemSelectedListener(this);

        detector = new SVMDetector(getResources());
        loadSettings();
        setupDetectionPipeline();

        Intent intent = getIntent();
        if (intent.getAction() == ACTION_VIEW) {
            Uri uri = intent.getData();
            try {
                Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                cursor.moveToFirst();
                String name = cursor.getString(abs(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)));
                cursor.close();
                FileOutputStream destination = openFileOutput(name, Context.MODE_PRIVATE);
                FileInputStream source = (FileInputStream) getContentResolver().openInputStream(uri);
                FileUtils.copy(source, destination);
                openAndView(Uri.fromFile(new File(getFilesDir(), name)));
            } catch (IOException e) {
                Log.e("File Save", e.getMessage());
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (detectionEnable && current == Current.VIEW) startCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeCamera();
    }

    @Override
    public void setContentView(int layoutResID) {
        setLocale();
        super.setContentView(layoutResID);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        switch (item.getItemId()) {
            case R.id.collections_button:
                current = Current.COLLECTION;
                transaction.replace(R.id.fragment, collectionFragment).commit();
                closeCamera();
                setTitle(R.string.collections_title);
                return true;

            case R.id.view_button:
                current = Current.VIEW;
                transaction.replace(R.id.fragment, viewFragment).commit();
                if (detectionEnable) startCamera();
                setTitle(R.string.view_title);
                return true;

            case R.id.settings_button:
                current = Current.SETTING;
                transaction.replace(R.id.fragment, settingFragment).addToBackStack(null).commit();
                closeCamera();
                setTitle(R.string.setting_title);
                return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
            super.onBackPressed();
        } else {
            finish();
        }
    }

    protected void triggerRight() {
        coolDown = System.currentTimeMillis();

        if (current == Current.VIEW) {
            viewFragment.nextPage();
        }
    }

    protected void triggerLeft() {
        coolDown = System.currentTimeMillis();

        if (current == Current.VIEW) {
            viewFragment.previousPage();
        }
    }

    public void openAndView(Uri uri) {
        viewFragment.openPDF(uri);
        bottomNavigationView.setSelectedItemId(R.id.view_button);
    }

    private void setupDetectionPipeline() {

        facemesh = new FaceMesh(
                this,
                FaceMeshOptions.builder()
                        .setStaticImageMode(false)
                        .setRefineLandmarks(true)
                        .setRunOnGpu(true)
                        .build());
        facemesh.setErrorListener((message, e) -> Log.e("MediaPipe Error", message));

        facemesh.setResultListener(
                faceMeshResult -> {
                    if (!detectionEnable || faceMeshResult.multiFaceLandmarks().isEmpty()) {
                        return;
                    }
                    if (System.currentTimeMillis() - coolDown < 1200) {
                        return;
                    }
                    List<NormalizedLandmark> landmarks = faceMeshResult.multiFaceLandmarks().get(0).getLandmarkList();
                    detector.setTransformMatrix(landmarks);

                    Enum<Shake> shake = detector.detectShake(landmarks);
                    if (shake != Shake.NONE) return;

                    if (blinkEnable) {
                        Enum<Blink> blink = detector.detectBlink(landmarks);
                        if (blink == Blink.LEFT) triggerLeft();
                        if (blink == Blink.RIGHT) triggerRight();
                    }

                    if (nodEnable) {
                        Enum<Nod> knock = detector.detectNod(landmarks);
                        if (knock == Nod.DOWN) triggerRight();
                    }
                });

        if (detectionEnable) {
            startCamera();
        }
    }

    private void startCamera() {
        cameraInput = new CameraInput(this);
        cameraInput.setNewFrameListener(textureFrame -> facemesh.send(textureFrame));
        cameraInput.start(
                this,
                facemesh.getGlContext(),
                CameraInput.CameraFacing.FRONT,
                600,
                800
        );
    }

    private void closeCamera() {
        if (cameraInput != null) {
            cameraInput.close();
            cameraInput = null;
        }
    }

    // load settings by reading the shared preferences
    protected void loadSettings() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        detectionEnable = sharedPreferences.getBoolean("ai_detector_preference", true);

        blinkEnable = sharedPreferences.getBoolean("blink_preference", false);
        blinkSensitivity = sharedPreferences.getInt("blink_sensitivity_preference", DEFAULT_BLINK_SENSITIVITY) * 0.05f;
        detector.setBlinkSensitivity(blinkSensitivity);

        nodEnable = sharedPreferences.getBoolean("nod_preference", true);
        nodSensitivity = sharedPreferences.getInt("nod_sensitivity_preference", DEFAULT_NOD_SENSITIVITY) * 0.02f;
        detector.setNodSensitivity(nodSensitivity);

        soundEffectEnable = sharedPreferences.getBoolean("sound_effect_preference", true);

        boolean incomingSetting = sharedPreferences.getBoolean("notification_preference", true);
        if (incomingSetting == true && notificationEnable == false) {
            setupNotification();
            notificationEnable = true;
        }
        if (incomingSetting == false && notificationEnable == true) {
            cancelNotification();
            notificationEnable = false;
        }
    }

    @SuppressWarnings("deprecation")
    public void setLocale() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String lang = sharedPreferences.getString("language_preference", "");

        Locale myLocale;
        if (lang.equals("zh-rTW")) {
            myLocale = Locale.TAIWAN;
        } else if (lang.equals("en")) {
            myLocale = new Locale(lang);
        } else {
            return;
        }
        Locale.setDefault(myLocale);
        Configuration config = new Configuration();
        config.locale = myLocale;
        this.getResources().updateConfiguration(config, this.getResources().getDisplayMetrics());
    }

    protected void setupTheme() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String theme = sharedPreferences.getString("theme_preference", "");

        if (theme.equals("dark")) {
            AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_YES);
        } else if (theme.equals("light")) {
            AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    public void setupNotification() {

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 20);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        if (calendar.getTime().compareTo(new Date()) < 0)
            calendar.add(Calendar.DAY_OF_MONTH, 1);

        Intent intent = new Intent(getApplicationContext(), NotificationReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        if (alarmManager != null) {
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntent);
        }
    }

    public void cancelNotification() {

        Intent intent = new Intent(getApplicationContext(), NotificationReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }

    @SuppressWarnings("deprecation")
    private void displayWelcomeMessage(SharedPreferences pref){
        SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean("IS_FIRST_TIME_USER", false);
        editor.apply();

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.welcome_title))
                .setMessage(Html.fromHtml(getString(R.string.welcome_content)))
                .setPositiveButton(R.string.confirm, null)
                .show();
    }

}