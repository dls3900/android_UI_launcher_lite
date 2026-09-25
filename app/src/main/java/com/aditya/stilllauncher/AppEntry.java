package com.aditya.stilllauncher;

import android.content.ComponentName;
import android.content.pm.LauncherActivityInfo;
import android.os.UserHandle;

final class AppEntry {
    final LauncherActivityInfo info;
    final ComponentName component;
    final UserHandle user;
    final String label;
    final String key;
    final String section;

    AppEntry(LauncherActivityInfo info) {
        this.info = info;
        component = info.getComponentName();
        user = info.getUser();
        label = info.getLabel().toString();
        key = component.flattenToString() + "@" + user.hashCode();
        String first = label.isEmpty() ? "#" : label.substring(0, 1).toUpperCase(java.util.Locale.getDefault());
        section = first.matches("[A-Z]") ? first : "#";
    }
}
