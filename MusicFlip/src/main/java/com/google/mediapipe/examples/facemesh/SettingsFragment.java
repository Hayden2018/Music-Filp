package com.google.mediapipe.examples.facemesh;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    private MainActivity activity;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (MainActivity) getActivity();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        Preference aboutAppPref = findPreference("about_app_preference");
        if (aboutAppPref != null && getContext() != null) {
            aboutAppPref.setOnPreferenceClickListener(preference -> {
                new MaterialAlertDialogBuilder(getContext())
                        .setTitle(getString(R.string.about_app_title))
                        .setMessage(getString(R.string.about_app_content))
                        .setPositiveButton(R.string.confirm, null)
                        .show();
                return true;
            });
        }

        Preference getHelpPref = findPreference("get_help_preference");
        if (getHelpPref != null && getContext() != null) {
            getHelpPref.setOnPreferenceClickListener(preference -> {
                new MaterialAlertDialogBuilder(getContext())
                        .setTitle(getString(R.string.get_help_title))
                        .setMessage(Html.fromHtml(getString(R.string.get_help_content)))
                        .setPositiveButton(R.string.confirm, null)
                        .show();
                return true;
            });
        }

        Preference sharePref = findPreference("share_preference");
        if (sharePref != null && getContext() != null) {
            sharePref.setOnPreferenceClickListener(preference -> {
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_content));
                sendIntent.setType("text/plain");

                Intent shareIntent = Intent.createChooser(sendIntent, null);
                startActivity(shareIntent);
                return true;
            });
        }

        SwitchPreferenceCompat blinkPref = findPreference("blink_preference");
        SwitchPreferenceCompat eyeClosePref = findPreference("eye_closing_preference");

        eyeClosePref.setOnPreferenceChangeListener((preference, newVal) -> {
            boolean value = (Boolean) newVal;

            if (value) {
                Log.i("TAG", "true");
                blinkPref.setChecked(false);
            } else {
                Log.i("TAG", "false");
                blinkPref.setChecked(true);
            }
            return true;
        });


        blinkPref.setOnPreferenceChangeListener((preference, newVal) -> {
            boolean value = (Boolean) newVal;

            if (value) {
                eyeClosePref.setChecked(false);
            } else {
                eyeClosePref.setChecked(true);
            }
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        activity.loadSettings();
    }
}