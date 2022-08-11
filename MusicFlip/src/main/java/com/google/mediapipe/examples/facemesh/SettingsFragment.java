package com.google.mediapipe.examples.facemesh;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.telecom.Call;
import android.text.Html;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    private MainActivity activity;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (MainActivity) getActivity();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        getActivity().setTitle(R.string.setting_title);
        return super.onCreateView(inflater, container, savedInstanceState);
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

        ListPreference localePref = findPreference("language_preference");
        localePref.setOnPreferenceChangeListener((preference, newVal) -> {
            String lang = newVal.toString();
            Intent refresh = new Intent(getActivity(), MainActivity.class);

            startActivity(refresh);
            getActivity().finish();
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