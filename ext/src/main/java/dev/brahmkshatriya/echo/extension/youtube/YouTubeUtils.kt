package dev.brahmkshatriya.echo.extension.youtube

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString

/**
 * Utility class for YouTube-specific operations based on SimpMusic approach
 * Handles signature deobfuscation and stream URL extraction
 */
class YouTubeUtils(
    private val downloader: Downloader,
) {
    init {
        // NewPipe is already initialized with our downloader
    }
    
    /**
     * Get signature timestamp for a video
     */
    fun getSignatureTimestamp(videoId: String): Result<Int> =
        runCatching {
            YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
        }
    
    /**
     * Get stream URL with proper signature deobfuscation
     * Based on SimpMusic implementation
     */
    fun getStreamUrl(
        url: String?,
        signatureCipher: String?,
        videoId: String,
    ): String? =
        try {
            println("DEBUG: YouTubeUtils - Getting stream url: ${url ?: signatureCipher}")
            
            val finalUrl = url ?: signatureCipher?.let { cipher ->
                val params = parseQueryString(cipher)
                val obfuscatedSignature =
                    params["s"]
                        ?: throw ParsingException("Could not parse cipher signature")
                val signatureParam =
                    params["sp"]
                        ?: throw ParsingException("Could not parse cipher signature parameter")
                val urlBuilder =
                    params["url"]?.let { URLBuilder(it) }
                        ?: throw ParsingException("Could not parse cipher url")
                
                // Deobfuscate signature using NewPipe's JavaScript player manager
                urlBuilder.parameters[signatureParam] =
                    YoutubeJavaScriptPlayerManager.deobfuscateSignature(
                        videoId,
                        obfuscatedSignature,
                    )
                urlBuilder.toString()
            } ?: throw ParsingException("Could not find format url")
            
            // Apply throttling parameter deobfuscation
            YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(
                videoId,
                finalUrl,
            )
        } catch (e: Exception) {
            println("ERROR: YouTubeUtils - Failed to get stream URL: ${e.message}")
            null
        }
    
    /**
     * Helper function to safely extract video ID from various URL formats
     */
    fun extractVideoId(url: String): String {
        return try {
            val patterns = listOf(
                Regex("youtube\\.com/watch\\?v=([^&]+)"),
                Regex("youtu\\.be/([^?]+)"),
                Regex("youtube\\.com/embed/([^?]+)"),
                Regex("youtube\\.com/v/([^?]+)")
            )
            
            for (pattern in patterns) {
                val matchResult = pattern.find(url)
                if (matchResult != null) {
                    return matchResult.groupValues[1]
                }
            }
            
            // Fallback: try to extract from URL directly
            url.substringAfterLast("/").takeIf { it.length == 11 } ?: url
        } catch (e: Exception) {
            println("ERROR: YouTubeUtils - Failed to extract video ID from: $url, error: ${e.message}")
            url
        }
    }
    
    /**
     * Check if a URL is accessible (returns 2xx status code)
     */
    fun isUrlAccessible(url: String): Boolean {
        return try {
            val request = okhttp3.Request.Builder()
                .url(url)
                .head()
                .build()
            
            val response = (downloader as YouTubeDownloaderImpl).client.newCall(request).execute()
            response.close()
            response.code in 200..299
        } catch (e: Exception) {
            println("ERROR: YouTubeUtils - URL not accessible: $url, error: ${e.message}")
            false
        }
    }
}