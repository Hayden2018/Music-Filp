package com.google.mediapipe.examples.facemesh;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
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

import java.util.List;

/** Main activity of MediaPipe Face Mesh app. */
public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {

  private FaceMesh facemesh;
  private CameraInput cameraInput;
  private static final boolean RUN_ON_GPU = true;

  private SVMDetector detector;

  private boolean detectionEnable = true;
  private boolean blinkEnable = true;
  private boolean eyeCloseEnable = true;
  private boolean shakeEnable = false;

    private static final int DEFAULT_BLINK_SENSITIVITY = 50;
    private float blinkSensitivity = DEFAULT_BLINK_SENSITIVITY;

    private static final int DEFAULT_EYE_CLOSE_DURATION = 2;
    private int eyeCloseDuration = DEFAULT_EYE_CLOSE_DURATION;

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

  private enum Current {
    COLLECTION,
    VIEW,
    SETTING
  }

  Enum<Current> current = Current.VIEW;

  public ViewFragment viewFragment;
  public CollectionFragment collectionFragment = CollectionFragment.newInstance();
  public SettingsFragment settingFragment = new SettingsFragment();

  private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.view_button);
        bottomNavigationView.setOnNavigationItemSelectedListener(this);

        viewFragment = (ViewFragment) getSupportFragmentManager().findFragmentById(R.id.fragment);

        detector = new SVMDetector(getResources());
        loadSettings();

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
  public boolean onNavigationItemSelected(@NonNull MenuItem item) {

    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

    switch (item.getItemId()) {
      case R.id.collections_button:
        current = Current.COLLECTION;
        transaction.replace(R.id.fragment, collectionFragment).commit();
        closeCamera();
        return true;

      case R.id.view_button:
        current = Current.VIEW;
        transaction.replace(R.id.fragment, viewFragment).commit();
        if (detectionEnable) startCamera();
        return true;

      case R.id.settings_button:
        current = Current.SETTING;
        transaction.replace(R.id.fragment, settingFragment).addToBackStack(null).commit();
        closeCamera();
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
                    .setRunOnGpu(RUN_ON_GPU)
                    .build());
        facemesh.setErrorListener((message, e) -> Log.e("MediaPipe Error", message));

        facemesh.setResultListener(
            faceMeshResult -> {
                if (!detectionEnable || faceMeshResult.multiFaceLandmarks().isEmpty()) {
                    return;
                }
                if (System.currentTimeMillis() - coolDown < 1000) {
                    return;
                }
                List<NormalizedLandmark> landmarks = faceMeshResult.multiFaceLandmarks().get(0).getLandmarkList();
                detector.setTransformMatrix(landmarks);

                if (blinkEnable) {
                    Enum<Shake> shake = detector.detectShake(landmarks);
                    if (shake != Shake.NONE) return;

                    Enum<Blink> blink = detector.detectBlink(landmarks);
                    if (blink == Blink.LEFT) triggerLeft();
                    if (blink == Blink.RIGHT) triggerRight();
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

        blinkEnable = sharedPreferences.getBoolean("blink_preference", true);
        eyeCloseEnable = sharedPreferences.getBoolean("eye_closing_preference", true);
        shakeEnable = sharedPreferences.getBoolean("detect_head_shake_preference", false);

        blinkSensitivity = sharedPreferences.getInt("blink_sensitivity_preference", DEFAULT_BLINK_SENSITIVITY) / 100f;
        detector.setSensitivity(blinkSensitivity);

        eyeCloseDuration = sharedPreferences.getInt("eye_closing_duration_preference", DEFAULT_EYE_CLOSE_DURATION);
    }

}
