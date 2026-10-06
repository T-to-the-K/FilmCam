package com.tk.filmcam.ui
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tk.filmcam.R




@Composable
fun ShutterButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ringColor = if (enabled) Color.White else Color.White.copy(alpha = 0.35f)
    val shutterDesc = stringResource(R.string.cd_shutter)

    Box(
        modifier = modifier
            .size(74.dp)
            .border(width = 3.dp, color = ringColor, shape = CircleShape)
            .padding(6.dp)
            .background(
                if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
                CircleShape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = shutterDesc },
        contentAlignment = Alignment.Center
    ) {}
}


@Composable
fun IconPill(
    icon: ImageVector,
    desc: String,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint by animateColorAsState(
        targetValue = when {
            !enabled -> Color.White.copy(alpha = 0.25f)
            active -> Color(0xFFFFC24B)
            else -> Color.White
        },
        label = "pillTint"
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .background(
                if (active) Color(0x33FFC24B) else Color.Black.copy(alpha = 0.4f),
                CircleShape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun ZoomReadout(zoom: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(48.dp)
            .width(64.dp)
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.zoom_readout, zoom),
            color = Color.White,
            fontSize = 13.sp
        )
    }
}

@Composable
fun StatusBar(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
fun Spacer8(modifier: Modifier = Modifier) = Spacer(modifier.width(8.dp))