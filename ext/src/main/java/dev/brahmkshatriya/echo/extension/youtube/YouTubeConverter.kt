package dev.brahmkshatriya.echo.extension.youtube

import dev.brahmkshatriya.echo.common.models.*
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Streamable.Companion.server
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toMedia
import dev.brahmkshatriya.echo.common.settings.Settings
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.AudioStream

class YouTubeConverter(private val settings: Settings) {
    
    /**
     * Convert NewPipe StreamInfoItem to Echo Track
     * Used for search results and track listings
     */
    fun toTrack(streamInfoItem: StreamInfoItem): Track {
        val streamables = mutableListOf<Streamable>()
        
        // Add a basic streamable for search results (will be detailed when track is loaded)
        streamables.add(
            server(
                id = "server_${streamInfoItem.url.hashCode()}",
                quality = 128, // Default quality for search results
                title = "Audio Stream",
                extras = mutableMapOf<String, String>().apply {
                    put("videoUrl", streamInfoItem.url)
                    put("videoId", extractVideoId(streamInfoItem.url))
                    put("title", streamInfoItem.name)
                    put("uploader", streamInfoItem.uploaderName ?: "")
                    put("duration", (streamInfoItem.duration * 1000L).toString())
                    put("thumbnailUrl", streamInfoItem.thumbnailUrl ?: "")
                }
            )
        )
        
        return Track(
            id = streamInfoItem.url,
            title = streamInfoItem.name,
            type = Track.Type.Song,
            cover = streamInfoItem.thumbnailUrl?.toImageHolder(),
            artists = listOf(toArtist(streamInfoItem)),
            duration = streamInfoItem.duration * 1000L, // Convert to milliseconds
            playedDuration = null,
            plays = streamInfoItem.viewCount,
            releaseDate = null,
            description = null,
            background = streamInfoItem.thumbnailUrl?.toImageHolder(),
            genres = emptyList(),
            isrc = null,
            albumOrderNumber = null,
            albumDiscNumber = null,
            playlistAddedDate = null,
            isExplicit = false, // NewPipe doesn't provide this info easily
            subtitle = streamInfoItem.uploaderName,
            extras = mapOf(
                "viewCount" to (streamInfoItem.viewCount?.toString() ?: "0"),
                "uploadDate" to (streamInfoItem.textualUploadDate ?: ""),
                "uploaderUrl" to (streamInfoItem.uploaderUrl ?: ""),
                "url" to streamInfoItem.url,
                "videoId" to extractVideoId(streamInfoItem.url)
            ),
            isPlayable = Track.Playable.Yes,
            streamables = streamables,
            isRadioSupported = true,
            isFollowable = false,
            isSaveable = true,
            isLikeable = true,
            isHideable = true,
            isShareable = true
        )
    }
    
    /**
     * Convert NewPipe StreamInfo to Echo Track with full details
     * Used when loading individual track details
     */
    fun toTrack(streamInfo: StreamInfo): Track {
        val streamables = mutableListOf<Streamable>()
        
        // Add all available audio streams with different qualities
        streamInfo.audioStreams.forEach { audioStream ->
            streamables.add(toStreamable(audioStream, streamInfo))
        }
        
        // Create album from upload date if available
        val album = streamInfo.uploadDate?.let { uploadDate ->
            Album(
                id = "album_${streamInfo.id}",
                title = "Uploaded $uploadDate",
                type = null,
                cover = streamInfo.thumbnailUrl?.toImageHolder(),
                artists = listOf(toArtist(streamInfo)),
                trackCount = null,
                duration = streamInfo.length * 1000L,
                releaseDate = null,
                description = streamInfo.description?.content,
                background = streamInfo.thumbnailUrl?.toImageHolder(),
                label = null,
                isExplicit = false,
                subtitle = "Single",
                extras = mapOf(
                    "uploadDate" to uploadDate,
                    "url" to streamInfo.url
                )
            )
        }
        
        return Track(
            id = streamInfo.url,
            title = streamInfo.name,
            type = Track.Type.Song,
            cover = streamInfo.thumbnailUrl?.toImageHolder(),
            artists = listOf(toArtist(streamInfo)),
            album = album,
            duration = streamInfo.length * 1000L, // Convert to milliseconds
            playedDuration = null,
            plays = streamInfo.viewCount,
            releaseDate = null,
            description = streamInfo.description?.content,
            background = streamInfo.thumbnailUrl?.toImageHolder(),
            genres = emptyList(),
            isrc = null,
            albumOrderNumber = null,
            albumDiscNumber = null,
            playlistAddedDate = null,
            isExplicit = false,
            subtitle = streamInfo.uploaderName,
            extras = mapOf(
                "viewCount" to (streamInfo.viewCount?.toString() ?: "0"),
                "uploadDate" to (streamInfo.textualUploadDate ?: ""),
                "uploaderUrl" to (streamInfo.uploaderUrl ?: ""),
                "url" to streamInfo.url,
                "videoId" to streamInfo.id,
                "likeCount" to (streamInfo.likeCount?.toString() ?: "0"),
                "dislikeCount" to (streamInfo.dislikeCount?.toString() ?: "0")
            ),
            isPlayable = if (streamables.isNotEmpty()) Track.Playable.Yes else Track.Playable.No,
            streamables = streamables,
            isRadioSupported = true,
            isFollowable = false,
            isSaveable = true,
            isLikeable = true,
            isHideable = true,
            isShareable = true
        )
    }
    
    /**
     * Convert NewPipe StreamInfo to Echo Artist
     */
    private fun toArtist(streamInfoItem: StreamInfoItem): Artist {
        return toArtist(
            name = streamInfoItem.uploaderName ?: "Unknown Artist",
            url = streamInfoItem.uploaderUrl,
            thumbnailUrl = streamInfoItem.uploaderAvatarUrl
        )
    }
    
    /**
     * Convert NewPipe StreamInfo to Echo Artist
     */
    private fun toArtist(streamInfo: StreamInfo): Artist {
        return toArtist(
            name = streamInfo.uploaderName ?: "Unknown Artist",
            url = streamInfo.uploaderUrl,
            thumbnailUrl = streamInfo.uploaderAvatarUrl
        )
    }
    
    /**
     * Create Echo Artist from basic info
     */
    private fun toArtist(name: String, url: String?, thumbnailUrl: String?): Artist {
        return Artist(
            id = url ?: "",
            name = name,
            cover = thumbnailUrl?.toImageHolder(),
            bio = null,
            background = thumbnailUrl?.toImageHolder(),
            banners = emptyList(),
            subtitle = null,
            extras = mapOf(
                "url" to (url ?: ""),
                "channelUrl" to (url ?: "")
            )
        )
    }
    
    /**
     * Convert NewPipe AudioStream to Echo Streamable using proper framework pattern
     */
    private fun toStreamable(audioStream: AudioStream, streamInfo: StreamInfo): Streamable {
        return server(
            id = "audio_${audioStream.itag}_${audioStream.averageBitrate}",
            quality = audioStream.averageBitrate ?: 128,
            title = "Audio Stream - ${audioStream.format?.name ?: "Unknown"}",
            extras = mutableMapOf<String, String>().apply {
                put("itag", audioStream.itag.toString())
                put("format", audioStream.format?.name ?: "Unknown")
                put("bitrate", (audioStream.averageBitrate ?: 0).toString())
                put("mimeType", audioStream.mimeType ?: "Unknown")
                put("contentLength", (audioStream.contentLength ?: 0).toString())
                put("trackId", streamInfo.id)
                put("trackTitle", streamInfo.name)
                put("videoUrl", streamInfo.url)
                put("audioUrl", audioStream.url)
            }
        )
    }
    
    /**
     * Convert Echo Streamable to Echo Streamable.Media for playback using proper framework pattern
     */
    fun toStreamableMedia(streamable: Streamable): Streamable.Media {
        // Extract the audio URL from extras
        val audioUrl = streamable.extras["audioUrl"] ?: streamable.extras["videoUrl"] ?: ""
        
        return toMedia(
            url = audioUrl,
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
                "Accept" to "audio/webm,audio/ogg,audio/wav,audio/*;q=0.9,application/ogg;q=0.7,video/*;q=0.6,*/*;q=0.5",
                "Accept-Language" to "en-US,en;q=0.5",
                "Accept-Encoding" to "gzip, deflate, br",
                "Connection" to "keep-alive",
                "Sec-Fetch-Dest" to "audio",
                "Sec-Fetch-Mode" to "no-cors",
                "Sec-Fetch-Site" to "cross-site",
                "Referer" to "https://www.youtube.com/"
            )
        )
    }
    
    /**
     * Extract YouTube video ID from URL
     */
    private fun extractVideoId(url: String): String {
        return try {
            val patterns = listOf(
                Regex("youtube\\.com/watch\\?v=([^&]+)"),
                Regex("youtu\\.be/([^?]+)"),
                Regex("youtube\\.com/embed/([^?]+)")
            )
            
            for (pattern in patterns) {
                val matchResult = pattern.find(url)
                if (matchResult != null) {
                    return matchResult.groupValues[1]
                }
            }
            url.substringAfterLast("/").take(11) // Fallback
        } catch (e: Exception) {
            url
        }
    }
    
    /**
     * Parse duration from YouTube format to milliseconds
     */
    fun parseDurationToMillis(duration: String?): Long? {
        if (duration.isNullOrBlank()) return null
        
        return try {
            // Handle different duration formats
            when {
                duration.contains(":") -> {
                    // Format: MM:SS or HH:MM:SS
                    val parts = duration.split(":").map { it.trim().toIntOrNull() ?: 0 }
                    when (parts.size) {
                        2 -> parts[0] * 60 * 1000L + parts[1] * 1000L // MM:SS
                        3 -> parts[0] * 60 * 60 * 1000L + parts[1] * 60 * 1000L + parts[2] * 1000L // HH:MM:SS
                        else -> null
                    }
                }
                duration.endsWith("s") -> {
                    // Format: 45s (seconds)
                    duration.removeSuffix("s").toLongOrNull()?.times(1000)
                }
                duration.endsWith("m") -> {
                    // Format: 3m (minutes)
                    duration.removeSuffix("m").toLongOrNull()?.times(60 * 1000)
                }
                else -> {
                    // Try to parse as plain number (assume seconds)
                    duration.toLongOrNull()?.times(1000)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get the best available audio stream URL
     */
    fun getBestAudioStream(streamInfo: StreamInfo): String? {
        return streamInfo.audioStreams
            .sortedByDescending { it.averageBitrate ?: 0 }
            .firstOrNull()
            ?.url
    }
    
    /**
     * Get audio stream by preferred quality
     */
    fun getAudioStreamByQuality(streamInfo: StreamInfo, preferredBitrate: Int = 128): String? {
        return streamInfo.audioStreams
            .sortedWith(compareBy<AudioStream> { 
                kotlin.math.abs((it.averageBitrate ?: 0) - preferredBitrate) 
            })
            .firstOrNull()
            ?.url
    }
}