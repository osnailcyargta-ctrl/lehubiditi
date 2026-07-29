package com.blockforge.engine.model

import kotlinx.serialization.json.Json

/** Single source of truth for how `game.json` is written and read, editor and runtime alike. */
object ProjectIO {

    val json: Json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    /** Compact form for the exported APK — no reason to ship whitespace to a device. */
    private val compact: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    fun encode(project: GameProject, pretty: Boolean = true): String =
        (if (pretty) json else compact).encodeToString(GameProject.serializer(), project)

    fun decode(text: String): GameProject =
        json.decodeFromString(GameProject.serializer(), text)

    fun decodeOrNull(text: String): GameProject? = runCatching { decode(text) }.getOrNull()
}
