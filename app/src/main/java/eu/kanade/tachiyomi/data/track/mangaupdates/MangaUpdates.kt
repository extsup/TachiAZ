package eu.kanade.tachiyomi.data.track.mangaupdates

import android.content.Context
import android.graphics.Color
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import rx.Completable
import rx.Observable

class MangaUpdates(private val context: Context, id: Int) : TrackService(id) {

    companion object {
        const val READING = 1
        const val COMPLETED = 2
        const val ON_HOLD = 3
        const val DROPPED = 4
        const val PLAN_TO_READ = 5
        const val UNFINISHED = 6

        const val DEFAULT_STATUS = READING
        const val DEFAULT_SCORE = 0
    }

    private val api by lazy { MangaUpdatesApi(client, this) }

    override val name = "MangaUpdates"

    override fun getLogo() = R.drawable.ic_tracker_mu

    override fun getLogoColor() = Color.rgb(116, 133, 156)

    override fun getStatusList() = listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ, UNFINISHED)

    override fun getStatus(status: Int): String = with(context) {
        when (status) {
            READING -> getString(R.string.reading)
            COMPLETED -> getString(R.string.completed)
            ON_HOLD -> getString(R.string.on_hold)
            DROPPED -> getString(R.string.dropped)
            PLAN_TO_READ -> getString(R.string.plan_to_read)
            UNFINISHED -> "Unfinished"
            else -> ""
        }
    }

    override fun getCompletionStatus() = COMPLETED

    override fun getScoreList() = IntRange(0, 10).map(Int::toString)

    override fun displayScore(track: Track) = track.score.toInt().toString()

    override fun add(track: Track): Observable<Track> = api.addLibManga(track)

    override fun update(track: Track): Observable<Track> = api.updateLibManga(track)

    override fun bind(track: Track): Observable<Track> {
        return api.findLibManga(track).flatMap { remoteTrack ->
            if (remoteTrack != null) {
                track.copyPersonalFrom(remoteTrack)
                update(track)
            } else {
                track.score = DEFAULT_SCORE.toFloat()
                track.status = DEFAULT_STATUS
                add(track)
            }
        }
    }

    override fun search(query: String): Observable<List<TrackSearch>> = api.search(query)

    override fun refresh(track: Track): Observable<Track> {
        return api.findLibManga(track).map { remoteTrack ->
            if (remoteTrack != null) {
                track.copyPersonalFrom(remoteTrack)
                track.total_chapters = remoteTrack.total_chapters
            }
            track
        }
    }

    override fun login(username: String, password: String): Completable {
        return api.login(username, password)
            .doOnNext { token ->
                saveCredentials(username, password)
                preferences.trackToken(this).set(token)
            }
            .doOnError { logout() }
            .toCompletable()
    }

    override fun logout() {
        super.logout()
        preferences.trackToken(this).delete()
    }

    fun getToken(): String = preferences.trackToken(this).get() ?: ""
}
