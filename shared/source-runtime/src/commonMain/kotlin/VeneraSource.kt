package app.koyura.source.runtime

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class VeneraEnvironment(
    val compatibleAppVersion: String,
    val locale: String,
    val platform: String,
)

data class VeneraSource(
    val name: String,
    val key: String,
    val version: String,
    val minAppVersion: String?,
    val url: String?,
    val callbacks: Set<VeneraCallback>,
)

enum class VeneraCallback(val path: String, internal val synchronous: Boolean = false) {
    Search("search.load"),
    SearchNext("search.loadNext"),
    LoadInfo("comic.loadInfo"),
    LoadEpisode("comic.loadEp"),
    LoadThumbnails("comic.loadThumbnails"),
    LoadImage("comic.onImageLoad"),
    LoadThumbnail("comic.onThumbnailLoad"),
    ClickTag("comic.onClickTag", synchronous = true),
    Login("account.login"),
    Logout("account.logout"),
    ValidateCookies("account.loginWithCookies.validate"),
    CheckWebLogin("account.loginWithWebview.checkStatus", synchronous = true),
    WebLoginSucceeded("account.loginWithWebview.onLoginSuccess"),
    CategoryComics("categoryComics.load"),
    CategoryOptions("categoryComics.optionLoader"),
    RankingPage("categoryComics.ranking.load"),
    RankingNext("categoryComics.ranking.loadWithNext"),
    SearchTagSuggestion("search.onTagSuggestionSelected", synchronous = true),
    FavoritePage("favorites.loadComics"),
    FavoriteNext("favorites.loadNext"),
    FavoriteFolders("favorites.loadFolders"),
    ChangeFavorite("favorites.addOrDelFavorite"),
    AddFavoriteFolder("favorites.addFolder"),
    DeleteFavoriteFolder("favorites.deleteFolder"),
    Comments("comic.loadComments"),
    SendComment("comic.sendComment"),
    ChapterComments("comic.loadChapterComments"),
    SendChapterComment("comic.sendChapterComment"),
    LikeComic("comic.likeComic"),
    LikeComment("comic.likeComment"),
    VoteComment("comic.voteComment"),
    StarRating("comic.starRating"),
    ComicLink("comic.link.linkToId", synchronous = true),
    Archives("comic.archive.getArchives"),
    ArchiveDownload("comic.archive.getDownloadUrl"),
}

@Serializable
data class VeneraHttpRequest(
    val sourceKey: String?,
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val data: JsonElement? = null,
    val extra: JsonObject? = null,
    val binaryData: String? = null,
)

@Serializable
data class VeneraHttpResponse(
    val status: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
)

fun interface VeneraHttpTransport {
    suspend fun execute(request: VeneraHttpRequest): VeneraHttpResponse

    suspend fun executeBytes(request: VeneraHttpRequest): VeneraBytesResponse {
        val text = execute(request)
        return VeneraBytesResponse(text.status, text.body.encodeToByteArray(), text.headers)
    }
}

/**
 * Binary counterpart of [VeneraHttpResponse]: status, raw bytes, headers.
 * Used for image payloads where text decoding would corrupt the bytes.
 */
data class VeneraBytesResponse(
    val status: Int,
    val body: ByteArray,
    val headers: Map<String, String> = emptyMap(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VeneraBytesResponse) return false
        return status == other.status && body.contentEquals(other.body) && headers == other.headers
    }

    override fun hashCode(): Int {
        var result = status
        result = 31 * result + body.contentHashCode()
        result = 31 * result + headers.hashCode()
        return result
    }
}

class VeneraScriptException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

enum class SourceIndexedCallback(internal val section: String, internal val member: String, internal val synchronous: Boolean = false) {
    ExplorePage("explore", "load"),
    ExploreNext("explore", "loadNext"),
    CategoryPart("category.parts", "loader", synchronous = true),
}

enum class SourceConfigField(internal val path: String) {
    Explore("explore"),
    Category("category"),
    CategoryOptions("categoryComics.optionList"),
    RankingOptions("categoryComics.ranking.options"),
    SearchOptions("search.optionList"),
    SearchTagSuggestions("search.enableTagsSuggestions"),
    Settings("settings"),
    AccountRegistration("account.registerWebsite"),
    AccountCookieFields("account.loginWithCookies.fields"),
    AccountLoggedIn("isLogged"),
    FavoriteMultiFolder("favorites.multiFolder"),
    FavoriteOldToNewSort("favorites.isOldToNewSort"),
    FavoriteSingleFolderForComic("favorites.singleFolderForSingleComic"),
}
