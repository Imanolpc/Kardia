package imanolpc.kardia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import imanolpc.kardia.ui.generator.GeneratorScreen
import imanolpc.kardia.ui.generator.GeneratorViewModel
import imanolpc.kardia.ui.theme.KardiaTheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
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
