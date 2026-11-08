package com.creativeclasses.callingapp

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The imported Excel, shown the way chat and form apps show an attached file: name, details and Change. */
@Composable
fun ExcelUploadCard(vm: CallingViewModel, onChange: () -> Unit) {
    val details = listOfNotNull(
        vm.excelSheet.takeIf { it.isNotBlank() }?.let { "Sheet: $it" },
        vm.students.size.takeIf { it > 0 }?.let { "$it students" },
    ).joinToString(" · ").ifEmpty { "Select the sheet below" }
    UploadCard(
        leading = { Text("\uD83D\uDCC4", style = MaterialTheme.typography.headlineMedium) },
        title = vm.excelFile.ifBlank { "Excel imported" },
        details = details,
        onChange = onChange,
    )
}

/** The uploaded marks image as a thumbnail; tap it to check names and marks on the full image. */
@Composable
fun MarksImageCard(vm: CallingViewModel, onChange: () -> Unit) {
    val file = vm.marksImageFile() ?: return
    var open by remember { mutableStateOf(false) }
    val thumb by rememberImage(file, maxSide = 256)
    UploadCard(
        leading = {
            thumb?.let { Image(it, "Marks image", Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop) }
        },
        title = "Marks image uploaded",
        details = "${vm.imageRows.size} rows read · tap to view",
        onChange = onChange,
        onOpen = { open = true },
    )
    if (open) ImageViewer(file) { open = false }
}

@Composable
private fun UploadCard(
    leading: @Composable () -> Unit, title: String, details: String, onChange: () -> Unit, onOpen: (() -> Unit)? = null,
) {
    ElevatedCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().let { if (onOpen != null) it.clickable(onClick = onOpen) else it }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("\u2713 $title", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(details, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onChange) { Text("Change") }
        }
    }
}

/** Full-screen image with pinch to zoom and drag to move. */
@Composable
private fun ImageViewer(file: File, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            ZoomableImage(file, Modifier.fillMaxSize())
            TextButton(onClick = onClose, Modifier.align(Alignment.TopEnd).statusBarsPadding()) { Text("Close", color = Color.White) }
        }
    }
}

/** The marks image with pinch to zoom and drag to move, clipped to [modifier]'s bounds. */
@Composable
fun ZoomableImage(file: File, modifier: Modifier) {
    val image by rememberImage(file, maxSide = 2048)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val zoom = rememberTransformableState { change, pan, _ ->
        scale = (scale * change).coerceIn(1f, 6f)
        offset += pan
    }
    // Double tap zooms in, like photo apps; another double tap goes back to the whole image.
    Box(
        modifier.clipToBounds().transformable(zoom).pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { tap ->
                if (scale > 1f) {
                    scale = 1f
                    offset = Offset.Zero
                } else {
                    scale = 2.5f
                    // Keeps the tapped spot under the finger (the layer scales around its centre).
                    offset = (Offset(size.width / 2f, size.height / 2f) - tap) * (scale - 1f)
                }
            })
        },
    ) {
        image?.let {
            Image(
                it, "Marks image",
                Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/** Decodes [file] off the main thread, downsampled so its longer side is at most about [maxSide] pixels. */
@Composable
private fun rememberImage(file: File, maxSide: Int) = produceState<ImageBitmap?>(null, file) {
    value = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }.getOrNull()
    }
}
