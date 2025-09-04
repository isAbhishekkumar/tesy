package dev.brahmkshatriya.echo.extension.youtube

import android.util.Log
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Utility class to handle Kotlin-Java compatibility issues
 * Specifically addresses the URLEncoder method signature problem
 */
object CompatibilityUtils {
    private const val TAG = "CompatibilityUtils"
    
    /**
     * Safely encode URL parameters handling the compatibility issue
     * between NewPipe (Java) and Android runtime
     */
    fun encodeUrlParameter(input: String): String {
        return try {
            // Try the modern method first (Java 8+)
            URLEncoder.encode(input, StandardCharsets.UTF_8.name())
        } catch (e: NoSuchMethodError) {
            Log.w(TAG, "Modern URLEncoder.encode not available, using fallback", e)
            try {
                // Fallback to legacy method (Android compatible)
                @Suppress("DEPRECATION")
                val result = URLEncoder.encode(input, "UTF-8")
                Log.d(TAG, "Used fallback encoding for: $input -> $result")
                result
            } catch (e2: Exception) {
                Log.e(TAG, "Both encoding methods failed, using manual fallback", e2)
                // Final fallback: manual encoding of common characters
                input.replace(" ", "+")
                    .replace("&", "%26")
                    .replace("=", "%3D")
                    .replace("?", "%3F")
                    .replace("#", "%23")
                    .replace("/", "%2F")
                    .replace("\\", "%5C")
            }
        } catch (e: Exception) {
            Log.e(TAG, "URL encoding failed", e)
            // Return original input if all methods fail
            input
        }
    }
    
    /**
     * Validate and clean a URL string
     */
    fun cleanUrl(url: String): String {
        return url.trim()
            .replace(" ", "%20")
            .replace("[", "%5B")
            .replace("]", "%5D")
            .replace("{", "%7B")
            .replace("}", "%7D")
    }
    
    /**
     * Extract video ID from various YouTube URL formats safely
     */
    fun extractVideoIdFromUrl(url: String): String? {
        return try {
            val patterns = listOf(
                Regex("youtube\\.com/watch\\?v=([^&]+)"),
                Regex("youtu\\.be/([^?]+)"),
                Regex("youtube\\.com/embed/([^?]+)"),
                Regex("youtube\\.com/v/([^?]+)"),
                Regex("youtube\\.com/shorts/([^?]+)")
            )
            
            for (pattern in patterns) {
                val matchResult = pattern.find(url)
                if (matchResult != null) {
                    return matchResult.groupValues[1].takeIf { it.length == 11 }
                }
            }
            
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract video ID from: $url", e)
            null
        }
    }
}