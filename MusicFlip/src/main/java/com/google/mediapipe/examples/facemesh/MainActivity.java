package com.google.mediapipe.examples.facemesh;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import android.util.Log;
import android.view.MenuItem;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.solutioncore.CameraInput;
import com.google.mediapipe.solutions.facemesh.FaceMesh;
import com.google.mediapipe.solutions.facemesh.FaceMeshOptions;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Main activity of MediaPipe Face Mesh app.
 */
public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {

    private FaceMesh facemesh;
    private CameraInput cameraInput;
    private SVMDetector detector;

    private boolean detectionEnable = true;
    private boolean blinkEnable = true;
    private boolean eyeCloseEnable = true;
    private boolean soundEffectEnable = true;

    private static final int DEFAULT_BLINK_SENSITIVITY = 50;
    private float blinkSensitivity = DEFAULT_BLINK_SENSITIVITY;

    private static final int DEFAULT_EYE_CLOSE_DURATION = 2;
    private int eyeCloseDuration = DEFAULT_EYE_CLOSE_DURATION;

    private boolean cameraOn = false;

    protected long coolDown = System.currentTimeMillis();

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

    public enum Knock {
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
    public CollectionFragment collectionFragment = CollectionFragment.newInstance();
    public SettingsFragment settingFragment = new SettingsFragment();

    private BottomNavigationView bottomNavigationView;

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupTheme();
        setContentView(R.layout.activity_main);

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.view_button);
        bottomNavigationView.setOnNavigationItemSelectedListener(this);

//        viewFragment = (ViewFragment) getSupportFragmentManager().findFragmentById(R.id.fragment);

        detector = new SVMDetector(getResources());
        loadSettings();
        setupNotification();

        setupDetectionPipeline();
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

    @RequiresApi(api = Build.VERSION_CODES.N)
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

                    if (eyeCloseEnable) {
                        Enum<Knock> knock = detector.detectKnock(landmarks);
                        if (knock == Knock.DOWN) triggerRight();
//                        if (knock != Knock.NONE)  {detector.refresh(); return;}
//                        if (detector.detectClose(landmarks)) {
//                            triggerRight();
//                        }
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
        cameraOn = true;
    }

    private void closeCamera() {
        if (cameraInput != null) {
            cameraInput.close();
            cameraInput = null;
            cameraOn = false;
        }
    }

    // load settings by reading the shared preferences
    protected void loadSettings() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        detectionEnable = sharedPreferences.getBoolean("ai_detector_preference", true);

        blinkEnable = sharedPreferences.getBoolean("blink_preference", true);
        blinkSensitivity = sharedPreferences.getInt("blink_sensitivity_preference", DEFAULT_BLINK_SENSITIVITY) / 100f;
        detector.setSensitivity(blinkSensitivity);

        eyeCloseEnable = sharedPreferences.getBoolean("eye_closing_preference", true);
        eyeCloseDuration = sharedPreferences.getInt("eye_closing_duration_preference", DEFAULT_EYE_CLOSE_DURATION);
        detector.setCloseDuration(eyeCloseDuration);

        soundEffectEnable = sharedPreferences.getBoolean("sound_effect_preference", true);
    }

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
            setTheme(R.style.DarkTheme);
        } else if (theme.equals("light")) {
            setTheme(R.style.LightTheme);
        } else {
            setTheme(R.style.AppTheme);
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
        PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        if (alarmManager != null) {
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntent);
        }

    }

    protected boolean getCameraOn() {
        return cameraOn;
    }

    protected boolean getSoundEffectEnable() {
        return soundEffectEnable;
    }

}