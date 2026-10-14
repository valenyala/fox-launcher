package dev.foxlauncher.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Scale
import coil3.toBitmap
import dev.foxlauncher.R
import dev.foxlauncher.core.model.WallpaperFraming
import dev.foxlauncher.feature.wallpaper.engine.WallpaperGeometry
import kotlin.math.roundToInt

object WallpaperFramingTestTags {
    const val PICTURE = "wallpaper_framing_picture"
}

/**
 * Choosing the part of a picture the wallpaper shows (ADR-26). The picture fills the screen exactly
 * as the wallpaper will show it, through the same [WallpaperGeometry]: drag to move it, pinch to
 * zoom. Save keeps it; Reset centres it again; Cancel or Back leave it as it was.
 */
@Composable
fun WallpaperFramingEditor(
    picture: Any,
    initial: WallpaperFraming,
    onSave: (WallpaperFraming) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onCancel)
    var framing by rememberSaveable(stateSaver = FramingSaver) { mutableStateOf(initial) }
    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val image = rememberPicture(picture, constraints.maxWidth, constraints.maxHeight)
        when {
            image == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            image.bitmap == null -> Text(
                stringResource(R.string.wallpaper_framing_failed),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
            else -> FramedPicture(image.bitmap, framing, onFramingChange = { framing = it })
        }
        Text(
            stringResource(R.string.wallpaper_framing_hint),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp)
                .background(SCRIM, MaterialTheme.shapes.small)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(SCRIM)
                .navigationBarsPadding()
                .padding(8.dp),
        ) {
            val colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
            TextButton(onClick = { framing = WallpaperFraming.CENTERED }, colors = colors) {
                Text(stringResource(R.string.wallpaper_framing_reset))
            }
            Row {
                TextButton(onClick = onCancel, colors = colors) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = { onSave(framing) }, enabled = image?.bitmap != null, colors = colors) {
                    Text(stringResource(R.string.wallpaper_framing_save))
                }
            }
        }
    }
}

/** The picture across the whole box, the part [framing] chooses; drags and pinches change it. */
@Composable
private fun FramedPicture(bitmap: ImageBitmap, framing: WallpaperFraming, onFramingChange: (WallpaperFraming) -> Unit) {
    val currentFraming by rememberUpdatedState(framing)
    val imageW = bitmap.width.toFloat()
    val imageH = bitmap.height.toFloat()
    Canvas(
        Modifier
            .fillMaxSize()
            .testTag(WallpaperFramingTestTags.PICTURE)
            .pointerInput(bitmap) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val boxW = size.width.toFloat()
                    val boxH = size.height.toFloat()
                    val zoomed = WallpaperGeometry.zoomed(currentFraming, zoom, centroid.x, centroid.y, imageW, imageH, boxW, boxH)
                    onFramingChange(WallpaperGeometry.moved(zoomed, pan.x, pan.y, imageW, imageH, boxW, boxH))
                }
            },
    ) {
        val region = WallpaperGeometry.visibleRegion(imageW, imageH, size.width, size.height, framing)
        val srcW = (region.width * bitmap.width).roundToInt().coerceIn(1, bitmap.width)
        val srcH = (region.height * bitmap.height).roundToInt().coerceIn(1, bitmap.height)
        drawImage(
            bitmap,
            srcOffset = IntOffset(
                (region.left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - srcW),
                (region.top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - srcH),
            ),
            srcSize = IntSize(srcW, srcH),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.Medium,
        )
    }
}

/** A loaded picture; [bitmap] is null when it could not be opened. */
private class LoadedPicture(val bitmap: ImageBitmap?)

/**
 * The picture at about the size it fills the screen with (zooming in stretches it a little; the
 * wallpaper itself decodes the chosen part sharp). Null while it loads.
 */
@Composable
private fun rememberPicture(picture: Any, width: Int, height: Int): LoadedPicture? {
    val context = LocalContext.current
    var loaded by remember(picture) { mutableStateOf<LoadedPicture?>(null) }
    LaunchedEffect(picture, width, height) {
        if (width <= 0 || height <= 0) return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(picture).size(width, height).scale(Scale.FILL).build()
        val result = SingletonImageLoader.get(context).execute(request)
        loaded = LoadedPicture((result as? SuccessResult)?.image?.toBitmap()?.asImageBitmap())
    }
    return loaded
}

private val FramingSaver = listSaver<WallpaperFraming, Float>(
    save = { listOf(it.focusX, it.focusY, it.zoom) },
    restore = { WallpaperFraming(it[0], it[1], it[2]) },
)

/** Crops like [ContentScale.Crop], then zooms in by the framing's zoom: a thumbnail of the chosen part. */
internal class FramedScale(private val zoom: Float) : ContentScale {
    override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
        val crop = ContentScale.Crop.computeScaleFactor(srcSize, dstSize)
        val by = zoom.coerceIn(1f, WallpaperFraming.MAX_ZOOM)
        return ScaleFactor(crop.scaleX * by, crop.scaleY * by)
    }
}

/** Places a [FramedScale]d image so its box shows the part [framing] chooses. */
internal class FramedAlignment(private val framing: WallpaperFraming) : Alignment {
    override fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection): IntOffset {
        val (x, y) = WallpaperGeometry.framedOffset(size.width.toFloat(), size.height.toFloat(), space.width.toFloat(), space.height.toFloat(), framing)
        return IntOffset(x.roundToInt(), y.roundToInt())
    }
}

private val SCRIM = Color(0x99000000)
