package com.zj.dashcam

import android.util.Log

class Logger(prefix: String) {
    private val tag = "Dashcam"
    private val fullPrefix = "[$prefix] "

    fun v(message: String) {
        if (isV()) {
            Log.v(tag, fullPrefix + message)
        }
    }

    fun v(message: String, throwable: Throwable) {
        if (isV()) {
            Log.v(tag, fullPrefix + message, throwable)
        }
    }

    fun d(message: String) {
        if (isD()) {
            Log.d(tag, fullPrefix + message)
        }
    }

    fun d(message: String, throwable: Throwable) {
        if (isD()) {
            Log.d(tag, fullPrefix + message, throwable)
        }
    }

    fun i(message: String) {
        if (isI()) {
            Log.i(tag, fullPrefix + message)
        }
    }

    fun i(message: String, throwable: Throwable) {
        if (isI()) {
            Log.i(tag, fullPrefix + message, throwable)
        }
    }

    fun w(message: String) {
        Log.w(tag, fullPrefix + message)
    }

    fun w(message: String, throwable: Throwable) {
        Log.w(tag, fullPrefix + message, throwable)
    }

    fun e(message: String) {
        Log.e(tag, fullPrefix + message)
    }

    fun e(message: String, throwable: Throwable) {
        Log.e(tag, fullPrefix + message, throwable)
    }

    fun wtf(message: String) {
        Log.wtf(tag, fullPrefix + message)
    }

    fun wtf(message: String, throwable: Throwable) {
        Log.wtf(tag, fullPrefix + message, throwable)
    }

    protected fun forceAllLogging(): Boolean = false

    private fun isV(): Boolean = Log.isLoggable(tag, Log.VERBOSE) || forceAllLogging()

    private fun isD(): Boolean = Log.isLoggable(tag, Log.DEBUG) || forceAllLogging()

    private fun isI(): Boolean = Log.isLoggable(tag, Log.INFO) || forceAllLogging()
}
