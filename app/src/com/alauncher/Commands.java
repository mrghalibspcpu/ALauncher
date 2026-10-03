package com.alauncher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The ":" command palette — launcher super powers from the keyboard. */
public class Commands {

    public static final String[] ALL = {
            "torch — toggle the flashlight",
            "focus — start / stop 30 min Focus Mode",
            "wifi — open Wi-Fi panel",
            "bluetooth — open Bluetooth settings",
            "settings — A Launcher settings",
            "wallpaper — change wallpaper",
            "widgets — add a widget",
            "hidden — manage hidden apps",
            "timeline — Time Machine usage graph",
            "orbit — edit Orbit favourites",
            "lock — lock the screen",
            "restart — restart the launcher",
            "web — search the web",
            "sysinfo — device vitals"
    };

    public static List<String> suggest(String q) {
        List<String> out = new ArrayList<>();
        String s = q == null ? "" : q.trim().toLowerCase(Locale.US);
        for (String c : ALL) {
            if (s.isEmpty() || c.startsWith(s) || c.contains(" " + s)) out.add(c);
        }
        if (out.isEmpty()) for (String c : ALL) out.add(c);
        return out;
    }
}
