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
import org.schabi.newpipe.extractor.utils.Localization
import java.io.IOException

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
                                            request.headers().forEach { (key, value) ->
                                                add(key, value)
                                            }
                                        }
                                        .build()
                                )
                                .build()
                            
                            val response = client.newCall(okhttpRequest).execute()
                            Response(
                                response.code,
                                response.message,
                                response.headers.toMultimap(),
                                response.body?.byteStream(),
                                response.body?.contentLength() ?: -1
                            )
                        } catch (e: Exception) {
                            throw IOException("Failed to execute request", e)
                        }
                    }
                }
                
                // Initialize NewPipe with our downloader
                NewPipe.init(downloader, Localization("en", "US"))
                
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

    // SearchFeedClient implementation
    override suspend fun loadSearchFeed(query: String): Feed {
        return withContext(Dispatchers.IO) {
            try {
                println("Searching YouTube for: $query")
                val searchExtractor = youtubeService.getSearchExtractor(query)
                searchExtractor.fetchPage()
                
                val items = searchExtractor.initialSearchResult.items.mapNotNull { item ->
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
                
                println("Found ${items.size} tracks for query: $query")
                
                Feed(
                    tabs = listOf("Songs", "Videos", "Playlists"),
                    pagedData = object : PagedData<Shelf> {
                        override suspend fun loadPage(page: String?): PagedData.Page<Shelf> {
                            val shelfItems = items.map { track ->
                                Shelf.Item(track)
                            }
                            return PagedData.Page(shelfItems, null)
                        }
                    }
                )
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
                val detailedTrack = converter.toTrack(streamExtractor)
                
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