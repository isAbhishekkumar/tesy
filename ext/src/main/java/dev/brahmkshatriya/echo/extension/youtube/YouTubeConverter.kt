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
                    // Store audio URL as empty for now - will be populated when track is loaded
                    put("audioUrl", "")
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
        
        // If no audio streams are available, add a fallback streamable
        if (streamables.isEmpty()) {
            streamables.add(
                server(
                    id = "fallback_${streamInfo.id}",
                    quality = 128,
                    title = "Fallback Audio Stream",
                    extras = mutableMapOf<String, String>().apply {
                        put("videoUrl", streamInfo.url)
                        put("videoId", streamInfo.id ?: "")
                        put("title", streamInfo.name)
                        put("audioUrl", "") // Empty - will need to be extracted differently
                    }
                )
            )
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
            title = "Audio Stream - ${audioStream.format?.name ?: "Unknown"} (${audioStream.averageBitrate ?: 0} kbps)",
            extras = mutableMapOf<String, String>().apply {
                put("itag", audioStream.itag.toString())
                put("format", audioStream.format?.name ?: "Unknown")
                put("bitrate", (audioStream.averageBitrate ?: 0).toString())
                put("mimeType", audioStream.format?.mimeType ?: "Unknown")
                put("contentLength", (audioStream.contentLength ?: 0).toString())
                put("trackId", streamInfo.id ?: "")
                put("trackTitle", streamInfo.name)
                put("videoUrl", streamInfo.url)
                // Store the actual audio URL - this is crucial for playback
                put("audioUrl", audioStream.url ?: "")
                // Add additional metadata for better user experience
                put("qualityLabel", getQualityLabel(audioStream.averageBitrate))
            }
        )
    }
    
    /**
     * Get a human-readable quality label for the bitrate
     */
    private fun getQualityLabel(bitrate: Int?): String {
        return when (bitrate) {
            null -> "Unknown"
            in 0..64 -> "Low (${bitrate} kbps)"
            in 65..128 -> "Medium (${bitrate} kbps)"
            in 129..192 -> "High (${bitrate} kbps)"
            in 193..320 -> "Very High (${bitrate} kbps)"
            else -> "Ultra (${bitrate} kbps)"
        }
    }
    
    /**
     * Convert Echo Streamable to Echo Streamable.Media for playback using proper framework pattern
     */
    fun toStreamableMedia(streamable: Streamable): Streamable.Media {
        // Extract the audio URL from extras
        var audioUrl = streamable.extras["audioUrl"] ?: streamable.extras["videoUrl"] ?: ""
        
        // If we don't have a direct audio URL, try to extract it from the video URL
        if (audioUrl.isNotEmpty() && !audioUrl.endsWith(".mp3") && !audioUrl.endsWith(".webm") && !audioUrl.endsWith(".m4a")) {
            // This might be a video URL, we might need to process it differently
            // For now, we'll use it as-is and let the player handle it
            println("Using video URL for audio playback: $audioUrl")
        }
        
        if (audioUrl.isEmpty()) {
            throw IllegalArgumentException("No audio URL found in streamable extras. Available keys: ${streamable.extras.keys}")
        }
        
        // Create a NetworkRequest for the audio URL
        val networkRequest = NetworkRequest(
            url = audioUrl,
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
                "Accept" to "audio/webm,audio/ogg,audio/wav,audio/*;q=0.9,application/ogg;q=0.7,video/*;q=0.6,*/*;q=0.5",
                "Accept-Language" to "en-US,en;q=0.9",
                "Accept-Encoding" to "gzip, deflate, br",
                "Connection" to "keep-alive",
                "Sec-Fetch-Dest" to "audio",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "cross-site"
            )
        )
        
        // Create an HTTP source for the audio stream
        val httpSource = Streamable.Source.Http(
            request = networkRequest,
            type = Streamable.SourceType.Progressive,
            quality = streamable.quality,
            title = streamable.title ?: "YouTube Audio Stream (${streamable.quality} kbps)",
            isVideo = false,
            isLive = false
        )
        
        // Return a Server media with the source
        return Streamable.Media.Server(
            sources = listOf(httpSource),
            merged = false // Don't merge sources, allow user to switch between qualities
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