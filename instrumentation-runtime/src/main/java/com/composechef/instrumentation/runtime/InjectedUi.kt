package com.composechef.instrumentation.runtime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** UI payload called by bytecode inserted by the instrumentation plugin. */
@Composable
fun InjectedBadge() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFD54F))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Hey ! i was Injected at Runtime",
            color = Color.Black,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
