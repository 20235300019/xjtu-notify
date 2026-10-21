package com.xjtu.notify.fetch

import com.xjtu.notify.ai.Analyzer
import com.xjtu.notify.ai.DashScope
import com.xjtu.notify.data.Importance
import com.xjtu.notify.data.Notice
import com.xjtu.notify.data.NoticeDao
import com.xjtu.notify.data.SiteDef
import com.xjtu.notify.net.Http
import com.xjtu.notify.parse.Head
import com.xjtu.notify.parse.SiteSource
import com.xjtu.notify.parse.Sources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class FetchProgress {
    object Idle : FetchProgress()
    data class Running(val msg: String, val done: Int, val total: Int) : FetchProgress()
    data class Done(val msg: String) : FetchProgress()
    data class Failed(val msg: String) : FetchProgress()
}

class FetchMode(val customFrom: String? = null, val customTo: String? = null) {
    val incremental get() = customFrom == null
}

class FetchManager(private val http: Http, private val dao: NoticeDao) {
    private val analyzer = Analyzer(DashScope(http))
    private val _progress = MutableStateFlow<FetchProgress>(FetchProgress.Idle)
    val progress: StateFlow<FetchProgress> = _progress

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

    suspend fun run(sites: List<SiteDef>, mode: FetchMode, baseUrl: String, apiKey: String, model: String,
                    profile: String, nowEpochMs: Long, rssUrl: String = "") = withContext(Dispatchers.IO) {
        if (sites.isEmpty()) { _progress.value = FetchProgress.Failed("请先在设置中启用至少一个网站"); return@withContext }
        if (apiKey.isBlank()) { _progress.value = FetchProgress.Failed("请先在设置中填写 API Key"); return@withContext }
        _progress.value = FetchProgress.Running("开始获取…", 0, 0)
        try {
            var totalNew = 0
            var totalKept = 0
            for (site in sites) {
                val src = Sources.all[site] ?: continue
                val from: String
                val to: String
                if (mode.incremental) {
                    val last = dao.siteState(site.id)
                    from = last?.let { SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(it.lastFetchAt)) }
                        ?: SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(nowEpochMs - 30L * 86400_000))
                    to = today()
                    _progress.value = FetchProgress.Running("${site.label}：增量（${from} 之后）", totalNew, totalNew)
                } else {
                    from = mode.customFrom!!; to = mode.customTo ?: today()
                    _progress.value = FetchProgress.Running("${site.label}：范围 $from ~ $to", totalNew, totalNew)
                }
                val heads = crawl(src, from, to, rssUrl)
                val fresh = heads.filter { dao.exists(it.url) == 0 }
                totalNew += fresh.size
                _progress.value = FetchProgress.Running("${site.label}：${heads.size} 条在范围内，新 ${fresh.size} 条", totalNew, totalNew)
                val processed = process(src, fresh, baseUrl, apiKey, model, profile)
                totalKept += processed
                dao.upsertSiteState(com.xjtu.notify.data.SiteState(site.id, nowEpochMs))
            }
            _progress.value = FetchProgress.Done("获取完成：新通知 $totalNew 条，入库 $totalKept 条（其余 AI 判定已过期）")
        } catch (e: Exception) {
            _progress.value = FetchProgress.Failed("获取失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun crawl(src: SiteSource, from: String, to: String, homeOverride: String = ""): List<Head> {
        val result = LinkedHashMap<String, Head>()
        // 公众号那条线地址由设置提供；没填就直接跳过，别拿空地址去请求
        val start = if (homeOverride.isNotBlank() && src.site.id == "wx") homeOverride else src.site.home
        if (start.isBlank()) return emptyList()
        var url: String? = start
        var page = 0
        while (url != null && page < 15) {
            page++
            val html = http.getBody(url)
            val heads = src.parseList(html, url)
            if (heads.isEmpty()) break
            var allOlder = true
            for (h in heads) {
                if (h.date > to) { allOlder = false; continue }
                if (h.date < from) continue
                allOlder = false
                result.putIfAbsent(h.url, h)
            }
            if (heads.all { it.date < from }) break
            if (allOlder && result.isNotEmpty()) break
            url = src.nextUrl(html, url)
            Thread.sleep(400)
        }
        return result.values.sortedByDescending { it.date }
    }

    private suspend fun process(src: SiteSource, fresh: List<Head>, baseUrl: String, apiKey: String, model: String, profile: String): Int {
        if (fresh.isEmpty()) return 0
        val today = today()

        // 一级：仅标题初筛（省正文抓取与 token）
        val drop = HashMap<Int, Boolean>()
        fresh.indices.chunked(15).forEachIndexed { ci, idxs ->
            _progress.value = FetchProgress.Running("标题初筛 ${ci * 15 + 1}~${minOf(fresh.size, (ci + 1) * 15)}/${fresh.size}…", 0, fresh.size)
            val r = analyzer.triage(baseUrl, apiKey, model, profile, today, idxs.map { fresh[it].title to fresh[it].site.label })
            idxs.forEachIndexed { li, gi -> drop[gi] = r[li] == true }
        }
        var kept = 0
        val need = ArrayList<Int>()
        for ((gi, h) in fresh.withIndex()) {
            if (drop[gi] == true) {
                dao.insert(Notice(siteId = h.site.id, url = h.url, title = h.title, category = h.category,
                    publishDate = h.date, fetchedAt = System.currentTimeMillis(),
                    importance = Importance.UNIMPORTANT.name, summary = "（仅凭标题初筛判定不重要）"))
            } else need.add(gi)
        }

        // 二级：需要者抓正文 + 完整分析
        val todo = need.map { fresh[it] }
        val bodies = ArrayList<String>()
        for ((i, h) in todo.withIndex()) {
            _progress.value = FetchProgress.Running("读取正文 ${i + 1}/${todo.size}：${h.title.take(18)}…", i, todo.size)
            bodies.add(runCatching { src.extractBody(http.getBody(h.url)) }.getOrDefault(""))
            delay(400)
        }
        val chunks = todo.indices.chunked(5)
        for ((ci, idxs) in chunks.withIndex()) {
            _progress.value = FetchProgress.Running("AI 分析 ${ci * 5 + 1}~${minOf(todo.size, (ci + 1) * 5)}/${todo.size}…", kept, todo.size)
            val titles = idxs.map { todo[it].title to todo[it].site.label }
            val chunkBodies = idxs.map { bodies[it] }
            val verdicts = runCatching { analyzer.analyze(baseUrl, apiKey, model, profile, today, titles, chunkBodies) }
                .getOrDefault(emptyMap())
            for ((li, ti) in idxs.withIndex()) {
                val h = todo[ti]
                var v = verdicts[li]
                if (v == null) v = runCatching {
                    analyzer.analyzeOne(baseUrl, apiKey, model, profile, today, h.title, h.site.label, bodies[ti])
                }.getOrNull()
                val fallback = v == null
                val imp = when {
                    fallback -> Importance.UNCERTAIN.name
                    v!!.expired -> Importance.UNIMPORTANT.name
                    else -> v.importance
                }
                val n = Notice(
                    siteId = h.site.id, url = h.url, title = h.title, category = h.category,
                    publishDate = h.date, fetchedAt = System.currentTimeMillis(),
                    importance = imp,
                    summary = when {
                        fallback -> ""
                        v!!.expired -> ("已过时：" + (v.expireDesc.ifBlank { v.expireDate ?: "" }) +
                            if (v.summary.isNotBlank()) "。${v.summary}" else "")
                        else -> v.summary
                    },
                    expireDate = if (fallback || v == null) null else v.expireDate,
                    expireDesc = if (fallback || v == null) "" else v.expireDesc,
                    aiFailed = fallback
                )
                dao.insert(n)
                if (imp != Importance.UNIMPORTANT.name) kept++
            }
        }
        return kept
    }

    suspend fun reanalyzeFailed(baseUrl: String, apiKey: String, model: String, profile: String) =
        withContext(Dispatchers.IO) {
            val failed = dao.getFailed()
            if (failed.isEmpty()) { _progress.value = FetchProgress.Done("没有待重新分析的条目"); return@withContext }
            val today = today()
            var ok = 0
            failed.forEachIndexed { i, n ->
                _progress.value = FetchProgress.Running("重新分析 ${i + 1}/${failed.size}…", ok, failed.size)
                val src = Sources.all.entries.find { it.key.id == n.siteId }?.value
                val body = src?.let { runCatching { it.extractBody(http.getBody(n.url)) }.getOrDefault("") } ?: ""
                val v = runCatching { analyzer.analyzeOne(baseUrl, apiKey, model, profile, today, n.title, n.siteId, body) }.getOrNull()
                if (v != null) {
                    val imp = if (v.expired) Importance.UNIMPORTANT.name else v.importance
                    val summary = if (v.expired) "已过时：" + (v.expireDesc.ifBlank { v.expireDate ?: "" }) else v.summary
                    dao.updateAi(n.id, imp, summary, v.expireDate, v.expireDesc)
                    ok++
                    delay(300)
                }
            }
            _progress.value = FetchProgress.Done("重新分析完成：成功 $ok/${failed.size} 条")
        }
}
