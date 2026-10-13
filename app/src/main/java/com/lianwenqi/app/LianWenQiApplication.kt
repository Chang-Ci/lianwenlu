package com.lianwenqi.app

import android.app.Application
import android.content.Context
import com.lianwenqi.app.ai.AiClient
import com.lianwenqi.app.data.AppDatabase
import com.lianwenqi.app.data.BookRepository
import com.lianwenqi.app.data.ChatRepository
import com.lianwenqi.app.data.ConfigRepository
import com.lianwenqi.app.data.OutlineRepository
import com.lianwenqi.app.data.Prefs

/** 简单的依赖容器（ServiceLocator），供 ViewModel 获取 */
class AppContainer(val app: Application) {
    val context: Context get() = app.applicationContext
    val database: AppDatabase = AppDatabase.get(app)
    val aiClient: AiClient = AiClient()
    val bookRepository: BookRepository = BookRepository(database)
    val chatRepository: ChatRepository = ChatRepository(database)
    val outlineRepository: OutlineRepository = OutlineRepository(database)
    val configRepository: ConfigRepository = ConfigRepository(database, aiClient)
    val prefs: Prefs = Prefs(app)
}

class LianWenQiApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as LianWenQiApplication).container