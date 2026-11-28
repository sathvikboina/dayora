package com.sonder.app.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.sonder.app.data.local.entity.MomentPhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sonder.app.util.PhotoVaultManager

/**
 * Small, memory-frugal captioned photo renderer. Files are decoded at
 * thumbnail resolution off the main thread, so a camera photo never stalls a
 * scroll.
 */
@Composable
fun PhotoThumbnail(
    uriString: String? = null,
    fileName: String? = null,
    modifier: Modifier = Modifier,
    maxDimension: Int = 360
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uriString, fileName) {
        value = withContext(Dispatchers.IO) {
            val manager = PhotoVaultManager(context)
            val decoded = when {
                fileName != null -> manager.decode(fileName, maxDimension)
                uriString != null -> manager.decodeUri(Uri.parse(uriString), maxDimension)
                else -> null
            }
            decoded?.asImageBitmap()
        }
    }
    Box(
        modifier = modifier.background(
            MaterialTheme.colorScheme.surfaceVariant,
            RoundedCornerShape(10.dp)
        )
    ) {
        bitmap?.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Up to three small thumbnails, used on the Calendar moment cards. */
@Composable
fun MomentPhotoStrip(
    photos: List<MomentPhotoEntity>,
    modifier: Modifier = Modifier,
    thumbSize: Int = 54
) {
    if (photos.isEmpty()) return
    Row(modifier = modifier) {
        photos.take(PhotoVaultManager.MAX_PHOTOS_PER_MOMENT).forEachIndexed { index, photo ->
            if (index > 0) Spacer(modifier = Modifier.width(6.dp))
            PhotoThumbnail(
                fileName = photo.fileName,
                modifier = Modifier.size(thumbSize.dp),
                maxDimension = 200
            )
        }
    }
}

/**
 * Photos with their small captions, shown on Home. The caption is what makes a
 * photo a memory rather than an attachment.
 */
@Composable
fun MomentPhotoDetails(
    photos: List<MomentPhotoEntity>,
    modifier: Modifier = Modifier,
    thumbSize: Int = 52
) {
    if (photos.isEmpty()) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        photos.take(PhotoVaultManager.MAX_PHOTOS_PER_MOMENT).forEach { photo ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                PhotoThumbnail(
                    fileName = photo.fileName,
                    modifier = Modifier.size(thumbSize.dp),
                    maxDimension = 220
                )
                val caption = photo.caption.orEmpty().trim()
                if (caption.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 2
                    )
                }
            }
        }
    }
}
