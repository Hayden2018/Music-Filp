package com.haydern.musicflip.examples.main;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.Locale;

public class NotificationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationHelper notificationHelper = new NotificationHelper(context);
        setLocale(context);
        notificationHelper.createNotification();

    }

    private void setLocale(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String lang = sharedPreferences.getString("language_preference", "");
        Log.i("TAG", "setContentView: " + lang);

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
        context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
    }
}
