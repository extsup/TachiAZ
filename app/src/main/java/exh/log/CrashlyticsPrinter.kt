package exh.log

import com.elvishew.xlog.printer.Printer
import eu.kanade.tachiyomi.BuildConfig

class CrashlyticsPrinter(private val logLevel: Int) : Printer {
    override fun println(
        logLevel: Int,
        tag: String?,
        msg: String?
    ) {
        // Crashlytics removed
    }
}