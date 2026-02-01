/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

@file:Suppress("DEPRECATION")

package com.google.ai.edge.gallery.common

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val TAG = "SecureStorage"

/**
 * Encrypted storage for sensitive data.
 * 
 * Uses Android's EncryptedSharedPreferences with AES256-GCM encryption.
 * Stores access tokens and other sensitive information securely.
 */
object SecureStorage {

    private const val PREFS_NAME = "secure_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_TOKEN_EXPIRY = "token_expiry"

    private var prefs: SharedPreferences? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return

        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            isInitialized = true
            Log.d(TAG, "SecureStorage initialized with encrypted preferences")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SecureStorage", e)
            // Fall back to regular SharedPreferences if encryption fails
            prefs = context.getSharedPreferences(PREFS_NAME + "_fallback", Context.MODE_PRIVATE)
            isInitialized = true
            Log.w(TAG, "Using fallback storage (not encrypted)")
        }
    }

    /**
     * Store access token securely.
     */
    var accessToken: String?
        get() = prefs?.getString(KEY_ACCESS_TOKEN, null)
        set(value) {
            prefs?.edit()?.apply {
                if (value != null) {
                    putString(KEY_ACCESS_TOKEN, value)
                } else {
                    remove(KEY_ACCESS_TOKEN)
                }
                apply()
            }
        }

    /**
     * Store refresh token securely.
     */
    var refreshToken: String?
        get() = prefs?.getString(KEY_REFRESH_TOKEN, null)
        set(value) {
            prefs?.edit()?.apply {
                if (value != null) {
                    putString(KEY_REFRESH_TOKEN, value)
                } else {
                    remove(KEY_REFRESH_TOKEN)
                }
                apply()
            }
        }

    /**
     * Store token expiry time.
     */
    var tokenExpiry: Long
        get() = prefs?.getLong(KEY_TOKEN_EXPIRY, 0L) ?: 0L
        set(value) {
            prefs?.edit()?.putLong(KEY_TOKEN_EXPIRY, value)?.apply()
        }

    /**
     * Check if token is valid (not expired).
     */
    fun isTokenValid(): Boolean {
        val expiry = tokenExpiry
        return expiry > 0 && expiry > System.currentTimeMillis()
    }

    /**
     * Store a custom key-value pair securely.
     */
    fun putString(key: String, value: String?) {
        prefs?.edit()?.apply {
            if (value != null) {
                putString(key, value)
            } else {
                remove(key)
            }
            apply()
        }
    }

    /**
     * Retrieve a custom string value.
     */
    fun getString(key: String, defaultValue: String? = null): String? {
        return prefs?.getString(key, defaultValue)
    }

    /**
     * Clear all stored credentials.
     */
    fun clearCredentials() {
        prefs?.edit()?.apply {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_TOKEN_EXPIRY)
            apply()
        }
        Log.d(TAG, "All credentials cleared")
    }

    /**
     * Clear all secure storage data.
     */
    fun clearAll() {
        prefs?.edit()?.clear()?.apply()
        Log.d(TAG, "All secure storage cleared")
    }
}
