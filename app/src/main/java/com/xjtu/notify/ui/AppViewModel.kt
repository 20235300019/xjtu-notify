@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.xjtu.notify.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xjtu.notify.App
import com.xjtu.notify.data.CountRow
import com.xjtu.notify.data.Importance
import com.xjtu.notify.data.Notice
import com.xjtu.notify.data.SettingsStore
import com.xjtu.notify.data.SiteDef
import com.xjtu.notify.data.SiteState
import com.xjtu.notify.fetch.FetchManager
import com.xjtu.notify.fetch.FetchMode
import com.xjtu.notify.fetch.FetchProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flatMapLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val dao = (app as App).dao
    val settings = (app as App).settings
    val fetcher: FetchManager = (app as App).fetcher

    val selTab = MutableStateFlow(0)          // 0通知 1收藏 2设置
    val selGroup = MutableStateFlow(Importance.IMPORTANT)
    val onlyUnread = MutableStateFlow(false)
    val siteFilter = MutableStateFlow<String?>(null)
    val settingsMap: StateFlow<Map<String, String>> =
        settings.snapshot.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val siteStates: StateFlow<List<SiteState>> =
        dao.observeSiteStates().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val unread: StateFlow<List<CountRow>> =
        dao.observeUnread().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val failedCount: StateFlow<Int> =
        dao.observeFailedCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val progress: StateFlow<FetchProgress> = fetcher.progress

    val listFlow: StateFlow<List<Notice>> = kotlinx.coroutines.flow.combine(
        selGroup, onlyUnread, siteFilter
    ) { g, u, s -> Triple(g, u, s) }
        .flatMapLatestCompat { (g, u, s) -> dao.observeList(listOf(g.name), s, u) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoritesFlow: StateFlow<List<Notice>> =
        dao.observeFavorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun fetch(incremental: Boolean, from: String, to: String) {
        val m = settingsMap.value
        val enabled = (m["enabled_sites"] ?: "").split(",").mapNotNull { id -> SiteDef.entries.find { it.id == id } }
        viewModelScope.launch {
            fetcher.run(enabled, if (incremental) FetchMode() else FetchMode(customFrom = from, customTo = to),
                m["base_url"].orEmpty(), m["api_key"].orEmpty(), m["model"].orEmpty(), settings.profileText(m), System.currentTimeMillis(),
                m["rss_url"].orEmpty())
        }
    }

    fun openUrl(n: Notice) {
        if (n.imp == Importance.IMPORTANT || n.imp == Importance.UNCERTAIN) setRead(n, true)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                getApplication<App>().startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(n.url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    fun setRead(n: Notice, v: Boolean) = viewModelScope.launch(Dispatchers.IO) { dao.setRead(n.id, v) }
    fun markGroupRead() = viewModelScope.launch(Dispatchers.IO) { dao.markGroupRead(selGroup.value.name) }

    fun reanalyzeFailed() {
        val m = settingsMap.value
        viewModelScope.launch {
            fetcher.reanalyzeFailed(m["base_url"].orEmpty(), m["api_key"].orEmpty(),
                m["model"].orEmpty(), settings.profileText(m))
        }
    }
    fun setFav(n: Notice, v: Boolean) = viewModelScope.launch(Dispatchers.IO) { dao.setFav(n.id, v) }
    fun setNote(n: Notice, v: String) = viewModelScope.launch(Dispatchers.IO) { dao.setNote(n.id, v) }
    fun delete(n: Notice) = viewModelScope.launch(Dispatchers.IO) { dao.delete(n.id) }
    fun markAllUnread() = viewModelScope.launch(Dispatchers.IO) { dao.markAllUnread() }

    suspend fun expiredCount(): Int = withContext(Dispatchers.IO) { dao.expiredCount(today()) }
    fun cleanExpired() = viewModelScope.launch(Dispatchers.IO) { dao.cleanExpired(today()) }
    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

    fun save(key: androidx.datastore.preferences.core.Preferences.Key<String>, v: String) =
        viewModelScope.launch(Dispatchers.IO) { settings.put(key, v) }

    fun toggleSite(site: SiteDef, on: Boolean) {
        val cur = settingsMap.value["enabled_sites"].orEmpty().split(",").filter { it.isNotBlank() }.toMutableSet()
        if (on) cur.add(site.id) else cur.remove(site.id)
        viewModelScope.launch(Dispatchers.IO) { settings.put(SettingsStore.K_SITES, cur.joinToString(",")) }
    }

    fun testAi(onResult: (String) -> Unit) {
        val m = settingsMap.value
        viewModelScope.launch(Dispatchers.IO) {
            val r = runCatching {
                com.xjtu.notify.ai.DashScope((getApplication() as App).http)
                    .chat(m["base_url"].orEmpty(), m["api_key"].orEmpty(), m["model"].orEmpty(), "回复两个字：成功", "测试")
            }
            onResult(r.getOrElse { e -> "失败：${e.message?.take(160)}" })
        }
    }
}

private fun <T, R> kotlinx.coroutines.flow.Flow<T>.flatMapLatestCompat(transform: suspend (T) -> kotlinx.coroutines.flow.Flow<R>) =
    flatMapLatest(transform)
