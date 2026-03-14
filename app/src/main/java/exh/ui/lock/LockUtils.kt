package exh.ui.lock

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.security.MessageDigest
import kotlin.experimental.and

/**
 * Password hashing utils
 */

/**
 * Yes, I know SHA512 is fast, but bcrypt on mobile devices is too slow apparently
 */
fun sha512(
    passwordToHash: String,
    salt: String
): String {
    val md = MessageDigest.getInstance("SHA-512")
    md.update(salt.toByteArray(charset("UTF-8")))
    val bytes = md.digest(passwordToHash.toByteArray(charset("UTF-8")))
    val sb = StringBuilder()
    for (i in bytes.indices) {
        sb.append(((bytes[i] and 0xff.toByte()) + 0x100).toString(16).substring(1))
    }
    return sb.toString()
}

/**
 * Check if lock is enabled
 */
fun lockEnabled(prefs: PreferencesHelper = Injekt.get()) = prefs.eh_lockLength().get() != -1
