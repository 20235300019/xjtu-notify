package com.xjtu.notify.ai

import com.xjtu.notify.net.Http
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class Verdict(
    val idx: Int,
    val expired: Boolean,
    val expireDate: String?,
    val expireDesc: String,
    val importance: String,
    val summary: String
)

class DashScope(private val http: Http) {
    companion object {
        const val DEFAULT_BASE = "https://dashscope.aliyuncs.com/compatible-mode/v1"
        val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

        fun endpoint(base: String): String {
            val b = base.trim().trimEnd('/')
            return when {
                b.isEmpty() -> DEFAULT_BASE + "/chat/completions"
                b.endsWith("/chat/completions") -> b
                else -> b + "/chat/completions"
            }
        }
        fun escape(s: String): String = JSON.encodeToString(String.serializer(), s)
    }

    fun chat(baseUrl: String, apiKey: String, model: String, system: String, user: String): String {
        val noThink = ",\"enable_thinking\":false,\"thinking\":{\"type\":\"disabled\"}"
        val body = """{"model":${escape(model)},"temperature":0.1$noThink,"messages":[""" +
            """{"role":"system","content":${escape(system)}},""" +
            """{"role":"user","content":${escape(user)}}]}"""
        val resp = http.postJson(endpoint(baseUrl), body, mapOf("Authorization" to "Bearer $apiKey"))
        val obj = JSON.parseToJsonElement(resp).jsonObject
        return obj["choices"]?.jsonArray?.get(0)?.jsonObject
            ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: error("响应缺少 content")
    }
}

class Analyzer(private val ds: DashScope) {
    companion object {
        const val TRIAGE_SYSTEM = """你是西安交通大学的通知初筛助手。给定用户画像和一批通知标题（只有标题，不抓正文以节省费用），判断哪些标题可以仅凭标题直接归为"不重要"（无需再抓取正文分析）。
drop=true 仅限以下明确情形：
1) 标题限定的受众与用户画像明显不符（如用户是本科生，标题明确只面向"新生/2026级/研究生/博士生/教职工/校友"等）；
2) 标题明显是无需学生行动的新闻类：工作动态、调研/访问/会议报道、获奖结果公布、多年前的例行安排。
其余一律 drop=false（包括拿不准的、标题含"新生"但用户就是该年级的）。
只输出JSON数组：[{"id":0,"drop":true},{"id":1,"drop":false}]，不要任何其它文字。"""

        const val SYSTEM = """你是西安交通大学学生事务通知分析助手。给定用户画像和若干条学校官网通知（标题+正文节选），逐条分析并只输出JSON数组，不要任何其它文字。
每条输出字段：id(整数，与输入对应)；expired(布尔，通知中明确截止时间相对"今天"是否已过)；expireDate(字符串YYYY-MM-DD，用户需行动的最终截止日，没有则null)；expireDesc(不超12字的截止说明，如"报名截至9月10日"，没有则空串)；importance(取值important/uncertain/unimportant)；summary(中文，不超60字，important与uncertain必须给出，包含关键动作与截止时间；unimportant给空串)。
判定标准：
- important：面向全校、或明确符合用户画像人群，且有明确截止/时间节点、错过有实际损失。包括：选课退课、考试、缴费、报到注册、奖助学金申请、答辩与毕业环节、宿舍事务、符合用户画像（专业/年级/学段/意向）的竞赛或项目报名。
- uncertain：需要行动或有明确时限，但与用户画像相关性不确定：仅限特定专业/年级/类别而用户未表达相关意向、面向人群描述模糊、或截止日不明确。
- unimportant：学生无需任何行动：新闻动态、会议访问、获奖名单公布且公示期已过、长期有效的政策文件、一般性告知。
- 正文无明确截止日时：expireDate=null 且 expired=false（视为长期有效）。"""
    }

    /** 标题初筛：返回 map[id]=是否可直接丢弃（判失败时返回空map=全部保留） */
    fun triage(baseUrl: String, apiKey: String, model: String, profile: String, today: String,
               titles: List<Pair<String, String>>): Map<Int, Boolean> {
        val sb = StringBuilder("今天是 $today。用户画像：$profile。标题列表：\n[")
        titles.forEachIndexed { i, (t, site) ->
            if (i > 0) sb.append(',')
            sb.append("""{"id":$i,"site":${DashScope.escape(site)},"title":${DashScope.escape(t)}}""")
        }
        sb.append(']')
        val content = runCatching { ds.chat(baseUrl, apiKey, model, TRIAGE_SYSTEM, sb.toString()) }.getOrNull() ?: return emptyMap()
        val start = content.indexOf('['); val end = content.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyMap()
        val arr = runCatching { DashScope.JSON.parseToJsonElement(content.substring(start, end + 1)).jsonArray }.getOrNull()
            ?: return emptyMap()
        val out = HashMap<Int, Boolean>()
        for (e in arr) runCatching {
            val o = e.jsonObject
            out[o["id"]!!.jsonPrimitive.content.toInt()] =
                o["drop"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() == true
        }
        return out
    }

    fun analyze(baseUrl: String, apiKey: String, model: String, profile: String, today: String,
                items: List<Pair<String, String>>, bodies: List<String>): Map<Int, Verdict> {
        val sb = StringBuilder("今天是 $today。用户画像：$profile。通知列表(JSON)：\n[")
        items.forEachIndexed { i, (title, site) ->
            val body = bodies.getOrElse(i) { "" }.take(1500)
            if (i > 0) sb.append(',')
            sb.append("""{"id":$i,"title":${DashScope.escape(title)},"site":${DashScope.escape(site)},"body":${DashScope.escape(body)}}""")
        }
        sb.append(']')
        val content = callJson(baseUrl, apiKey, model, sb.toString()) ?: return emptyMap()
        return parseVerdicts(content, items.size)
    }

    private fun callJson(baseUrl: String, apiKey: String, model: String, user: String): String? =
        runCatching { ds.chat(baseUrl, apiKey, model, SYSTEM, user) }.getOrNull()

    private fun parseVerdicts(content: String, size: Int): Map<Int, Verdict> {
        val start = content.indexOf('[')
        val end = content.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyMap()
        val arr = runCatching { DashScope.JSON.parseToJsonElement(content.substring(start, end + 1)).jsonArray }.getOrNull()
            ?: return emptyMap()
        val entries = ArrayList<Verdict>()
        for (e in arr) runCatching {
            val o = e.jsonObject
            entries.add(Verdict(
                o["id"]!!.jsonPrimitive.content.toInt(),
                o["expired"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() == true,
                o["expireDate"]?.jsonPrimitive?.content?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) },
                o["expireDesc"]?.jsonPrimitive?.content ?: "",
                when (o["importance"]?.jsonPrimitive?.content?.lowercase()) {
                    "important" -> "IMPORTANT"; "unimportant" -> "UNIMPORTANT"; else -> "UNCERTAIN"
                },
                o["summary"]?.jsonPrimitive?.content ?: ""
            ))
        }
        // 优先按 id 对齐；id 越界/重复的按出现顺序回填到空位（防模型 1-based 编号错位）
        val out = HashMap<Int, Verdict>()
        val rest = ArrayList<Verdict>()
        for (v in entries) if (v.idx in 0 until size && !out.containsKey(v.idx)) out[v.idx] = v else rest.add(v)
        for (v in rest) { val k = (0 until size).firstOrNull { !out.containsKey(it) } ?: break; out[k] = v.copy(idx = k) }
        return out
    }

    fun analyzeOne(baseUrl: String, apiKey: String, model: String, profile: String, today: String,
                   title: String, site: String, body: String): Verdict? {
        analyze(baseUrl, apiKey, model, profile, today, listOf(title to site), listOf(body)).let { m ->
            if (m.isNotEmpty()) return m[0]
        }
        return runCatching { analyze(baseUrl, apiKey, model, profile, today, listOf(title to site), listOf(body)) }
            .getOrNull()?.get(0)
    }
}
