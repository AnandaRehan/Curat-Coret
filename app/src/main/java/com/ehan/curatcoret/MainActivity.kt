package com.ehan.curatcoret

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.ehan.curatcoret.ui.DrawingScreen
import com.ehan.curatcoret.ui.theme.CuratCoretTheme

class MainActivity : ComponentActivity() {

    private val viewModel: CanvasViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            CuratCoretTheme {
                DrawingScreen(viewModel)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.saveNow()
    }
}
