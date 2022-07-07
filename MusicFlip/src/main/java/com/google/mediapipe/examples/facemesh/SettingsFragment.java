package com.google.mediapipe.examples.facemesh;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsFragment extends PreferenceFragmentCompat {

    private MainActivity activity;

    public SettingsFragment() {
        // Required empty public constructor
    }

    public static SettingsFragment newInstance() { return new SettingsFragment();}

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);
    }
}