package com.xjtu.notify.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Importance(val label: String) {
    IMPORTANT("重要"), UNCERTAIN("不确定"), UNIMPORTANT("不重要"), EXPIRED("已失效")
}

enum class SiteDef(val id: String, val label: String, val short: String, val home: String) {
    JWC("jwc", "教务处·教学通知", "教务处", "https://jwc.xjtu.edu.cn/jxxx/jxtz2.htm"),
    INTL("intl", "国际处·海外学习", "海外学习", "https://international.xjtu.edu.cn/hwxx1.htm"),
    MEC("mec", "机械学院·本科通知", "机械学院", "https://mec.xjtu.edu.cn/index/tzgg/bks.htm"),
    PEC("pec", "实践教学中心·通知", "实践中心", "https://pec.xjtu.edu.cn/xxgg/tzgg.htm"),
    WEN("wen", "文治书院·通知公告", "文治书院", "https://wen.xjtu.edu.cn/tzgg.htm"),
    // home 故意留空：真实地址由「设置 → 公众号订阅地址」提供，不写死在 APK 里
    WX("wx", "公众号·取阅订阅", "公众号", "")
}

@Entity(tableName = "notice", indices = [Index(value = ["url"], unique = true)])
data class Notice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteId: String,
    val url: String,
    val title: String,
    val category: String = "",
    val publishDate: String,            // yyyy-MM-dd
    val fetchedAt: Long,
    val importance: String = Importance.UNCERTAIN.name,
    val summary: String = "",
    val expireDate: String? = null,     // yyyy-MM-dd; null = 长期有效
    val expireDesc: String = "",
    val isRead: Boolean = false,
    val favorite: Boolean = false,
    val note: String = "",
    val aiFailed: Boolean = false
) {
    val imp get() = runCatching { Importance.valueOf(importance) }.getOrDefault(Importance.UNCERTAIN)
}

@Entity(tableName = "site_state")
data class SiteState(
    @PrimaryKey val siteId: String,
    val lastFetchAt: Long
)
