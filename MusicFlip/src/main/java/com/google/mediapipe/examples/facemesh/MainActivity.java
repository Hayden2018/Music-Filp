package com.google.mediapipe.examples.facemesh;

import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

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

  private Detector detector = new Detector();

  private boolean detectionEnable = true;
  private boolean blinkEnable = true;
  private boolean shakeEnable = false;

  protected long coolDown = System.currentTimeMillis();

  private enum Source {
    SHAKE,
    BLINK
  }

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
  public SettingFragment settingFragment = SettingFragment.newInstance("", "");

  private BottomNavigationView bottomNavigationView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    setupLiveDemoUiComponents();

    bottomNavigationView = findViewById(R.id.bottom_navigation);

    bottomNavigationView.setSelectedItemId(R.id.view_button);
    bottomNavigationView.setOnNavigationItemSelectedListener(this);

    viewFragment = (ViewFragment) getSupportFragmentManager().findFragmentById(R.id.fragment);
  }

  @Override
  public boolean onNavigationItemSelected(@NonNull MenuItem item) {

    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

    switch (item.getItemId()) {
      case R.id.collections_button:
        current = Current.COLLECTION;
        transaction.replace(R.id.fragment, collectionFragment).commit();
        return true;

      case R.id.view_button:
        current = Current.VIEW;
        transaction.replace(R.id.fragment, viewFragment).commit();
        return true;

      case R.id.settings_button:
        current = Current.SETTING;
        transaction.replace(R.id.fragment, settingFragment).commit();
        return true;
    }
    return false;
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (detectionEnable) {
      cameraInput = new CameraInput(this);
      cameraInput.setNewFrameListener(textureFrame -> facemesh.send(textureFrame));
      startCamera();
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (detectionEnable) {
      cameraInput.close();
    }
  }

  protected void triggerRight(Enum<Source> source) {
    coolDown = System.currentTimeMillis();

    if (current == Current.VIEW) {
      viewFragment.nextPage();
    }
  }

  protected void triggerLeft(Enum<Source> source) {
    coolDown = System.currentTimeMillis();

    if (current == Current.VIEW) {
      viewFragment.previousPage();
    }
  }

  public void openAndView(Uri uri) {
    viewFragment.openPDF(uri);
    bottomNavigationView.setSelectedItemId(R.id.view_button);
  }

  /** Sets up the UI components for the live demo with camera input. */
  private void setupLiveDemoUiComponents() {
    setupStreamingModePipeline();
  }

  /** Sets up core workflow for streaming mode. */
  private void setupStreamingModePipeline() {

    facemesh = new FaceMesh(
      this,
      FaceMeshOptions.builder()
        .setStaticImageMode(false)
        .setRefineLandmarks(true)
        .setRunOnGpu(RUN_ON_GPU)
        .build());
    facemesh.setErrorListener((message, e) -> Log.e("MediaPipe Error", message));

    cameraInput = new CameraInput(this);
    cameraInput.setNewFrameListener(textureFrame -> facemesh.send(textureFrame));

    facemesh.setResultListener(
      faceMeshResult -> {
        if (faceMeshResult.multiFaceLandmarks().isEmpty()) {
          return;
        }
        if (System.currentTimeMillis() - coolDown < 1000) {
          return;
        }
        List<NormalizedLandmark> landmarks = faceMeshResult.multiFaceLandmarks().get(0).getLandmarkList();
        detector.setTransformMatrix(landmarks);
        Enum<Blink> blink = detector.detectBlink(landmarks);
        Enum<Shake> shake = detector.detectShake(landmarks);
        if (shake == Shake.LEFT) {
          if (shakeEnable) triggerLeft(Source.SHAKE);
          return;
        }
        if (shake == Shake.RIGHT) {
          if (shakeEnable) triggerRight(Source.SHAKE);
          return;
        }
        if (blink == Blink.LEFT) triggerLeft(Source.BLINK);
        if (blink == Blink.RIGHT) triggerRight(Source.BLINK);
      });

    if (detectionEnable) {
      startCamera();
    }
  }

  private void startCamera() {
    cameraInput.start(
      this,
      facemesh.getGlContext(),
      CameraInput.CameraFacing.FRONT,
      900,
      1200
    );
  }
}
