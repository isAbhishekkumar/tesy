package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.settings.Settings

/**
 * Test extension class for unit testing
 * This class is used by the ExtensionUnitTest to test the extension functionality
 */
class TestExtension : ExtensionClient {
    
    private lateinit var settings: Settings
    
    override fun setSettings(settings: Settings) {
        this.settings = settings
    }
    
    override suspend fun onInitialize() {
        // Initialize the test extension
        println("TestExtension initialized")
    }
    
    override suspend fun getSettingItems(): List<dev.brahmkshatriya.echo.common.settings.Setting> {
        return emptyList()
    }
}