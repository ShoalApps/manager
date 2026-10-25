package io.github.swordfishapps.manager.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import org.json.JSONObject

/**
 * Hands your accounts to the Swordfish apps: content://<manager>.accounts/all returns one row of
 * JSON. Protected by a signature permission, so only apps signed with the same key can read it.
 */
class AccountsProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        Accounts.init(context!!)
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val s = Accounts.saved.value
        val out = JSONObject()
            .put("version", s.version)
            .put("cookies", JSONObject(s.cookies))
            .put("youtubePageId", s.youtubePageId ?: JSONObject.NULL)
            .put("blueskyHandle", s.blueskyHandle ?: JSONObject.NULL)
            .put("blueskyAppPassword", s.blueskyAppPassword ?: JSONObject.NULL)
        return MatrixCursor(arrayOf("json")).apply { addRow(arrayOf(out.toString())) }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}
