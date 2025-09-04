package dev.brahmkshatriya.echo.extension.youtube

import dev.brahmkshatriya.echo.common.models.*
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Streamable.Companion.server
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
                    put("thumbnailUrl", streamInfoItem.thumbnails.firstOrNull()?.url ?: "")
                }
            )
        )
        
        return Track(
            id = streamInfoItem.url,
            title = streamInfoItem.name,
            type = Track.Type.Song,
            cover = streamInfoItem.thumbnails.firstOrNull()?.url?.toImageHolder(),
            artists = listOf(toArtist(streamInfoItem)),
            duration = streamInfoItem.duration * 1000L, // Convert to milliseconds
            playedDuration = null,
            plays = streamInfoItem.viewCount,
            releaseDate = null,
            description = null,
            background = streamInfoItem.thumbnails.firstOrNull()?.url?.toImageHolder(),
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
                cover = streamInfo.thumbnails.firstOrNull()?.url?.toImageHolder(),
                artists = listOf(toArtist(streamInfo)),
                trackCount = null,
                duration = streamInfo.duration * 1000L,
                releaseDate = null,
                description = streamInfo.description?.content,
                background = streamInfo.thumbnails.firstOrNull()?.url?.toImageHolder(),
                label = null,
                isExplicit = false,
                subtitle = "Single",
                extras = mapOf<String, String>(
                    "uploadDate" to (uploadDate?.toString() ?: ""),
                    "url" to streamInfo.url
                )
            )
        }
        
        return Track(
            id = streamInfo.url,
            title = streamInfo.name,
            type = Track.Type.Song,
            cover = streamInfo.thumbnails.firstOrNull()?.url?.toImageHolder(),
            artists = listOf(toArtist(streamInfo)),
            album = album,
            duration = streamInfo.duration * 1000L, // Convert to milliseconds
            playedDuration = null,
            plays = streamInfo.viewCount,
            releaseDate = null,
            description = streamInfo.description?.content,
            background = streamInfo.thumbnails.firstOrNull()?.url?.toImageHolder(),
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
            isPlayable = if (streamables.isNotEmpty()) Track.Playable.Yes else Track.Playable.No as Track.Playable,
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
     * Convert NewPipe StreamInfoItem to Echo Artist
     */
    private fun toArtist(streamInfoItem: StreamInfoItem): Artist {
        return toArtist(
            name = streamInfoItem.uploaderName ?: "Unknown Artist",
            url = streamInfoItem.uploaderUrl,
            thumbnailUrl = streamInfoItem.uploaderAvatars.firstOrNull()?.url
        )
    }
    
    /**
     * Convert NewPipe StreamInfo to Echo Artist
     */
    private fun toArtist(streamInfo: StreamInfo): Artist {
        return toArtist(
            name = streamInfo.uploaderName ?: "Unknown Artist",
            url = streamInfo.uploaderUrl,
            thumbnailUrl = streamInfo.uploaderAvatars.firstOrNull()?.url
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
                put("mimeType", audioStream.format?.mimeType ?: "Unknown")
                put("contentLength", (audioStream.contentLength?.toString() ?: "0"))
                put("trackId", streamInfo.id ?: "")
                put("trackTitle", streamInfo.name)
                put("videoUrl", streamInfo.url)
                put("audioUrl", audioStream.url ?: "")
            }
        )
    }
    
    /**
     * Convert Echo Streamable to Echo Streamable.Media for playback using proper framework pattern
     */
    fun toStreamableMedia(streamable: Streamable): Streamable.Media {
        // Extract the audio URL from extras
        val audioUrl = streamable.extras["audioUrl"] ?: streamable.extras["videoUrl"] ?: ""
        
        // For now, return a simple implementation
        // This will need to be adjusted based on the actual Echo framework API
        return object : Streamable.Media() {
            override val uri: String = audioUrl
            override val headers: Map<String, String> = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
        }
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