package com.kardia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kardia.app.ui.generator.GeneratorScreen
import com.kardia.app.ui.generator.GeneratorViewModel
import com.kardia.app.ui.theme.KardiaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KardiaTheme {
                // Un contenedor de superficie que utiliza el color 'background' del tema
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: GeneratorViewModel = viewModel()
                    GeneratorScreen(viewModel = viewModel)
                }
            }
        }
    }
}
