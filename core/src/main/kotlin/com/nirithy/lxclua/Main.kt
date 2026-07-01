package com.nirithy.lxclua

import android.content.Context
import android.content.Intent
import android.os.Bundle

class Main : LuaActivity() {
    override fun onReceive(context: Context?, intent: Intent?) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        DebugLogger.log("Main", "onCreate 开始")
        // TODO: Implement this method
        super.onCreate(savedInstanceState)
        DebugLogger.log("Main", "super.onCreate 完成")
        if (savedInstanceState == null && getIntent().getData() != null) runFunc(
            "onNewIntent",
            getIntent()
        )
        if (getIntent().getBooleanExtra(
                "isVersionChanged",
                false
            ) && (savedInstanceState == null)
        ) {
            onVersionChanged(
                getIntent().getStringExtra("newVersionName"),
                getIntent().getStringExtra("oldVersionName")
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        // TODO: Implement this method
        runFunc("onNewIntent", intent)
        super.onNewIntent(intent)
    }

    override val luaDir: String?
        get() = localDir

    override val luaPath: String?
        get() {
            initMain()
            return localDir + "/main.lua"
        }

    private fun onVersionChanged(newVersionName: String?, oldVersionName: String?) {
        // TODO: Implement this method
        runFunc("onVersionChanged", newVersionName, oldVersionName)
    }
}
