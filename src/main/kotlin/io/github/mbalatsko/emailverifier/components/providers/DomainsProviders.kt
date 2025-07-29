package io.github.mbalatsko.emailverifier.components.providers

import io.github.mbalatsko.emailverifier.components.core.ConnectionError
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import org.slf4j.LoggerFactory
import java.io.File
import java.net.IDN

/**
 * Interface for providing a set of domain names.
 */
interface DomainsProvider {
    /**
     * Retrieves a list of domain names.
     *
     * @return a set of ASCII-compatible domain names.
     */
    suspend fun provide(): Set<String>
}

/**
 * Abstract implementation of [DomainsProvider] for linefeed-separated domain sources.
 * Filters out empty lines and comment lines starting with "//".
 * Converts domain names to ASCII using [IDN.toASCII].
 */
abstract class LFDomainsProvider : DomainsProvider {
    /**
     * Fetches raw textual data from the source.
     *
     * @return raw domain data as a single string.
     */
    abstract suspend fun obtainData(): String

    override suspend fun provide(): Set<String> {
        val lines =
            obtainData()
                .lineSequence()
                .filter { it.isNotEmpty() && !it.startsWith("//") }
                .map { IDN.toASCII(it.trim().lowercase()) }
                .toSet()
        logger.debug("Parsed {} valid lines from fetched data.", lines.size)
        return lines
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LFDomainsProvider::class.java)
    }
}

/**
 * [LFDomainsProvider] implementation that retrieves domain data from a remote URL.
 *
 * @property url the HTTP(S) address of the linefeed-separated domain list.
 * @property httpClient HTTP client used to download linefeed-separated domain list from [url]
 */
class OnlineLFDomainsProvider(
    private val url: String,
    private val httpClient: HttpClient,
) : LFDomainsProvider() {
    /**
     * Performs an HTTP GET request to fetch the domain list.
     *
     * @return the response body as raw text.
     */
    override suspend fun obtainData(): String {
        logger.debug("Fetching domains from URL: {}", url)
        val response = httpClient.get(url)
        if (response.status.value >= 400) {
            throw ConnectionError("Failed to fetch domains from $url: HTTP status ${response.status}")
        } else {
            val data = response.bodyAsText()
            logger.debug("Successfully fetched {} bytes from {}", data.length, url)
            return data
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OnlineLFDomainsProvider::class.java)
    }
}

/**
 * [LFDomainsProvider] implementation that retrieves domain data from a local resource file.
 *
 * @property resourcesFilePath the path to the linefeed-separated domain list within the resources.
 */
class ResourceFileLFDomainsProvider(
    private val resourcesFilePath: String,
) : LFDomainsProvider() {
    private val resourceUrl = this.javaClass.getResource(resourcesFilePath)

    init {
        require(resourceUrl != null) { "$resourcesFilePath resource does not exist" }
    }

    /**
     * Reads the content of the local resource file.
     *
     * @return the content of the resource file as raw text.
     */
    override suspend fun obtainData(): String {
        logger.debug("Loading domains from resource file: {}", resourcesFilePath)
        val data = resourceUrl!!.readText()
        logger.debug("Successfully loaded {} bytes from {}", data.length, resourcesFilePath)
        return data
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ResourceFileLFDomainsProvider::class.java)
    }
}

/**
 * [LFDomainsProvider] implementation that retrieves domain data from a file on the local filesystem.
 *
 * @param filePath the path to the linefeed-separated domain list on the filesystem.
 */
class FileLFDomainsProvider(
    filePath: String,
) : LFDomainsProvider() {
    private val file = File(filePath)

    init {
        require(file.exists()) { "$filePath does not exist" }
        require(file.isFile) { "$filePath must be normal file" }
    }

    override suspend fun obtainData(): String {
        logger.debug("Loading domains from file: {}", file.path)
        val data = file.readText()
        logger.debug("Successfully loaded {} bytes from {}", data.length, file.path)
        return data
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FileLFDomainsProvider::class.java)
    }
}
