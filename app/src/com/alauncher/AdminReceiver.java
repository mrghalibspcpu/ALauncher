package com.alauncher;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/** Enables the double-tap-to-lock gesture. */
public class AdminReceiver extends DeviceAdminReceiver {

    public static ComponentName component(Context c) {
        return new ComponentName(c, AdminReceiver.class);
    }

    public static boolean isActive(Context c) {
        DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isAdminActive(component(c));
    }

    public static void request(Context c) {
        Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(c));
        i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Allow A Launcher to lock the screen when you double tap the home screen.");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    public static boolean lock(Context c) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null && dpm.isAdminActive(component(c))) { dpm.lockNow(); return true; }
        } catch (Throwable ignored) { }
        return false;
    }
}
