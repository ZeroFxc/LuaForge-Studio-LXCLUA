package com.nirithy.lxclua

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LuaBroadcastReceiver(private val mRlt: OnReceiveListener) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // TODO: Implement this method
        mRlt.onReceive(context, intent)
    }

    interface OnReceiveListener {
        fun onReceive(context: Context?, intent: Intent?)
    }
}
