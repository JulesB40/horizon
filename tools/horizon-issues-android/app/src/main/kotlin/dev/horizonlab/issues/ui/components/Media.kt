package dev.horizonlab.issues.ui.components

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import dev.horizonlab.issues.ui.theme.Theme
import dev.horizonlab.issues.ui.theme.Type
import kotlinx.coroutines.delay

enum class MediaKind { IMAGE, VIDEO }

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "avif")
private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mov", "m4v", "mkv", "3gp")

/**
 * What a link points at, from the file name in its path (query and fragment ignored): Discord attachment links
 * (`cdn.discordapp.com/attachments/.../clip.mp4?ex=...`), which Kira copies into issues, end in their file name.
 */
fun mediaKind(url: String): MediaKind? {
    if (!url.startsWith("http://") && !url.startsWith("https://")) return null
    val path = url.substringBefore('#').substringBefore('?')
    val ext = path.substringAfterLast('/').substringAfterLast('.', "").lowercase()
    return when (ext) {
        in IMAGE_EXTENSIONS -> MediaKind.IMAGE
        in VIDEO_EXTENSIONS -> MediaKind.VIDEO
        else -> null
    }
}

private fun isDiscord(url: String) = url.contains("discordapp.com/") || url.contains("discordapp.net/")

/** An image, with a link row instead when it can't load (an expired Discord link, say). */
@Composable
fun MediaImage(url: String, name: String) {
    val p = Theme.palette
    var failed by remember(url) { mutableStateOf(false) }
    var loading by remember(url) { mutableStateOf(true) }
    if (failed) {
        MediaFallback(url, name, MediaKind.IMAGE)
        return
    }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (loading) 140.dp else 0.dp, max = 420.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.grouped)
            .border(1.dp, p.border, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
            contentDescription = name,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
            onState = { state ->
                loading = state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Empty
                if (state is AsyncImagePainter.State.Error) failed = true
            },
        )
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = p.tertiary, strokeWidth = 2.dp)
    }
}

/** A video played in place: tap to play or pause, with its progress along the bottom. */
@Composable
fun MediaVideo(url: String, name: String) {
    val p = Theme.palette
    var started by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    var prepared by remember(url) { mutableStateOf(false) }
    var playing by remember(url) { mutableStateOf(false) }
    var ratio by remember(url) { mutableFloatStateOf(16f / 9f) }
    var progress by remember(url) { mutableFloatStateOf(0f) }
    var view by remember(url) { mutableStateOf<VideoView?>(null) }

    if (failed) {
        MediaFallback(url, name, MediaKind.VIDEO)
        return
    }

    DisposableEffect(url) {
        onDispose { view?.stopPlayback() }
    }
    LaunchedEffect(playing, prepared) {
        while (playing && prepared) {
            view?.let { v -> if (v.duration > 0) progress = v.currentPosition.toFloat() / v.duration }
            delay(200)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .border(1.dp, p.border, RoundedCornerShape(12.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio.coerceIn(0.5f, 2.4f))
                .clickable {
                    val v = view
                    when {
                        !started -> started = true
                        v == null || !prepared -> Unit
                        v.isPlaying -> {
                            v.pause()
                            playing = false
                        }
                        else -> {
                            v.start()
                            playing = true
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (started) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setOnPreparedListener { mp ->
                                if (mp.videoWidth > 0 && mp.videoHeight > 0) ratio = mp.videoWidth.toFloat() / mp.videoHeight
                                prepared = true
                                playing = true
                                start()
                            }
                            setOnCompletionListener {
                                playing = false
                                progress = 1f
                            }
                            setOnErrorListener { _, _, _ ->
                                failed = true
                                true
                            }
                            setVideoURI(Uri.parse(url))
                            view = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (started && !prepared) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White.copy(alpha = 0.8f), strokeWidth = 2.dp)
            } else if (!playing) {
                Box(
                    Modifier.size(54.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
            if (!started) {
                Text(
                    name, style = Type.xs2, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                )
            }
        }
        if (started) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = p.primary,
                trackColor = Color.White.copy(alpha = 0.12f),
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

/** A media link that doesn't load: its name, why, and a way to open it. */
@Composable
private fun MediaFallback(url: String, name: String, kind: MediaKind) {
    val p = Theme.palette
    val uri = LocalUriHandler.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.grouped)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .clickable { runCatching { uri.openUri(url) } }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.BrokenImage, null, tint = p.tertiary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = Type.sm.copy(fontWeight = FontWeight.Medium), color = p.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    isDiscord(url) -> "The Discord link has expired; the Discord thread keeps the file."
                    kind == MediaKind.VIDEO -> "This video can't play here."
                    else -> "This image doesn't load."
                },
                style = Type.xs2, color = p.tertiary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open", tint = p.secondary, modifier = Modifier.size(16.dp))
    }
}
