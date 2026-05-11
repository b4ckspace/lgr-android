package de.uhsemann.lgr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import de.uhsemann.lgr.ui.navigation.AppNavigation
import de.uhsemann.lgr.ui.screens.LoginScreen
import de.uhsemann.lgr.ui.theme.LgrTheme
import de.uhsemann.lgr.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LgrTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (viewModel.auth.data?.authenticated == true || viewModel.readonlyMode) {
                        AppNavigation(viewModel)
                    } else {
                        // LoginScreen stays mounted during loading so remember{} state is preserved
                        LoginScreen(viewModel)
                    }
                }
            }
        }
    }
}
