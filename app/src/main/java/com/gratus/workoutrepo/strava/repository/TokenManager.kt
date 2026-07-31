package com.gratus.workoutrepo.strava.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object TokenManager {
    private const val PREFS_FILENAME = "intervals_secure_prefs"
    private const val KEY_CLIENT_ID = "strava_client_id"
    private const val KEY_CLIENT_SECRET = "strava_client_secret"
    private const val KEY_REFRESH_TOKEN = "strava_refresh_token"
    private const val KEY_STRAVA_ATHLETE_ID = "strava_athlete_id"

    private var cachedClientId: String? = null
    private var cachedClientSecret: String? = null
    private var cachedRefreshToken: String? = null
    private var cachedAthleteId: String? = null

    // This one expires every 6 hours. We update it automatically.
    @JvmField
    var accessToken: String? = null

    var accessKeyword = "Hobdy"

    private fun getSecurePrefs(context: Context) = EncryptedSharedPreferences.create(
        PREFS_FILENAME,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    @JvmStatic
    fun getClientId(context: Context): String {
        if (cachedClientId == null) {
            val saved = getSecurePrefs(context).getString(KEY_CLIENT_ID, null)
            cachedClientId = saved ?: ""
        }
        return cachedClientId!!
    }

    @JvmStatic
    fun getClientSecret(context: Context): String {
        if (cachedClientSecret == null) {
            val saved = getSecurePrefs(context).getString(KEY_CLIENT_SECRET, null)
            cachedClientSecret = saved ?: ""
        }
        return cachedClientSecret!!
    }

    @JvmStatic
    fun getRefreshToken(context: Context): String {
        if (cachedRefreshToken == null) {
            val saved = getSecurePrefs(context).getString(KEY_REFRESH_TOKEN, null)
            cachedRefreshToken = saved ?: ""
        }
        return cachedRefreshToken!!
    }

    @JvmStatic
    fun getStravaAthleteId(context: Context): String? {
        if (cachedAthleteId == null) {
            cachedAthleteId = getSecurePrefs(context).getString(KEY_STRAVA_ATHLETE_ID, null)
        }
        return cachedAthleteId
    }

    @JvmStatic
    fun saveStravaAthleteId(context: Context, athleteId: String) {
        getSecurePrefs(context).edit().putString(KEY_STRAVA_ATHLETE_ID, athleteId).apply()
        cachedAthleteId = athleteId
    }

    @JvmStatic
    fun hasValidCredentials(context: Context): Boolean {
        return getClientId(context).isNotBlank() &&
               getClientSecret(context).isNotBlank() &&
               getRefreshToken(context).isNotBlank()
    }

    @JvmStatic
    fun saveCredentials(context: Context, clientId: String, clientSecret: String, refreshToken: String) {
        getSecurePrefs(context).edit()
            .putString(KEY_CLIENT_ID, clientId)
            .putString(KEY_CLIENT_SECRET, clientSecret)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()

        cachedClientId = clientId
        cachedClientSecret = clientSecret
        cachedRefreshToken = refreshToken
        accessToken = null
    }

    @JvmStatic
    fun saveRefreshToken(context: Context, newRefreshToken: String) {
        getSecurePrefs(context).edit().putString(KEY_REFRESH_TOKEN, newRefreshToken).apply()
        cachedRefreshToken = newRefreshToken
    }
}