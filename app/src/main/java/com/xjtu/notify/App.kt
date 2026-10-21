package com.xjtu.notify

import android.app.Application
import com.xjtu.notify.data.AppDatabase
import com.xjtu.notify.data.SettingsStore
import com.xjtu.notify.data.settingsStore
import com.xjtu.notify.fetch.FetchManager
import com.xjtu.notify.net.Http

class App : Application() {
    lateinit var http: Http
        private set
    lateinit var db: AppDatabase
        private set
    lateinit var dao: com.xjtu.notify.data.NoticeDao
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var fetcher: FetchManager
        private set

    override fun onCreate() {
        super.onCreate()
        http = Http()
        db = AppDatabase.create(this)
        dao = db.noticeDao()
        settings = SettingsStore(applicationContext.settingsStore)
        fetcher = FetchManager(http, dao)
    }
}
