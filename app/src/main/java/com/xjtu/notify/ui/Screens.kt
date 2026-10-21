package com.xjtu.notify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.xjtu.notify.data.Importance
import com.xjtu.notify.data.Notice
import com.xjtu.notify.data.SettingsStore
import com.xjtu.notify.data.SiteDef
import com.xjtu.notify.fetch.FetchProgress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: AppViewModel) {
    val tab by vm.selTab.collectAsState()
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { vm.selTab.value = 0 },
                    icon = { Icon(Icons.Filled.Notifications, null) }, label = { Text("通知") })
                NavigationBarItem(tab == 1, { vm.selTab.value = 1 },
                    icon = { Icon(Icons.Filled.Star, null) }, label = { Text("收藏") })
                NavigationBarItem(tab == 2, { vm.selTab.value = 2 },
                    icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("设置") })
            }
        }
    ) { pad ->
        when (tab) {
            0 -> HomeScreen(vm, pad)
            1 -> FavoritesScreen(vm, pad)
            else -> SettingsScreen(vm, pad)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, pad: PaddingValues) {
    val groups = listOf(Importance.IMPORTANT, Importance.UNCERTAIN, Importance.UNIMPORTANT)
    val group by vm.selGroup.collectAsState()
    val onlyUnread by vm.onlyUnread.collectAsState()
    val siteFilter by vm.siteFilter.collectAsState()
    val list by vm.listFlow.collectAsState()
    val unread by vm.unread.collectAsState()
    val progress by vm.progress.collectAsState()
    val settings by vm.settingsMap.collectAsState()
    var showFetch by remember { mutableStateOf(false) }
    var showClean by remember { mutableStateOf(false) }
    var cleanCount by remember { mutableStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.padding(pad),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("交大通知", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { vm.markGroupRead() }) { Icon(Icons.Filled.DoneAll, "本栏全部已读") }
                        IconButton(onClick = { showFetch = true }) { Icon(Icons.Filled.Download, "一键获取") }
                        IconButton(onClick = {
                            scope.launch { cleanCount = vm.expiredCount(); showClean = true }
                        }) { Icon(Icons.Filled.DeleteSweep, "清理过期") }
                    }
                )
                if (progress !is FetchProgress.Idle && progress !is FetchProgress.Done) {
                    when (progress) {
                        is FetchProgress.Running -> Column {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text((progress as FetchProgress.Running).msg,
                                Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall)
                        }
                        is FetchProgress.Failed -> Text((progress as FetchProgress.Failed).msg,
                            Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                        else -> {}
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { g ->
                        val u = unread.find { it.importance == g.name }?.n ?: 0
                        FilterChip(g == group, { vm.selGroup.value = g }, {
                            Text(if (u > 0 && g != Importance.UNIMPORTANT) "${g.label}·${u}未读" else g.label)
                        })
                    }
                }
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("仅未读", style = MaterialTheme.typography.labelMedium)
                    Switch(onlyUnread, { vm.onlyUnread.value = it }, Modifier.height(28.dp))
                    Spacer(Modifier.width(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(siteFilter == null, { vm.siteFilter.value = null }, { Text("全部") })
                        SiteDef.entries.forEach { s ->
                            FilterChip(siteFilter == s.id, { vm.siteFilter.value = s.id }, { Text(s.short) })
                        }
                    }
                }
            }
        }
    ) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p)) {
            items(list, key = { it.id }) { n -> NoticeCard(vm, n) }
            if (list.isEmpty()) item {
                Text("（暂无通知，点右上角 ⬇ 获取）", Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.outline)
            }
        }
    }

    if (showFetch) FetchDialog(vm) { showFetch = false }
    if (showClean) AlertDialog(
        onDismissRequest = { showClean = false },
        title = { Text("清理过期通知") },
        text = { Text("发现 $cleanCount 条已失效通知（含获取时即过期的）。\n收藏的通知不会被删除。") },
        confirmButton = {
            TextButton(onClick = { vm.cleanExpired(); showClean = false }) { Text("清理") }
        },
        dismissButton = { TextButton(onClick = { showClean = false }) { Text("取消") } }
    )
}

@Composable
fun NoticeCard(vm: AppViewModel, n: Notice) {
    var expanded by remember(n.id) { mutableStateOf(false) }
    var showNote by remember(n.id) { mutableStateOf(false) }
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
    val expired = n.expireDate != null && n.expireDate < today
    val unreadable = (n.imp == Importance.IMPORTANT || n.imp == Importance.UNCERTAIN) && !n.isRead

    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
        .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (unreadable) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (unreadable) Box(Modifier.size(9.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                if (unreadable) Spacer(Modifier.width(6.dp))
                Text(n.title, fontWeight = if (unreadable) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.setFav(n, !n.favorite) }) {
                    Icon(if (n.favorite) Icons.Filled.Star else Icons.Filled.StarBorder, "收藏",
                        tint = if (n.favorite) Color(0xFFB8860B) else MaterialTheme.colorScheme.outline)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(n.site(), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
                Text(n.publishDate, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
                if (n.expireDate != null) {
                    Text(
                        (if (expired) "已失效 " else "⏳ ") + (n.expireDesc.ifBlank { "${n.expireDate} 截止" }),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (expired) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error)
                }
                if (n.aiFailed) Text("AI未分析", style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB8860B))
                if (n.note.isNotBlank()) Text("📝", style = MaterialTheme.typography.labelSmall)
            }
            if ((n.imp == Importance.IMPORTANT || n.imp == Importance.UNCERTAIN) &&
                n.summary.isNotBlank() && !expanded) {
                Text(n.summary, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                if (n.summary.isNotBlank()) Text("摘要：${n.summary}", style = MaterialTheme.typography.bodySmall)
                Text("链接：${n.url}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { vm.openUrl(n) }) {
                        Text(if (n.isRead) "再看原文" else "看原文(标已读)")
                    }
                    OutlinedButton(onClick = { showNote = true }) { Text("备注") }
                    OutlinedButton(onClick = { vm.setRead(n, !n.isRead) }) { Text(if (n.isRead) "标未读" else "标已读") }
                    TextButton(onClick = { vm.delete(n) }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
                if (n.note.isNotBlank()) Text("备注：${n.note}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (showNote) NoteDialog(n) { v -> vm.setNote(n, v); showNote = false }
}

private fun Notice.site(): String = SiteDef.entries.find { it.id == siteId }?.short ?: siteId

@Composable
fun NoteDialog(n: Notice, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(n.note) }
    AlertDialog(onDismissRequest = { onSave(n.note) },
        title = { Text("收藏备注") },
        text = { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("备注内容") }) },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { onSave("") }) { Text("清空") } })
}

@Composable
fun FetchDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val settings by vm.settingsMap.collectAsState()
    val states by vm.siteStates.collectAsState()
    var incremental by remember { mutableStateOf(true) }
    var from by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        .format(Date(System.currentTimeMillis() - 30L * 86400_000))) }
    var to by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())) }
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("一键获取通知") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(incremental, { incremental = true })
                    Text("仅上次获取之后")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(!incremental, { incremental = false })
                    Text("自定义时间范围")
                }
                if (!incremental) {
                    OutlinedTextField(from, { from = it }, label = { Text("开始 YYYY-MM-DD") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(to, { to = it }, label = { Text("结束 YYYY-MM-DD") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                Text("各站上次获取：", style = MaterialTheme.typography.labelMedium)
                SiteDef.entries.forEach { s ->
                    val st = states.find { it.siteId == s.id }
                    Text("${s.label}　${st?.let { fmt.format(Date(it.lastFetchAt)) } ?: "从未"}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                if (settings["api_key"].isNullOrBlank()) {
                    Text("⚠ 未设置 API Key，请到设置页填写", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.fetch(incremental, from, to); onDismiss()
            }) { Text("开始获取") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
fun FavoritesScreen(vm: AppViewModel, pad: PaddingValues) {
    val favs by vm.favoritesFlow.collectAsState()
    Column(Modifier.padding(pad)) {
        Text("收藏", Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.fillMaxSize()) {
            items(favs, key = { it.id }) { n -> NoticeCard(vm, n) }
            if (favs.isEmpty()) item {
                Text("（还没有收藏。在通知卡片右上角点 ☆）", Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, pad: PaddingValues) {
    val settings by vm.settingsMap.collectAsState()
    val loaded = settings.isNotEmpty()
    var baseUrl by remember(loaded) { mutableStateOf(settings["base_url"] ?: "") }
    var apiKey by remember(loaded) { mutableStateOf(settings["api_key"] ?: "") }
    var rssUrl by remember(loaded) { mutableStateOf(settings["rss_url"] ?: "") }
    var model by remember(loaded) { mutableStateOf(settings["model"] ?: "qwen-plus") }
    var grade by remember(loaded) { mutableStateOf(settings["grade"] ?: "") }
    var major by remember(loaded) { mutableStateOf(settings["major"] ?: "") }
    var free by remember(loaded) { mutableStateOf(settings["profile_free"] ?: "") }
    val enabled = (settings["enabled_sites"] ?: "").split(",")
    val intentions = (settings["intentions"] ?: "").split(",").filter { it.isNotBlank() }
    val allIntentions = listOf("保研推免", "竞赛报名", "出国交流", "奖助学金", "转专业", "考研", "支教/志愿")
    var testMsg by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("设置", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { Text("AI 服务（OpenAI 兼容接口，任选一家）", fontWeight = FontWeight.Bold) }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("通义" to "https://dashscope.aliyuncs.com/compatible-mode/v1",
                    "DeepSeek" to "https://api.deepseek.com/v1",
                    "智谱" to "https://open.bigmodel.cn/api/paas/v4",
                    "Kimi" to "https://api.moonshot.cn/v1",
                    "硅基" to "https://api.siliconflow.cn/v1").forEach { (name, url) ->
                    FilterChip(baseUrl == url, { baseUrl = url; vm.save(SettingsStore.K_BASE_URL, url) }, { Text(name) })
                }
            }
        }
        item {
            OutlinedTextField(baseUrl, { baseUrl = it; vm.save(SettingsStore.K_BASE_URL, it) },
                label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(apiKey, { apiKey = it; vm.save(SettingsStore.K_API_KEY, it) },
                label = { Text("API Key（仅存本机）") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(model, { model = it; vm.save(SettingsStore.K_MODEL, it) },
                    label = { Text("模型") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { testAi0(vm) { testMsg = it } }) { Text("测试") }
            }
            if (testMsg.isNotBlank()) Text(testMsg, style = MaterialTheme.typography.labelSmall)
        }
        item { Text("数据源", fontWeight = FontWeight.Bold) }
        item {
            OutlinedTextField(rssUrl, { rssUrl = it; vm.save(SettingsStore.K_RSS_URL, it) },
                label = { Text("公众号订阅地址（取阅 RSS，留空则不抓）") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
        }
        item { Text("我的画像（AI 判断通知是否与你相关）", fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("本科生", "硕士生", "博士生").forEach { st ->
                    FilterChip((settings["stage"] ?: "") == st, { vm.save(SettingsStore.K_STAGE, st) }, { Text(st) })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(grade, { grade = it; vm.save(SettingsStore.K_GRADE, it) },
                    label = { Text("年级，如2024") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(major, { major = it; vm.save(SettingsStore.K_MAJOR, it) },
                    label = { Text("专业") }, singleLine = true, modifier = Modifier.weight(1.5f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                allIntentions.forEach { it2 ->
                    FilterChip(intentions.contains(it2), {
                        val cur = intentions.toMutableList()
                        if (!cur.remove(it2)) cur.add(it2)
                        vm.save(SettingsStore.K_INTENTIONS, cur.joinToString(","))
                    }, { Text(it2) })
                }
            }
        }
        item {
            OutlinedTextField(free, { free = it; vm.save(SettingsStore.K_FREE, it) },
                label = { Text("补充，如：准备参加数学竞赛、住X舍") }, modifier = Modifier.fillMaxWidth())
        }
        item { Text("网站开关", fontWeight = FontWeight.Bold) }
        items(SiteDef.entries) { s ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(s.label, Modifier.weight(1f))
                Switch(enabled.contains(s.id), { vm.toggleSite(s, it) })
            }
        }
        item {
            val failed by vm.failedCount.collectAsState()
            OutlinedButton(onClick = { vm.reanalyzeFailed() },
                modifier = Modifier.fillMaxWidth(), enabled = failed > 0) {
                Text(if (failed > 0) "重新分析 AI 未分析的 $failed 条" else "没有 AI 未分析的条目")
            }
        }
        item {
            OutlinedButton(onClick = { vm.markAllUnread() }, modifier = Modifier.fillMaxWidth()) { Text("全部重新标记为未读") }
        }
        item {
            Text("说明：通知只从学校官网获取；本应用不缓存全文，点『看原文』跳转浏览器阅读；" +
                "AI 分类摘要会产生少量 API 费用（每次获取约几分钱）。数据仅存本机。",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

private fun testAi0(vm: AppViewModel, cb: (String) -> Unit) = vm.testAi(cb)
