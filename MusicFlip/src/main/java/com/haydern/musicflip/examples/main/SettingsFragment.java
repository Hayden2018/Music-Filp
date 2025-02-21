package com.haydern.musicflip.examples.main;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.haydern.musicflip.examples.main.R;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    private MainActivity activity;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (MainActivity) getActivity();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        Preference contactPref = findPreference("contact_us_preference");
        if (contactPref != null && getContext() != null) {
            contactPref.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(Intent.ACTION_SENDTO);
                intent.setData(Uri.parse("mailto:yikhei123@gmail.com"));
                intent.putExtra(Intent.EXTRA_SUBJECT, "Music Flip Issues");
                startActivity(intent);
                return true;
            });
        }

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
        SwitchPreferenceCompat nodPref = findPreference("nod_preference");

        nodPref.setOnPreferenceChangeListener((preference, newVal) -> {
            boolean value = (Boolean) newVal;

            if (value) {
                blinkPref.setChecked(false);
            } else {
                blinkPref.setChecked(true);
            }
            return true;
        });

        blinkPref.setOnPreferenceChangeListener((preference, newVal) -> {
            boolean value = (Boolean) newVal;

            if (value) {
                nodPref.setChecked(false);
            } else {
                nodPref.setChecked(true);
            }
            return true;
        });

        ListPreference localePref = findPreference("language_preference");
        localePref.setOnPreferenceChangeListener((preference, newVal) -> {
            Intent refresh = new Intent(getActivity(), MainActivity.class);
            startActivity(refresh);
            getActivity().finish();
            return true;
        });

        ListPreference themePref = findPreference("theme_preference");
        themePref.setOnPreferenceChangeListener(((preference, newValue) -> {
            startActivity(new Intent(getActivity(), MainActivity.class));
            getActivity().finish();
            return true;
        }));

        SwitchPreferenceCompat notificationPref = findPreference("notification_preference");
        notificationPref.setOnPreferenceChangeListener(((preference, newValue) -> {
            boolean value = (Boolean) newValue;
            NotificationManager mNotificationManager = (NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
            if (value) {
                mNotificationManager.cancel(0);
            } else {
                activity.setupNotification();
            }
            return true;
        }));

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