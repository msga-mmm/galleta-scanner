import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun App() {
    var count by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Count: $count",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(
                onClick = { count -= 1 },
                enabled = count > 0
            ) {
                Text("-")
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = { count += 1 },
                enabled = count < 10
            ) {
                Text("+")
            }
        }
    }
}
