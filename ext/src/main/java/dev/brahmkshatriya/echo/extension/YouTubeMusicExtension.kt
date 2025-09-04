package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.QuickSearchClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.*
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.youtube.YouTubeConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Headers
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NewPipeRequest
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.io.ByteArrayInputStream

class YouTubeMusicExtension : ExtensionClient, QuickSearchClient, TrackClient {

    private lateinit var settings: Settings
    private lateinit var youtubeService: StreamingService
    private lateinit var downloader: Downloader
    private lateinit var converter: YouTubeConverter

    override fun setSettings(settings: Settings) {
        this.settings = settings
    }

    override suspend fun onInitialize() {
        withContext(Dispatchers.IO) {
            try {
                // Initialize NewPipe with custom downloader
                downloader = object : Downloader() {
                    override fun execute(request: NewPipeRequest): Response {
                        return try {
                            val client = OkHttpClient()
                            val okhttpRequest = Request.Builder()
                                .url(request.url())
                                .headers(
                                    Headers.Builder()
                                        .apply {
                                            // Simple header iteration - avoid complex type conversions
                                            try {
                                                val headerNames = request.headers().keySet()
                                                for (name in headerNames) {
                                                    val value = request.headers().get(name)
                                                    if (value != null) {
                                                        // Split comma-separated values
                                                        val values = value.split(",")
                                                        for (v in values) {
                                                            add(name, v.trim())
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                // Fallback: add basic headers
                                                add("User-Agent", "Mozilla/5.0")
                                            }
                                        }
                                        .build()
                                )
                                .build()
                            
                            val response = client.newCall(okhttpRequest).execute()
                            val responseBodyStream = response.body?.byteStream()
                            val responseBytes = responseBodyStream?.readBytes()
                            responseBodyStream?.close()
                            
                            val responseHeaders = try {
                                response.headers.names().associateWith { name ->
                                    response.headers.values(name)
                                }
                            } catch (e: Exception) {
                                emptyMap<String, List<String>>()
                            }
                            val responseBody = response.body
                            val contentLength = responseBody?.contentLength()?.toString() ?: "0"
                                Response(
                                    response.code,
                                    response.message,
                                    responseHeaders,
                                    responseBody?.string(),
                                    contentLength
                                )
                        } catch (e: Exception) {
                            throw IOException("Failed to execute request", e)
                        }
                    }
                }
                
                // Initialize NewPipe with our downloader
                NewPipe.init(downloader)
                
                // Get YouTube service
                youtubeService = ServiceList.YouTube
                
                // Initialize converter
                converter = YouTubeConverter(settings)
                
                println("YouTubeMusicExtension initialized successfully")
            } catch (e: Exception) {
                println("Failed to initialize YouTubeMusicExtension: ${e.message}")
                throw e
            }
        }
    }

    override suspend fun getSettingItems(): List<Setting> {
        return emptyList()
    }

    // Implement missing abstract method
    override suspend fun loadFeed(track: Track): Feed<Shelf>? {
        return null // YouTube Music extension doesn't support track-specific feeds
    }

    // SearchFeedClient implementation
    override suspend fun loadSearchFeed(query: String): Feed<Shelf> {
        return withContext(Dispatchers.IO) {
            try {
                println("Searching YouTube for: $query")
                val searchExtractor = youtubeService.getSearchExtractor(query)
                searchExtractor.fetchPage()
                
                val items = try {
                    searchExtractor.initialPage.items
                } catch (e: Exception) {
                    // Fallback: try alternative method names
                    try {
                        // Try common alternatives
                        searchExtractor.javaClass.methods.firstOrNull { 
                            it.name.contains("page", ignoreCase = true) || it.name.contains("result", ignoreCase = true)
                        }?.let { method ->
                            when (method.name) {
                                "getInitialPage" -> method.invoke(searchExtractor).let { page ->
                                    page.javaClass.getMethod("getItems").invoke(page) as? List<org.schabi.newpipe.extractor.InfoItem>
                                }
                                else -> null
                            }
                        } ?: emptyList()
                    } catch (e2: Exception) {
                        emptyList()
                    }
                }.mapNotNull { item: org.schabi.newpipe.extractor.InfoItem ->
                    when (item) {
                        is StreamInfoItem -> {
                            try {
                                converter.toTrack(item)
                            } catch (e: Exception) {
                                println("Failed to convert StreamInfoItem to Track: ${e.message}")
                                null
                            }
                        }
                        else -> null
                    }
                }
                
                println("Found ${items.count()} tracks for query: $query")
                
                // Create a simple feed with the search results
                val shelfItems = items.map { track: Track ->
                    Shelf.Item(track)
                }
                
                // Use PagedData.Single for a simple list of items
                val pagedData: PagedData<Shelf> = PagedData.Single { shelfItems }
                
                Feed(
                    tabs = listOf(Tab("songs", "Songs"), Tab("videos", "Videos"), Tab("playlists", "Playlists"))
                ) { tab ->
                    // Return Feed.Data based on the selected tab
                    when (tab?.id) {
                        "songs" -> Feed.Data(
                            pagedData = pagedData,
                            buttons = Feed.Buttons(showSearch = true, showSort = true, showPlayAndShuffle = true)
                        )
                        "videos" -> Feed.Data(
                            pagedData = pagedData,
                            buttons = Feed.Buttons(showSearch = true, showSort = true, showPlayAndShuffle = false)
                        )
                        "playlists" -> Feed.Data(
                            pagedData = pagedData,
                            buttons = Feed.Buttons(showSearch = true, showSort = true, showPlayAndShuffle = false)
                        )
                        else -> Feed.Data(pagedData = pagedData) // Default case
                    }
                }
            } catch (e: Exception) {
                println("Failed to search YouTube Music: ${e.message}")
                throw RuntimeException("Failed to search YouTube Music", e)
            }
        }
    }

    // TrackClient implementation
    override suspend fun loadTrack(track: Track, refresh: Boolean): Track {
        return withContext(Dispatchers.IO) {
            try {
                println("Loading track details for: ${track.title}")
                val streamExtractor = youtubeService.getStreamExtractor(track.id)
                streamExtractor.fetchPage()
                
                // Convert NewPipe StreamInfo to Echo Track using converter
                val detailedTrack = converter.toTrack(streamExtractor as org.schabi.newpipe.extractor.stream.StreamInfo)
                
                println("Successfully loaded track: ${detailedTrack.title}")
                detailedTrack
            } catch (e: Exception) {
                println("Failed to load track details: ${e.message}")
                throw RuntimeException("Failed to load track details", e)
            }
        }
    }

    override suspend fun loadStreamableMedia(streamable: Streamable, refresh: Boolean): Streamable.Media {
        return withContext(Dispatchers.IO) {
            try {
                println("Loading streamable media for: ${streamable.title}")
                val media = converter.toStreamableMedia(streamable)
                println("Successfully loaded streamable media")
                media
            } catch (e: Exception) {
                println("Failed to load streamable media: ${e.message}")
                throw RuntimeException("Failed to load streamable media", e)
            }
        }
    }

    // QuickSearchClient implementation
    override suspend fun quickSearch(query: String): List<QuickSearchItem> {
        return withContext(Dispatchers.IO) {
            try {
                println("Quick searching YouTube for: $query")
                if (query.isBlank()) {
                    return@withContext emptyList()
                }
                
                // Get search suggestions from YouTube
                val suggestionExtractor = youtubeService.getSuggestionExtractor()
                val suggestions = try {
                    suggestionExtractor.suggestionList(query)
                } catch (e: Exception) {
                    // Fallback: try alternative method names
                    try {
                        // Try reflection to find available methods
                        suggestionExtractor.javaClass.methods.firstOrNull { 
                            it.name.contains("suggestion", ignoreCase = true) && it.parameterCount == 1 
                        }?.invoke(suggestionExtractor, query) as? List<String> ?: emptyList()
                    } catch (e2: Exception) {
                        emptyList()
                    }
                }
                
                println("Found ${suggestions.size} suggestions for query: $query")
                
                // Convert suggestions to QuickSearchItem.Query with explicit types
                val queryItems: List<QuickSearchItem.Query> = suggestions.map { suggestion: String ->
                    QuickSearchItem.Query(
                        query = suggestion,
                        searched = false,
                        extras = mapOf(
                            "source" to "youtube",
                            "type" to "suggestion"
                        )
                    )
                }
                
                // Add some recent/popular searches as media items if available
                val mediaItems: MutableList<QuickSearchItem.Media> = mutableListOf()
                
                // Try to get a few actual tracks for popular searches
                if (query.length > 2) {
                    try {
                        val searchExtractor = youtubeService.getSearchExtractor(query)
                        searchExtractor.fetchPage()
                        val topResults = try {
                            searchExtractor.initialPage.items
                        } catch (e: Exception) {
                            // Fallback: try alternative method names
                            try {
                                // Try common alternatives
                                searchExtractor.javaClass.methods.firstOrNull { 
                                    it.name.contains("page", ignoreCase = true) || it.name.contains("result", ignoreCase = true)
                                }?.let { method ->
                                    when (method.name) {
                                        "getInitialPage" -> method.invoke(searchExtractor).let { page ->
                                            page.javaClass.getMethod("getItems").invoke(page) as? List<org.schabi.newpipe.extractor.InfoItem>
                                        }
                                        else -> null
                                    }
                                } ?: emptyList()
                            } catch (e2: Exception) {
                                emptyList()
                            }
                        }
                            .take(3) // Limit to top 3 results
                            .filterIsInstance<StreamInfoItem>()
                            .mapNotNull { streamItem: StreamInfoItem ->
                                try {
                                    val track = converter.toTrack(streamItem)
                                    QuickSearchItem.Media(
                                        media = track,
                                        searched = false
                                    )
                                } catch (e: Exception) {
                                    println("Failed to convert stream item to track: ${e.message}")
                                    null
                                }
                            }
                        mediaItems.addAll(topResults)
                    } catch (e: Exception) {
                        println("Failed to get media items for quick search: ${e.message}")
                    }
                }
                
                // Combine queries and media items, prioritizing queries
                val result: List<QuickSearchItem> = queryItems + mediaItems
                result
                
            } catch (e: Exception) {
                println("Failed to quick search YouTube: ${e.message}")
                // Return basic query item as fallback
                listOf(
                    QuickSearchItem.Query(
                        query = query,
                        searched = false,
                        extras = mapOf("source" to "youtube", "type" to "fallback")
                    )
                )
            }
        }
    }

    override suspend fun deleteQuickSearch(item: QuickSearchItem) {
        // YouTube Music extension doesn't store quick search history
        // This is a no-op implementation
        println("Deleting quick search item: ${item.title}")
    }
}