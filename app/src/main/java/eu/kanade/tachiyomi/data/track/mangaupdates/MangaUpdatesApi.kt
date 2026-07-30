package eu.kanade.tachiyomi.data.track.mangaupdates

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import rx.Observable

class MangaUpdatesApi(private val client: OkHttpClient, private val tracker: MangaUpdates) {

    companion object {
        const val BASE_URL = "https://api.mangaupdates.com/v1"
    }

    private fun authHeaders() = okhttp3.Headers.Builder()
        .add("Authorization", "Bearer ${tracker.getToken()}")
        .add("Content-Type", "application/json")
        .build()

    fun login(username: String, password: String): Observable<String> {
        return Observable.fromCallable {
            val body = JSONObject().apply {
                put("username", username)
                put("password", password)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$BASE_URL/account/login")
                .put(body)
                .build()

            val response = client.newCall(request).execute()
            val json = JSONObject(response.body!!.string())
            json.getJSONObject("context").getString("session_token")
        }
    }

    fun search(query: String): Observable<List<TrackSearch>> {
        return Observable.fromCallable {
            val body = JSONObject().apply {
                put("search", query)
                put("perpage", 10)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$BASE_URL/series/search")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val json = JSONObject(response.body!!.string())
            val results = json.getJSONArray("results")
            val list = mutableListOf<TrackSearch>()

            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i).getJSONObject("record")
                val track = TrackSearch.create(tracker.id).apply {
                    media_id = item.getInt("series_id")
                    title = item.getString("title")
                    cover_url = item.optJSONObject("image")
                        ?.optJSONObject("url")
                        ?.optString("original") ?: ""
                    summary = item.optString("description", "")
                    tracking_url = "https://www.mangaupdates.com/series/${item.getInt("series_id")}"
                    total_chapters = 0
                }
                list.add(track)
            }
            list as List<TrackSearch>
        }
    }

    fun findLibManga(track: Track): Observable<Track?> {
        return Observable.fromCallable<Track?> {
            val request = Request.Builder()
                .url("$BASE_URL/lists/series/${track.media_id}")
                .headers(authHeaders())
                .get()
                .build()

            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@fromCallable null
                val json = JSONObject(response.body!!.string())
                track.status = muStatusToTracker(json.optString("list_type", ""))
                track.last_chapter_read = json.optInt("chapter", 0)
                track
            } catch (e: Exception) {
                null
            }
        }
    }

    fun addLibManga(track: Track): Observable<Track> {
        return Observable.fromCallable {
            val body = JSONObject().apply {
                put("series", JSONObject().put("id", track.media_id))
                put("list_id", trackerStatusToListId(track.status))
                put("list_type", trackerStatusToMu(track.status))
                put("status", JSONObject().put("chapter", track.last_chapter_read))
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$BASE_URL/lists/series")
                .headers(authHeaders())
                .post(body)
                .build()

            client.newCall(request).execute()
            track
        }
    }

    fun updateLibManga(track: Track): Observable<Track> {
        return Observable.fromCallable {
            val body = JSONObject().apply {
                put("list_id", trackerStatusToListId(track.status))
                put("list_type", trackerStatusToMu(track.status))
                put("status", JSONObject().put("chapter", track.last_chapter_read))
                if (track.score > 0) put("rating", track.score.toInt())
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$BASE_URL/lists/series/${track.media_id}")
                .headers(authHeaders())
                .post(body)
                .build()

            client.newCall(request).execute()
            track
        }
    }

    private fun trackerStatusToListId(status: Int) = when (status) {
        MangaUpdates.READING -> 0
        MangaUpdates.PLAN_TO_READ -> 1
        MangaUpdates.COMPLETED -> 2
        MangaUpdates.DROPPED -> 3
        MangaUpdates.UNFINISHED -> 3
        MangaUpdates.ON_HOLD -> 4
        else -> 0
    }

    private fun trackerStatusToMu(status: Int) = when (status) {
        MangaUpdates.READING -> "read"
        MangaUpdates.COMPLETED -> "complete"
        MangaUpdates.ON_HOLD -> "hold"
        MangaUpdates.DROPPED -> "unfinished"
        MangaUpdates.PLAN_TO_READ -> "wish"
        MangaUpdates.UNFINISHED -> "unfinished"
        else -> "read"
    }

    private fun muStatusToTracker(status: String) = when (status) {
        "read" -> MangaUpdates.READING
        "complete" -> MangaUpdates.COMPLETED
        "hold" -> MangaUpdates.ON_HOLD
        "unfinished" -> MangaUpdates.UNFINISHED
        "wish" -> MangaUpdates.PLAN_TO_READ
        else -> MangaUpdates.READING
    }
}
