package com.tk.filmcam.ui
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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

/**
 * Manual focus. The slider maps left-to-right onto the lens's focus throw, far
 * to near, and drags the camera out of autofocus the moment it moves. The label
 * reads AF while the camera still owns focus, which is also how it gets back:
 * tapping the viewfinder. Disabled outright on a lens with no focus range, so
 * it never looks like it is doing something it cannot.
 */
@Composable
fun FocusSlider(
    fraction: Float,
    manual: Boolean,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (manual) "MF" else "AF",
            color = if (manual) Color(0xFFFFC24B) else Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(28.dp)
        )
        Slider(
            value = fraction,
            onValueChange = onChange,
            enabled = enabled,
            modifier = Modifier.width(180.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFFFFC24B),
                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                disabledThumbColor = Color.White.copy(alpha = 0.3f),
                disabledActiveTrackColor = Color.White.copy(alpha = 0.12f),
                disabledInactiveTrackColor = Color.White.copy(alpha = 0.1f)
            )
        )
    }
}