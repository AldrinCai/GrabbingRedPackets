package com.aldrin.grabbingredpackets.fragment;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import com.aldrin.grabbingredpackets.R;

public class SettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings_preference);
    }

}
