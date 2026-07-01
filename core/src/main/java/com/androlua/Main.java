package com.androlua;

import android.content.Intent;
import android.os.Bundle;

public class Main extends LuaActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        DebugLogger.log("Main", "onCreate 开始");
        // TODO: Implement this method
        super.onCreate(savedInstanceState);
        DebugLogger.log("Main", "super.onCreate 完成");
        if (savedInstanceState == null && getIntent().getData() != null)
            runFunc("onNewIntent", getIntent());
        if (getIntent().getBooleanExtra("isVersionChanged", false) && (savedInstanceState == null)) {
            onVersionChanged(getIntent().getStringExtra("newVersionName"), getIntent().getStringExtra("oldVersionName"));
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // TODO: Implement this method
        runFunc("onNewIntent", intent);
        super.onNewIntent(intent);
    }

    @Override
    public String getLuaDir() {
        // TODO: Implement this method
        return getLocalDir();
    }

    @Override
    public String getLuaPath() {
        // TODO: Implement this method
        initMain();
        return getLocalDir() + "/main.lua";
    }

    private void onVersionChanged(String newVersionName, String oldVersionName) {
        // TODO: Implement this method
        runFunc("onVersionChanged", newVersionName, oldVersionName);

    }


}
