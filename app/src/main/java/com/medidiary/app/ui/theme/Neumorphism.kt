package com.medidiary.app.ui.theme

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Neumorphism ("soft UI") tuned for Catppuccin Mocha.
 *
 * A raised element = same colour as the background + a light shadow up-left + a dark shadow
 * down-right. A pressed/selected element is "inset": slightly darker fill + inner shadows.
 * Shadows are drawn OUTSIDE the element's bounds, so give neumorphic elements ~8–12 dp of
 * breathing room from their parent's edge (don't clip the parent).
 */
object Neu {
    val Surface = Mocha.Base
    val InsetSurface = Mocha.Mantle
    /** Lighter than Base: the "light source" top-left highlight. */
    val LightShadow = Color(0xFF2E2E45)
    /** Darker than Crust: the bottom-right drop shadow. */
    val DarkShadow = Color(0xFF0B0B12)
    val EdgeHighlight = Color(0x1FCDD6F4) // Text @ 12 %
}

/**
 * @param inset true for pressed / selected / input fields.
 * @param elevation shadow offset + blur. 4–8 dp for buttons, 8–10 dp for cards.
 */
fun Modifier.neumorphic(
    cornerRadius: Dp = 20.dp,
    elevation: Dp = 6.dp,
    inset: Boolean = false,
    surface: Color = Neu.Surface,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return if (!inset) {
        this
            .drawBehind { drawOuterShadows(cornerRadius.toPx(), elevation.toPx()) }
            .clip(shape)
            .background(surface)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(Neu.EdgeHighlight, Color.Transparent)),
                shape = shape,
            )
    } else {
        this
            .clip(shape)
            .background(Neu.InsetSurface)
            .drawBehind { drawInnerShadows(cornerRadius.toPx(), elevation.toPx().coerceAtLeast(3.dp.toPx())) }
    }
}

private fun DrawScope.drawOuterShadows(r: Float, e: Float) {
    // Hardware-accelerated BlurMaskFilter needs Android 9+. Older phones get a flat (border-only) look.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || e <= 0f) return
    drawIntoCanvas { canvas ->
        val paint = Paint()
        val fp = paint.asFrameworkPaint()
        fp.isAntiAlias = true
        fp.maskFilter = BlurMaskFilter(e * 1.6f, BlurMaskFilter.Blur.NORMAL)
        fp.color = Neu.DarkShadow.toArgb()
        canvas.drawRoundRect(e, e, size.width + e, size.height + e, r, r, paint)
        fp.color = Neu.LightShadow.toArgb()
        canvas.drawRoundRect(-e, -e, size.width - e, size.height - e, r, r, paint)
    }
}

private fun DrawScope.drawInnerShadows(r: Float, e: Float) {
    val mid = Offset(size.width / 2f, size.height / 2f)
    drawRoundRect(
        brush = Brush.linearGradient(listOf(Neu.DarkShadow, Color.Transparent), start = Offset.Zero, end = mid),
        cornerRadius = CornerRadius(r),
        style = Stroke(width = e * 1.4f),
    )
    drawRoundRect(
        brush = Brush.linearGradient(listOf(Color.Transparent, Neu.LightShadow), start = mid, end = Offset(size.width, size.height)),
        cornerRadius = CornerRadius(r),
        style = Stroke(width = e),
    )
}
