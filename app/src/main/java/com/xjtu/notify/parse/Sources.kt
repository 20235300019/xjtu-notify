package com.xjtu.notify.parse

import com.xjtu.notify.data.SiteDef
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Locale

data class Head(
    val site: SiteDef,
    val title: String,
    val url: String,
    val date: String, // yyyy-MM-dd
    val category: String = ""
)

interface SiteSource {
    val site: SiteDef
    fun parseList(html: String, pageUrl: String): List<Head>
    fun nextUrl(html: String, pageUrl: String): String?
    fun extractBody(html: String): String {
        var m = Regex("(?s)<div class=\"v_news_content\">(.*?)</div>").find(html)
        if (m == null || text(m.groups[1]!!.value).length < 60) {
            m = Regex("(?s)<div[^>]+class=\"[^\"]*(?:article|content|TRS_Editor)[^\"]*\"[^>]*>(.*?)(?=<div class=\"|<script)").find(html) ?: return ""
        }
        return text(m.groups[1]!!.value)
    }

    companion object {
        fun text(html: String): String = html
            .replace(Regex("(?s)<(script|style).*?</\\1>"), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&ldquo;", "“").replace("&rdquo;", "”")
            .replace(Regex("\\s+"), " ").trim()

        fun resolve(pageUrl: String, href: String): String? =
            runCatching { pageUrl.toHttpUrl().resolve(href) }.getOrNull()?.toString()

        fun nextUrlCommon(html: String, pageUrl: String): String? {
            val m = Regex("<a[^>]+href=\"([^\"]+)\"[^>]*>\\s*(?:下一页|下页|下 页)\\s*<").find(html) ?: return null
            return resolve(pageUrl, m.groups[1]!!.value)
        }

        fun normalizeDate(raw: String): String? {
            val s = raw.trim().replace('/', '-').replace('.', '-')
            Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})").find(s)?.let {
                return String.format(Locale.ROOT, "%s-%02d-%02d", it.groupValues[1], it.groupValues[2].toInt(), it.groupValues[3].toInt())
            }
            return null
        }
    }
}

object JwcSource : SiteSource {
    override val site = SiteDef.JWC
    private val itemRe = Regex("(?s)<li[^>]*>\\s*<a href=\"([^\"]+)\"[^>]*>(?:<i>([^<]*)</i>)?([^<]{4,}?)</a>\\s*<span>\\s*(\\d{4}-\\d{1,2}-\\d{1,2})\\s*</span>")

    override fun parseList(html: String, pageUrl: String): List<Head> =
        itemRe.findAll(html).mapNotNull { m ->
            val url = SiteSource.resolve(pageUrl, m.groupValues[1]) ?: return@mapNotNull null
            val date = SiteSource.normalizeDate(m.groupValues[4]) ?: return@mapNotNull null
            Head(site, m.groupValues[3].trim(), url, date, m.groupValues[2].removePrefix("[").removeSuffix("]").trim())
        }.filter { it.url.contains("/info/") }.toList()

    override fun nextUrl(html: String, pageUrl: String) = SiteSource.nextUrlCommon(html, pageUrl)
}

object IntlSource : SiteSource {
    override val site = SiteDef.INTL
    private val itemRe = Regex("(?s)<a href=\"([^\"]+)\"[^>]*>.*?<p class=\"list-tit[^\"]*\">([^<]+)</p>\\s*<p class=\"list-time[^\"]*\">\\s*([^<]+?)\\s*</p>")

    override fun parseList(html: String, pageUrl: String): List<Head> =
        itemRe.findAll(html).mapNotNull { m ->
            val url = SiteSource.resolve(pageUrl, m.groupValues[1]) ?: return@mapNotNull null
            val date = SiteSource.normalizeDate(m.groupValues[3]) ?: return@mapNotNull null
            Head(site, m.groupValues[2].trim(), url, date)
        }.filter { it.url.contains("/info/") }.toList()

    override fun nextUrl(html: String, pageUrl: String) = SiteSource.nextUrlCommon(html, pageUrl)
}

object MecSource : SiteSource {
    override val site = SiteDef.MEC
    private val itemRe = Regex("(?s)<a href=\"([^\"]+)\"[^>]*>\\s*<time[^>]*><span>\\s*(\\d{1,2})\\s*</span>(\\d{4})\\.(\\d{1,2})</time>\\s*<h4[^>]*>([^<]+)</h4>")

    override fun parseList(html: String, pageUrl: String): List<Head> =
        itemRe.findAll(html).mapNotNull { m ->
            val url = SiteSource.resolve(pageUrl, m.groupValues[1]) ?: return@mapNotNull null
            val date = String.format(Locale.ROOT, "%s-%02d-%02d", m.groupValues[3], m.groupValues[4].toInt(), m.groupValues[2].toInt())
            Head(site, m.groupValues[5].trim(), url, date)
        }.filter { it.url.contains("/info/") }.toList()

    override fun nextUrl(html: String, pageUrl: String) = SiteSource.nextUrlCommon(html, pageUrl)
}

object PecSource : SiteSource {
    override val site = SiteDef.PEC
    private val itemRe = Regex("(?s)<span class=\"date-list\">\\s*(\\d{4}-\\d{1,2}-\\d{1,2})\\s*</span>\\s*<a href=\"([^\"]+)\"[^>]*title=\"([^\"]+)\"")

    override fun parseList(html: String, pageUrl: String): List<Head> =
        itemRe.findAll(html).mapNotNull { m ->
            val date = SiteSource.normalizeDate(m.groupValues[1]) ?: return@mapNotNull null
            val url = SiteSource.resolve(pageUrl, m.groupValues[2]) ?: return@mapNotNull null
            Head(site, m.groupValues[3].trim(), url, date)
        }.filter { it.url.contains("/info/") }.toList()

    override fun nextUrl(html: String, pageUrl: String) = SiteSource.nextUrlCommon(html, pageUrl)
}

object WenSource : SiteSource {
    override val site = SiteDef.WEN
    private val itemRe = Regex("(?s)<a href=\"(info/\\d+/\\d+\\.htm)\"[^>]*title=\"([^\"]+)\"[^>]*>\\s*<span class=\"date\">\\s*<span class=\"r\">\\s*(\\d{1,2})-(\\d{1,2})\\s*</span>\\s*<span class=\"y\">\\s*(\\d{4})\\s*</span>")

    override fun parseList(html: String, pageUrl: String): List<Head> =
        itemRe.findAll(html).mapNotNull { m ->
            val url = SiteSource.resolve(pageUrl, m.groupValues[1]) ?: return@mapNotNull null
            val date = String.format(Locale.ROOT, "%s-%02d-%02d",
                m.groupValues[5], m.groupValues[3].toInt(), m.groupValues[4].toInt())
            Head(site, m.groupValues[2].trim(), url, date)
        }.filter { it.url.contains("/info/") }.toList()

    override fun nextUrl(html: String, pageUrl: String): String? = null
}

// 公众号 RSS 订阅（取阅 feed.zoro.build）：解析 RSS 2.0 的 <item>，没有翻页。
// ⚠️ 它的 <pubDate> 是 RFC822（Mon, 20 Oct 2026 08:00:00 GMT），SiteSource.normalizeDate 只认
//    2026-10-20 那种写法，直接套会全部解析失败，所以下面单独写了一个 dateOf。
object WxSource : SiteSource {
    override val site = SiteDef.WX
    private val itemRe = Regex("(?s)<item[^>]*>(.*?)</item>")
    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    private fun tag(block: String, name: String): String =
        Regex("(?s)<$name[^>]*>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</$name>")
            .find(block)?.groups?.get(1)?.value?.trim() ?: ""

    private fun dateOf(raw: String): String? {
        SiteSource.normalizeDate(raw)?.let { return it }
        val m = Regex("(\\d{1,2})\\s+([A-Za-z]{3})\\s+(\\d{4})").find(raw) ?: return null
        val mon = months.indexOf(m.groupValues[2].lowercase().replaceFirstChar { it.uppercase() }) + 1
        if (mon <= 0) return null
        return String.format(Locale.ROOT, "%s-%02d-%02d", m.groupValues[3], mon, m.groupValues[1].toInt())
    }

    override fun parseList(html: String, pageUrl: String): List<Head> =
        itemRe.findAll(html).mapNotNull { m ->
            val b = m.groups[1]!!.value
            val title = tag(b, "title")
            val link = tag(b, "link")
            if (title.length < 4 || link.isBlank()) return@mapNotNull null
            val date = dateOf(tag(b, "pubDate")) ?: return@mapNotNull null
            Head(site, title, link, date, tag(b, "dc:creator"))
        }.toList()

    override fun nextUrl(html: String, pageUrl: String): String? = null
}

object Sources {
    val all = mapOf(
        SiteDef.JWC to JwcSource,
        SiteDef.INTL to IntlSource,
        SiteDef.MEC to MecSource,
        SiteDef.PEC to PecSource,
        SiteDef.WEN to WenSource,
        SiteDef.WX to WxSource
    )
}
