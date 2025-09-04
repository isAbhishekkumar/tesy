package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.*
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.youtube.YouTubeConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.io.ByteArrayInputStream

class YouTubeMusicExtension : ExtensionClient, SearchFeedClient, TrackClient {

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
                    override fun execute(request: Request): Response {
                        return try {
                            val client = okhttp3.OkHttpClient()
                            val okhttpRequest = okhttp3.Request.Builder()
                                .url(request.url())
                                .headers(
                                    okhttp3.Headers.Builder()
                                        .apply {
                                            // Iterate through headers properly
                                            request.headers().forEach { (key, value) ->
                                                add(key, value)
                                            }
                                        }
                                        .build()
                                )
                                .build()
                            
                            val response = client.newCall(okhttpRequest).execute()
                            val responseBody = response.body?.byteStream()
                            val responseBytes = responseBody?.readBytes()
                            responseBody?.close()
                            
                            Response(
                                response.code,
                                response.message,
                                response.headers.toMultimap(),
                                response.body?.string(),
                                response.body?.contentLength()?.toString()
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
                
                val items = searchExtractor.initialSearchResult.items.mapNotNull { item: org.schabi.newpipe.extractor.InfoItem ->
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
                
                Feed(
                    tabs = listOf(Tab("songs", "Songs"), Tab("videos", "Videos"), Tab("playlists", "Playlists"))
                ) { tab ->
                    // Return Feed.Data based on the selected tab using list extension function
                    when (tab?.id) {
                        "songs" -> shelfItems.toFeedData(
                            buttons = Feed.Buttons(showSearch = true, showSort = true, showPlayAndShuffle = true)
                        )
                        "videos" -> shelfItems.toFeedData(
                            buttons = Feed.Buttons(showSearch = true, showSort = true, showPlayAndShuffle = false)
                        )
                        "playlists" -> shelfItems.toFeedData(
                            buttons = Feed.Buttons(showSearch = true, showSort = true, showPlayAndShuffle = false)
                        )
                        else -> shelfItems.toFeedData() // Default case
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
}