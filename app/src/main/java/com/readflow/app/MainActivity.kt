package com.readflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.readflow.app.ui.navigation.ReadFlowNavGraph
import com.readflow.app.ui.theme.ReadFlowTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReadFlowTheme {
                ReadFlowNavGraph()
            }
        }
    }
}
