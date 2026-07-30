package eu.kanade.tachiyomi.data.track.mangaupdates

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.rx2.rxObservable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
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
        return rxObservable(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("username", username)
                put("password", password)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$BASE_URL/account/login")
                .post(body)
                .build()

            val response = client.newCall(request).await()
            val json = JSONObject(response.body!!.string())
            val token = json.getJSONObject("context").getString("session_token")
            send(token)
        }
    }

    fun search(query: String): Observable<List<TrackSearch>> {
        return rxObservable(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("search", query)
                put("perpage", 10)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$BASE_URL/series/search")
                .post(body)
                .build()

            val response = client.newCall(request).await()
            val json = JSONObject(response.body!!.string())
            val results = json.getJSONArray("results")
            val list = mutableListOf<TrackSearch>()

            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i).getJSONObject("record")
                val track = TrackSearch.create(tracker.id).apply {
                    media_id = item.getLong("series_id")
                    title = item.getString("title")
                    cover_url = item.optJSONObject("image")
                        ?.optJSONObject("url")
                        ?.optString("original") ?: ""
                    summary = item.optString("description", "")
                    tracking_url = "https://www.mangaupdates.com/series/${item.getLong("series_id")}"
                    total_chapters = 0
                }
                list.add(track)
            }
            send(list)
        }
    }

    fun findLibManga(track: Track): Observable<Track?> {
        return rxObservable(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$BASE_URL/lists/series/${track.media_id}")
                .headers(authHeaders())
                .get()
                .build()

            try {
                val response = client.newCall(request).await()
                if (!response.isSuccessful) { send(null); return@rxObservable }
                val json = JSONObject(response.body!!.string())
                track.status = muStatusToTracker(json.optString("list_type", ""))
                track.last_chapter_read = json.optInt("chapter", 0)
                send(track)
            } catch (e: Exception) {
                send(null)
            }
        }
    }

    fun addLibManga(track: Track): Observable<Track> {
        return rxObservable(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("series", JSONObject().put("id", track.media_id))
                put("list_type", trackerStatusToMu(track.status))
                put("chapter", track.last_chapter_read)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$BASE_URL/lists/series")
                .headers(authHeaders())
                .post(body)
                .build()

            client.newCall(request).await()
            send(track)
        }
    }

    fun updateLibManga(track: Track): Observable<Track> {
        return rxObservable(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("list_type", trackerStatusToMu(track.status))
                put("chapter", track.last_chapter_read)
                put("rating", if (track.score > 0) track.score.toInt() else JSONObject.NULL)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$BASE_URL/lists/series/${track.media_id}")
                .headers(authHeaders())
                .post(body)
                .build()

            client.newCall(request).await()
            send(track)
        }
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
