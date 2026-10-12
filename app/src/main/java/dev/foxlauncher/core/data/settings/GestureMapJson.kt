package dev.foxlauncher.core.data.settings

import dev.foxlauncher.core.model.DEFAULT_GESTURE_MAP
import dev.foxlauncher.core.model.GestureAction
import dev.foxlauncher.core.model.GestureType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * The gesture map's stored form (task 3.1): one JSON object under one DataStore key holding only
 * the gestures the user changed, e.g. `{"DOUBLE_TAP":{"type":"open_app","component":"…","userSerial":0}}`,
 * so untouched gestures follow [DEFAULT_GESTURE_MAP] as it evolves. Reading forgives what a newer
 * Fox may have written (a downgrade, a restore): an unknown action becomes [GestureAction.None],
 * an unknown gesture is dropped, and unreadable JSON counts as no changes.
 */
object GestureMapJson {

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    /** What every gesture does: the defaults with the stored changes on top. */
    fun decode(stored: String?): Map<GestureType, GestureAction> = DEFAULT_GESTURE_MAP + decodeChanges(stored)

    /** Only the gestures the user changed. */
    fun decodeChanges(stored: String?): Map<GestureType, GestureAction> {
        if (stored.isNullOrBlank()) return emptyMap()
        val entries = parseOrNull(stored) ?: return emptyMap()
        return entries.mapNotNull { (name, value) ->
            val type = GestureType.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
            // SerializationException, thrown for an unknown type or a missing field, is an IllegalArgumentException.
            val action = try {
                json.decodeFromJsonElement(GestureAction.serializer(), value)
            } catch (e: IllegalArgumentException) {
                GestureAction.None
            }
            type to action
        }.toMap()
    }

    fun encodeChanges(changes: Map<GestureType, GestureAction>): String =
        json.encodeToString(
            JsonObject.serializer(),
            JsonObject(changes.entries.associate { (type, action) -> type.name to json.encodeToJsonElement(GestureAction.serializer(), action) }),
        )

    private fun parseOrNull(stored: String): JsonObject? = try {
        json.parseToJsonElement(stored) as? JsonObject
    } catch (e: IllegalArgumentException) {
        null
    }
}
