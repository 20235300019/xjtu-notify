package com.xjtu.notify.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val ds: DataStore<Preferences>) {
    companion object {
        val K_API_KEY = stringPreferencesKey("api_key")
        val K_RSS_URL = stringPreferencesKey("rss_url")
        val K_BASE_URL = stringPreferencesKey("base_url")
        val K_MODEL = stringPreferencesKey("model")
        val K_STAGE = stringPreferencesKey("stage")
        val K_GRADE = stringPreferencesKey("grade")
        val K_MAJOR = stringPreferencesKey("major")
        val K_INTENTIONS = stringPreferencesKey("intentions")
        val K_FREE = stringPreferencesKey("profile_free")
        val K_SITES = stringPreferencesKey("enabled_sites")
    }

    val snapshot: Flow<Map<String, String>> = ds.data.map { p ->
        mapOf(
            "api_key" to (p[K_API_KEY] ?: ""),
            "rss_url" to (p[K_RSS_URL] ?: ""),
            "base_url" to (p[K_BASE_URL] ?: "https://dashscope.aliyuncs.com/compatible-mode/v1"),
            "model" to (p[K_MODEL] ?: "qwen-plus"),
            "stage" to (p[K_STAGE] ?: "本科生"),
            "grade" to (p[K_GRADE] ?: ""),
            "major" to (p[K_MAJOR] ?: ""),
            "intentions" to (p[K_INTENTIONS] ?: ""),
            "profile_free" to (p[K_FREE] ?: ""),
            "enabled_sites" to (p[K_SITES] ?: SiteDef.entries.joinToString(",") { it.id })
        )
    }

    suspend fun put(key: Preferences.Key<String>, value: String) {
        ds.edit { it[key] = value }
    }

    fun profileText(m: Map<String, String>): String {
        val parts = mutableListOf("${m["stage"]}", m["grade"]?.takeIf { it.isNotEmpty() }?.plus("级") ?: "",
            m["major"]?.takeIf { it.isNotEmpty() }?.let { "专业：$it" } ?: "")
        if (m["intentions"].orEmpty().isNotEmpty()) parts += "意向：${m["intentions"]}"
        if (m["profile_free"].orEmpty().isNotEmpty()) parts += m["profile_free"]!!
        return parts.filter { it.isNotBlank() }.joinToString("，")
    }
}
