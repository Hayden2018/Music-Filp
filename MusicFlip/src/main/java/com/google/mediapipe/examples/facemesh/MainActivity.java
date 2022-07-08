// Copyright 2021 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.mediapipe.examples.facemesh;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.MenuItem;

// ContentResolver dependency
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.solutioncore.CameraInput;
import com.google.mediapipe.solutions.facemesh.FaceMesh;
import com.google.mediapipe.solutions.facemesh.FaceMeshOptions;
import com.google.mediapipe.solutions.facemesh.FaceMeshResult;

import java.io.FileNotFoundException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static java.lang.Math.abs;

/** Main activity of MediaPipe Face Mesh app. */
public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {

  private FaceMesh facemesh;
  private CameraInput cameraInput;
  private static final boolean RUN_ON_GPU = true;
  private boolean detectionEnable = true;
  private boolean blinkEnable = true;
  private boolean shakeEnable = false;

  private static final int DEFAULT_BLINK_SENSITIVITY = 80;
  private int blinkSensitivity = DEFAULT_BLINK_SENSITIVITY;

  protected long coolDown = System.currentTimeMillis();

  private enum Source {
    SHAKE,
    BLINK
  }

  private enum Shake {
    LEFT,
    NONE,
    RIGHT
  }

  private enum Blink {
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
  public SettingsFragment settingFragment = SettingsFragment.newInstance();

  private BottomNavigationView bottomNavigationView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    setupLiveDemoUiComponents();

    loadSettings();

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
        Enum<Blink> blink = detectBlink(faceMeshResult);
        Enum<Shake> shake = detectShake(faceMeshResult);
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

  final List<Integer> rightEyeUpper = Arrays.asList(398, 384, 385, 386, 387, 388, 466);
  final List<Integer> rightEyeLower = Arrays.asList(382, 381, 380, 374, 373, 390, 249);
  final List<Integer> leftEyeUpper = Arrays.asList(246, 161, 160, 159, 158, 157, 173);
  final List<Integer> leftEyeLower = Arrays.asList(7, 163, 144, 145, 153, 154, 155);

  private Enum<Blink> detectBlink(FaceMeshResult result) {

    if (!blinkEnable) return Blink.NONE;

    List<NormalizedLandmark> landmarks = result.multiFaceLandmarks().get(0).getLandmarkList();
    float right = 0;
    float left = 0;
    float tilt = landmarks.get(0).getX() - landmarks.get(164).getX();

    for (int i = 0; i < 7; ++i) {
      right += abs(landmarks.get(rightEyeUpper.get(i)).getY() - landmarks.get(rightEyeLower.get(i)).getY());
      left += abs(landmarks.get(leftEyeUpper.get(i)).getY() - landmarks.get(leftEyeLower.get(i)).getY());
    }

    if (right > (left + 0.03) && tilt < -0.008) return Blink.LEFT;
    if (left > (right + 0.03) && tilt > 0.008) return Blink.RIGHT;
    return Blink.NONE;
  }

  private Enum<Shake> detectShake(FaceMeshResult result) {

    List<NormalizedLandmark> landmarks = result.multiFaceLandmarks().get(0).getLandmarkList();

    float dz = landmarks.get(454).getZ() - landmarks.get(234).getZ();
    if (dz > 0.12) return Shake.RIGHT;
    if (dz < -0.12) return Shake.LEFT;
    return Shake.NONE;
  }

  private void loadSettings(){
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    shakeEnable = sharedPreferences.getBoolean("detect_head_shake_preference", false);
    blinkSensitivity = sharedPreferences.getInt("blink_sensitivity_preference", DEFAULT_BLINK_SENSITIVITY);
  }


}
