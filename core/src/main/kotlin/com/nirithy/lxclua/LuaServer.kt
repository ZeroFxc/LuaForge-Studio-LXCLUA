package com.nirithy.lxclua

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket

/**
 * Created by Administrator on 2017/10/20 0020.
 */
class LuaServer : LuaGcable {
    private var mServerSocket: ServerSocket? = null
    private var mOnReadLineListener: OnReadLineListener? = null
    private var mGc = false

    constructor(context: LuaContext) {
        context.regGc(this)
    }

    constructor()

    fun start(port: Int): Boolean {
        if (mServerSocket != null) return false
        try {
            mServerSocket = ServerSocket(port)
            ServerThread(mServerSocket).start()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun stop(): Boolean {
        try {
            mServerSocket!!.close()
            mServerSocket = null
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun setOnReadLineListener(listener: OnReadLineListener?) {
        mOnReadLineListener = listener
    }

    override fun gc() {
        if (mServerSocket == null) return
        try {
            mServerSocket!!.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mGc = true
    }

    override val isGc get() = mGc

    private inner class ServerThread(private val mServer: ServerSocket?) : Thread() {
        override fun run() {
            while (true) {
                try {
                    val socket = mServerSocket!!.accept()
                    SocketThread(socket).start()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private inner class SocketThread(private val mSocket: Socket) : Thread() {
        private var out: BufferedWriter? = null

        override fun run() {
            try {
                val `in` = BufferedReader(InputStreamReader(mSocket.getInputStream()))
                out = BufferedWriter(OutputStreamWriter(mSocket.getOutputStream()))
                var line: String?
                while ((`in`.readLine().also { line = it }) != null) {
                    if (mOnReadLineListener != null) mOnReadLineListener!!.onReadLine(
                        this@LuaServer,
                        this,
                        line
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun write(text: String?): Boolean {
            try {
                out!!.write(text)
                return true
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }

        fun flush(): Boolean {
            try {
                out!!.flush()
                return true
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }

        fun newLine(): Boolean {
            try {
                out!!.newLine()
                out!!.flush()
                return true
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }

        fun close(): Boolean {
            try {
                mSocket.close()
                return true
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }
    }

    interface OnReadLineListener {
        fun onReadLine(server: LuaServer?, socket: Thread?, line: String?)
    }
}
