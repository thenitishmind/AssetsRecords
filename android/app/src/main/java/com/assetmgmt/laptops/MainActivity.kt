package com.assetmgmt.laptops

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.assetmgmt.laptops.navigation.AppNavHost
import com.assetmgmt.laptops.ui.theme.AssetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AssetTheme { AppNavHost() } }
    }
}
