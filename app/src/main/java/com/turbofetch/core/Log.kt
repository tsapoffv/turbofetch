package com.turbofetch.core

import android.util.Log

/** Единая точка логирования: тег один, формат один — легко искать в logcat. */
object Log {

    private const val TAG = "TurboFetch"

    fun d(message: String) = Log.d(TAG, message)

    fun i(message: String) = Log.i(TAG, message)

    fun w(message: String, throwable: Throwable? = null) =
        if (throwable != null) Log.w(TAG, message, throwable) else Log.w(TAG, message)

    fun e(message: String, throwable: Throwable? = null) =
        if (throwable != null) Log.e(TAG, message, throwable) else Log.e(TAG, message)
}
