package com.androlua;

import android.app.Activity;

public class LuaActivity extends Activity {
    public static void logError(String name, Throwable e) {
        android.util.Log.e("LuaActivity", name, e);
    }
}
