/*
 * Copyright (c) 2018 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger.ui;

import static net.fabiszewski.ulogger.ui.SettingsActivity.KEY_AUTO_NAME; // auto naming track logs
import static net.fabiszewski.ulogger.ui.SettingsActivity.KEY_AUTO_START;
import static net.fabiszewski.ulogger.ui.SettingsActivity.KEY_PROVIDER;


import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.TwoStatePreference;

import net.fabiszewski.ulogger.Logger;
import net.fabiszewski.ulogger.R;
import net.fabiszewski.ulogger.db.DbAccess;
import net.fabiszewski.ulogger.utils.PermissionHelper;

public class SettingsFragment extends PreferenceFragmentCompat implements PermissionHelper.PermissionRequester {

    private static final String TAG = SettingsFragment.class.getSimpleName();

    final PermissionHelper permissionHelper;

    public SettingsFragment() {
        permissionHelper = new PermissionHelper(this, this);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        setListeners();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        if (preference instanceof AutoNamePreference && KEY_AUTO_NAME.equals(preference.getKey())) {
            final AutoNamePreferenceDialogFragment fragment = AutoNamePreferenceDialogFragment.newInstance(preference.getKey());
            fragment.setTargetFragment(this, 0);
            fragment.show(getParentFragmentManager(), "AutoNamePreferenceDialogFragment");
        } else if (preference instanceof ListPreference && KEY_PROVIDER.equals(preference.getKey())) {
            final ProviderPreferenceDialogFragment fragment = ProviderPreferenceDialogFragment.newInstance(preference.getKey());
            fragment.setTargetFragment(this, 0);
            fragment.show(getParentFragmentManager(), "ProviderPreferenceDialogFragment");
        } else if (preference instanceof ListPreference) {
            final ListPreferenceDialogWithMessageFragment fragment = ListPreferenceDialogWithMessageFragment.newInstance(preference.getKey());
            fragment.setTargetFragment(this, 0);
            fragment.show(getParentFragmentManager(), "ListPreferenceDialogWithMessageFragment");
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    /**
     * Set various listeners
     */
    private void setListeners() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // On change listener to check permission for background location
            Preference.OnPreferenceChangeListener permissionLevelChanged = (preference, newValue) -> {
                if (Boolean.parseBoolean(newValue.toString())) {
                    if (!permissionHelper.hasBackgroundLocationPermission()) {
                        permissionHelper.requestBackgroundLocationPermission(preference.getKey());
                        return false;
                    }
                }
                return true;
            };
            final Preference prefAutoStart = findPreference(KEY_AUTO_START);
            if (prefAutoStart != null) {
                prefAutoStart.setOnPreferenceChangeListener(permissionLevelChanged);
            }
        }
    }

    /**
     * Enable preference, set checkbox
     * @param context Context
     */
    private void setBooleanPreference(@NonNull Context context, @NonNull String key, boolean isSet) {
        if (Logger.DEBUG) { Log.d(TAG, "[enabling " + key + "]"); }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(key, isSet);
        editor.apply();

        final Preference preference = findPreference(key);
        if (preference instanceof TwoStatePreference) {
            ((TwoStatePreference) preference).setChecked(isSet);
        }
    }

    /**
     * Check whether server setup parameters are set
     * @param context Context
     * @return boolean True if set
     */

    @Override
    public void onPermissionGranted(@Nullable String requestCode) {
        if (Logger.DEBUG) { Log.d(TAG, "[onPermissionGranted: " + requestCode + "]"); }

        Context context = getContext();
        if (context != null && requestCode != null) {
            setBooleanPreference(context, requestCode, true);
        }
    }

    @Override
    public void onPermissionDenied(@Nullable String requestCode) {
        if (Logger.DEBUG) { Log.d(TAG, "[onPermissionGranted: " + requestCode + "]"); }
    }
}
