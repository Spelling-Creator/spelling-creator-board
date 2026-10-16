package org.spellingcreator.board

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.spellingcreator.board.theme.SpellingBoardTheme
import org.spellingcreator.board.ui.BoardScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpellingBoardTheme {
                BoardScreen()
            }
        }
    }
}
