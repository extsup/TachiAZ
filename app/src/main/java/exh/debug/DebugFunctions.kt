package exh.debug

import android.app.Application
import com.pushtorefresh.storio.sqlite.queries.RawQuery
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.tables.MangaTable
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.system.jobScheduler
import exh.util.await
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

object DebugFunctions {
    val app: Application by injectLazy()
    val db: DatabaseHelper by injectLazy()
    val prefs: PreferencesHelper by injectLazy()
    val sourceManager: SourceManager by injectLazy()
    val DownloadManager: DownloadManager by injectLazy()

    fun crash() {
        throw CrashButtonException()
    }

    fun forceLibraryUpdateJobSetup() {
        LibraryUpdateJob.setupTask(Injekt.get<Application>())
    }

    fun addAllMangaInDatabaseToLibrary() {
        db.inTransaction {
            db.lowLevel().executeSQL(
                RawQuery.builder()
                    .query(
                        """
                        UPDATE ${MangaTable.TABLE}
                            SET ${MangaTable.COL_FAVORITE} = 1
                        """.trimIndent()
                    )
                    .affectsTables(MangaTable.TABLE)
                    .build()
            )
        }
    }

    fun getStatisticsInfo(): StatisticsInfoClass {
        val statisticsObject = StatisticsInfoClass()
        runBlocking {
            val libraryManga = db.getLibraryMangas().await()
            val databaseManga = db.getMangas().await()
            val databaseTracks = db.getAllTracks().await()
            val databaseChapters = db.getAllChapters().await()

            val databaseMangaMap = databaseManga.associateBy { it.id }

            statisticsObject.apply {
                mangaCount = libraryManga.count()
                completedMangaCount = libraryManga.count { it.status == SManga.COMPLETED && it.unread == 0 }
                startedMangaCount = databaseChapters.distinctBy { it.manga_id }.count { databaseMangaMap[it.manga_id]?.favorite ?: false }
                localMangaCount = databaseManga.count { it.source == LocalSource.ID }
                totalChapterCount = databaseChapters.count { databaseMangaMap[it.manga_id]?.favorite ?: false }
                readChapterCount = databaseChapters.count { it.read }
                downloadedChapterCount = DownloadManager.getDownloadCount()
                trackedMangaCount = databaseTracks.distinctBy { it.manga_id }.count()
                meanMangaScore = if (trackedMangaCount == 0) { Double.NaN } else { databaseTracks.map { it.score }.filter { it > 0 }.average() }
            }
        }
        return statisticsObject
    }

    fun countMangaInDatabaseInLibrary() = db.getMangas().executeAsBlocking().count { it.favorite }

    fun countMangaInDatabaseNotInLibrary() = db.getMangas().executeAsBlocking().count { !it.favorite }

    fun countMangaInDatabase() = db.getMangas().executeAsBlocking().size

    fun countMetadataInDatabase() = db.getSearchMetadata().executeAsBlocking().size

    fun countMangaInLibraryWithMissingMetadata() =
        db.getMangas().executeAsBlocking().count {
            it.favorite && db.getSearchMetadataForManga(it.id!!).executeAsBlocking() == null
        }

    fun clearSavedSearches() = prefs.eh_savedSearches().set(emptySet())

    fun listAllSources() =
        sourceManager.getCatalogueSources().joinToString("\n") {
            "${it.id}: ${it.name} (${it.lang.uppercase()})"
        }

    fun listFilteredSources() =
        sourceManager.getVisibleCatalogueSources().joinToString("\n") {
            "${it.id}: ${it.name} (${it.lang.uppercase()})"
        }

    fun listAllHttpSources() =
        sourceManager.getOnlineSources().joinToString("\n") {
            "${it.id}: ${it.name} (${it.lang.uppercase()})"
        }

    fun listFilteredHttpSources() =
        sourceManager.getVisibleOnlineSources().joinToString("\n") {
            "${it.id}: ${it.name} (${it.lang.uppercase()})"
        }

    fun listScheduledJobs() =
        app.jobScheduler.allPendingJobs.map { j ->
            """
            {
                info: ${j.id},
                isPeriod: ${j.isPeriodic},
                isPersisted: ${j.isPersisted},
                intervalMillis: ${j.intervalMillis},
            }
            """.trimIndent()
        }.joinToString(",\n")

    fun cancelAllScheduledJobs() = app.jobScheduler.cancelAll()

    class CrashButtonException() : RuntimeException("Crash Button Pressed!")
}
