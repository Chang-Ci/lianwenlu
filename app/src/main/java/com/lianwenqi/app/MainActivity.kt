package com.lianwenqi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lianwenqi.app.ui.navigation.RootNavHost
import com.lianwenqi.app.ui.theme.LianWenQiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = applicationContext.appContainer.prefs
            val night by prefs.nightMode.collectAsState(initial = false)
            LianWenQiTheme(darkTheme = night) {
                RootNavHost()
            }
        }
    }
}