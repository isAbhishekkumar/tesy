package dev.brahmkshatriya.echo.extension.youtube

import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NewPipeRequest
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Custom HTTP downloader implementation for NewPipe based on SimpMusic approach
 * Handles cookie management, proxy support, and proper error handling
 */
class YouTubeDownloaderImpl(
    private val cookie: String? = null,
    proxy: Proxy? = null,
) : Downloader() {
    
    private val client: OkHttpClient = OkHttpClient.Builder()
        .proxy(proxy)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: NewPipeRequest): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()
        
        val requestBuilder = OkHttpRequest.Builder()
            .method(httpMethod, dataToSend?.toRequestBody())
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .addHeader("Accept-Language", "en-US,en;q=0.5")
            .addHeader("Accept-Encoding", "gzip, deflate, br")
            .addHeader("Connection", "keep-alive")
            .addHeader("Upgrade-Insecure-Requests", "1")
            .addHeader("Sec-Fetch-Dest", "document")
            .addHeader("Sec-Fetch-Mode", "navigate")
            .addHeader("Sec-Fetch-Site", "none")
            .addHeader("Sec-Fetch-User", "?1")
            .addHeader("Cache-Control", "max-age=0")
        
        // Proper header handling based on SimpMusic implementation
        headers.forEach { (headerName, headerValueList) ->
            if (headerValueList.size > 1) {
                requestBuilder.removeHeader(headerName)
                headerValueList.forEach { headerValue ->
                    requestBuilder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                requestBuilder.header(headerName, headerValueList[0])
            }
        }
        
        // Add cookie if provided
        cookie?.let {
            requestBuilder.addHeader("Cookie", it)
        }
        
        val response = client.newCall(requestBuilder.build()).execute()
        
        // Handle reCAPTCHA challenge
        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }
        
        val responseBodyToReturn = response.body?.string()
        val latestUrl = response.request.url.toString()
        
        // Use toMultimap() - this method exists in NewPipe!
        return Response(response.code, response.message, response.headers.toMultimap(), responseBodyToReturn, latestUrl)
    }
}