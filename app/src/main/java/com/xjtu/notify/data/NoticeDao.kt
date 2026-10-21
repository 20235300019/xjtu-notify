package com.xjtu.notify.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class CountRow(val importance: String, val n: Int)

@Dao
interface NoticeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(n: Notice): Long

    @Query("SELECT COUNT(*) FROM notice WHERE url = :url")
    suspend fun exists(url: String): Int

    @Query("SELECT * FROM notice WHERE importance IN (:imps) " +
        "AND (:site IS NULL OR siteId = :site) " +
        "AND (:onlyUnread = 0 OR isRead = 0) " +
        "ORDER BY publishDate DESC, id DESC LIMIT 2000")
    fun observeList(imps: List<String>, site: String?, onlyUnread: Boolean): Flow<List<Notice>>

    @Query("SELECT * FROM notice WHERE favorite = 1 ORDER BY publishDate DESC, id DESC")
    fun observeFavorites(): Flow<List<Notice>>

    @Query("SELECT importance, COUNT(*) n FROM notice WHERE isRead = 0 " +
        "AND importance IN ('IMPORTANT','UNCERTAIN') GROUP BY importance")
    fun observeUnread(): Flow<List<CountRow>>

    @Query("UPDATE notice SET isRead = :v WHERE id = :id")
    suspend fun setRead(id: Long, v: Boolean)

    @Query("UPDATE notice SET isRead = 0 WHERE importance IN ('IMPORTANT','UNCERTAIN')")
    suspend fun markAllUnread()

    @Query("UPDATE notice SET isRead = 1 WHERE importance = :imp")
    suspend fun markGroupRead(imp: String)

    @Query("SELECT * FROM notice WHERE aiFailed = 1 ORDER BY publishDate DESC LIMIT 100")
    suspend fun getFailed(): List<Notice>

    @Query("SELECT COUNT(*) FROM notice WHERE aiFailed = 1")
    fun observeFailedCount(): Flow<Int>

    @Query("UPDATE notice SET importance = :imp, summary = :s, expireDate = :e, expireDesc = :d, aiFailed = 0 WHERE id = :id")
    suspend fun updateAi(id: Long, imp: String, s: String, e: String?, d: String)

    @Query("UPDATE notice SET favorite = :v WHERE id = :id")
    suspend fun setFav(id: Long, v: Boolean)

    @Query("UPDATE notice SET note = :v WHERE id = :id")
    suspend fun setNote(id: Long, v: String)

    @Query("DELETE FROM notice WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM notice WHERE (expireDate IS NOT NULL AND expireDate < :today) " +
        "OR importance = 'EXPIRED'")
    suspend fun expiredCount(today: String): Int

    @Query("DELETE FROM notice WHERE ((expireDate IS NOT NULL AND expireDate < :today) " +
        "OR importance = 'EXPIRED') AND favorite = 0")
    suspend fun cleanExpired(today: String): Int

    @Query("SELECT * FROM site_state WHERE siteId = :site")
    suspend fun siteState(site: String): SiteState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSiteState(s: SiteState)

    @Query("SELECT * FROM site_state")
    fun observeSiteStates(): Flow<List<SiteState>>
}
