package app.galletascanner.dev

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() =
    application {
        Window(onCloseRequest = ::exitApplication, title = "Galleta Scanner") {
            App()
        }
    }
