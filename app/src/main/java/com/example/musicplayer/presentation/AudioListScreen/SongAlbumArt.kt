package com.example.musicplayer.presentation.AudioListScreen

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
fun SongAlbumArt(
    trackId: String,
    albumArtUri: Any?
) {
    val context = LocalContext.current

    val imageRequest = remember(trackId, albumArtUri) {
        ImageRequest.Builder(context)
            .data(albumArtUri)
            .size(150, 150)
            .crossfade(false)
            .memoryCacheKey("album_$trackId")
            .diskCacheKey("album_$trackId")
            .build()
    }

    AsyncImage(
        model = imageRequest,
        contentDescription = null,
        modifier = Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Crop,
        placeholder = painterResource(R.drawable.app_icon),
        error = painterResource(R.drawable.app_icon),
        fallback = painterResource(R.drawable.app_icon)
    )
}