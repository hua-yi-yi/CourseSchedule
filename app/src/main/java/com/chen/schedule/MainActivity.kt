package com.chen.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.chen.schedule.ui.navigation.AppNavHost
import com.chen.schedule.ui.theme.ScheduleTheme
import com.chen.schedule.ui.theme.ThemePrefs
import com.chen.schedule.util.BackgroundFileManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeConfig by ThemePrefs.state.collectAsState()
            ScheduleTheme(themeConfig = themeConfig) {
                val bgBitmap = remember(themeConfig.customBackgroundPath) {
                    BackgroundFileManager.loadBitmap(themeConfig.customBackgroundPath)?.asImageBitmap()
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (bgBitmap != null) {
                        Image(
                            bitmap = bgBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (themeConfig.backgroundDim > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = themeConfig.backgroundDim))
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = if (bgBitmap != null) Color.Transparent else MaterialTheme.colorScheme.background
                    ) {
                        AppNavHost()
                    }
                }
            }
        }
    }
}
