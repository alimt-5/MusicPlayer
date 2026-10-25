package com.example.musicplayer.musicFeature.presentation.screens.list

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.musicplayer.R

@Composable
fun SongAlbumArt(albumArtUri: String?) {
    val context = LocalContext.current
    val request = remember(albumArtUri) {
        ImageRequest.Builder(context)
            .data(albumArtUri)
            .size(96, 96)
            .crossfade(false)
            .memoryCacheKey(albumArtUri ?: "no_album_art")
            .diskCacheKey(albumArtUri ?: "no_album_art")
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Crop,
        placeholder = painterResource(R.drawable.app_icon),
        error = painterResource(R.drawable.app_icon),
        fallback = painterResource(R.drawable.app_icon)
    )
}
