package com.readflow.app.data.local.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureTokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse {
            // 加密初始化异常时降级为普通存储，避免功能不可用。
            context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    fun readCloudSyncToken(legacyToken: String): String {
        val secure = prefs.getString(KEY_CLOUD_TOKEN, null).orEmpty()
        if (secure.isNotBlank()) return secure
        val migrated = legacyToken.trim()
        if (migrated.isNotBlank()) {
            saveCloudSyncToken(migrated)
        }
        return migrated
    }

    fun saveCloudSyncToken(token: String) {
        val normalized = token.trim()
        prefs.edit().apply {
            if (normalized.isBlank()) {
                remove(KEY_CLOUD_TOKEN)
            } else {
                putString(KEY_CLOUD_TOKEN, normalized)
            }
        }.apply()
    }

    companion object {
        private const val FILE_NAME = "readflow_secure_store"
        private const val KEY_CLOUD_TOKEN = "cloud_sync_token"
    }
}
