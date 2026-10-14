package dev.foxlauncher.core.data.settings

import dev.foxlauncher.core.model.DEFAULT_GESTURE_MAP
import dev.foxlauncher.core.model.GestureAction
import dev.foxlauncher.core.model.GestureType
import dev.foxlauncher.core.model.Macros
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The gesture map's stored form (task 3.1): one JSON object under one DataStore key holding only
 * the gestures the user changed, e.g. `{"DOUBLE_TAP":{"type":"open_app","component":"…","userSerial":0}}`,
 * so untouched gestures follow [DEFAULT_GESTURE_MAP] as it evolves. Reading forgives what a newer
 * Fox may have written (a downgrade, a restore): an unknown action becomes [GestureAction.None],
 * an unknown gesture is dropped, and unreadable JSON counts as no changes.
 *
 * Per-page overrides (task 9.3) live under their own key in the same shape, one object per page
 * index: `{"1":{"DOUBLE_TAP":{"type":"next_wallpaper"}}}`.
 */
object GestureMapJson {

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    /** What every gesture does: the defaults with the stored changes on top. */
    fun decode(stored: String?): Map<GestureType, GestureAction> = DEFAULT_GESTURE_MAP + decodeChanges(stored)

    /** Only the gestures the user changed. */
    fun decodeChanges(stored: String?): Map<GestureType, GestureAction> =
        stored?.takeIf { it.isNotBlank() }?.let(::parseOrNull)?.let(::changesOf).orEmpty()

    fun encodeChanges(changes: Map<GestureType, GestureAction>): String =
        json.encodeToString(JsonObject.serializer(), objectOf(changes))

    /** Each page's own gestures, by page index; pages without any are left out. */
    fun decodePages(stored: String?): Map<Int, Map<GestureType, GestureAction>> {
        val pages = stored?.takeIf { it.isNotBlank() }?.let(::parseOrNull) ?: return emptyMap()
        return pages.mapNotNull { (key, value) ->
            val page = key.toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
            val changes = (value as? JsonObject)?.let(::changesOf).orEmpty()
            if (changes.isEmpty()) null else page to changes
        }.toMap()
    }

    fun encodePages(pages: Map<Int, Map<GestureType, GestureAction>>): String =
        json.encodeToString(
            JsonObject.serializer(),
            JsonObject(pages.filterValues { it.isNotEmpty() }.entries.associate { (page, changes) -> page.toString() to objectOf(changes) }),
        )

    private fun changesOf(entries: JsonObject): Map<GestureType, GestureAction> = entries.mapNotNull { (name, value) ->
        val type = GestureType.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
        type to actionOf(value)
    }.toMap()

    private fun actionOf(value: JsonElement): GestureAction {
        // SerializationException, thrown for an unknown type or a missing field, is an IllegalArgumentException.
        val action = try {
            json.decodeFromJsonElement(GestureAction.serializer(), value)
        } catch (e: IllegalArgumentException) {
            GestureAction.None
        }
        return if (action is GestureAction.Macro) Macros.sanitize(action) else action
    }

    private fun objectOf(changes: Map<GestureType, GestureAction>): JsonObject =
        JsonObject(changes.entries.associate { (type, action) -> type.name to json.encodeToJsonElement(GestureAction.serializer(), action) })

    private fun parseOrNull(stored: String): JsonObject? = try {
        json.parseToJsonElement(stored) as? JsonObject
    } catch (e: IllegalArgumentException) {
        null
    }
}
