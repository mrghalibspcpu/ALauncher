package com.alauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Feeds notification dots on icons and the Control Center media card. */
public class NotifService extends NotificationListenerService {

    public static final String ACTION_BADGES = "com.alauncher.BADGES";
    private static NotifService instance;

    @Override public void onListenerConnected() {
        instance = this;
        refresh();
    }

    @Override public void onListenerDisconnected() { instance = null; }

    @Override public void onNotificationPosted(StatusBarNotification sbn) { refresh(); }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) { refresh(); }

    private void refresh() {
        try {
            Map<String, Integer> m = new HashMap<>();
            StatusBarNotification[] ns = getActiveNotifications();
            if (ns != null) {
                for (StatusBarNotification n : ns) {
                    if (n.isOngoing()) continue;
                    Integer c = m.get(n.getPackageName());
                    m.put(n.getPackageName(), c == null ? 1 : c + 1);
                }
            }
            AppStore.badges.clear();
            AppStore.badges.putAll(m);
            sendBroadcast(new Intent(ACTION_BADGES).setPackage(getPackageName()));
        } catch (Throwable ignored) { }
    }

    public static boolean enabled(Context c) {
        String s = android.provider.Settings.Secure.getString(
                c.getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(c.getPackageName());
    }

    public static List<MediaController> sessions(Context c) {
        List<MediaController> out = new ArrayList<>();
        try {
            MediaSessionManager msm = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> l = msm.getActiveSessions(new ComponentName(c, NotifService.class));
            if (l != null) out.addAll(l);
        } catch (Throwable ignored) { }
        return out;
    }
}
