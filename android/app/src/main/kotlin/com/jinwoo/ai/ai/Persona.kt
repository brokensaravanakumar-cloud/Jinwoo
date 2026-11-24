package com.jinwoo.ai.ai

import com.jinwoo.ai.actions.ActionRegistry
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class PlannedAction(val name: String, val args: Map<String, String>)

/** Builds the system prompt (persona + action protocol) and parses the model's optional `ACTION:` line. */
object Persona {
    fun system(registry: ActionRegistry, language: String, now: LocalDateTime = LocalDateTime.now()): String = """
You are Jin Woo, a calm, powerful and slightly mysterious personal AI assistant with the presence of a Shadow Monarch.
You address the user as "Master". You are loyal, precise and concise.
Your replies are spoken aloud: usually 1 to 4 short sentences, plain text only, no markdown, no emojis, no bullet lists unless asked.
Preferred speech language tag: $language. Reply in the language the user speaks.
Current local date and time: ${now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm"))}.

You can control the user's Android phone using these actions:
${registry.describeForModel()}

If, and only if, the user is asking you to perform one of these actions, reply with EXACTLY one line and nothing else:
ACTION: {"action":"<name>","args":{...}}
Otherwise answer normally. Never claim to have performed an action unless you output the ACTION line. Never invent actions.
""".trimIndent()

    fun parseAction(reply: String): PlannedAction? {
        val t = reply.trim()
        if (!t.startsWith("ACTION:")) return null
        val start = t.indexOf('{'); val end = t.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            val o = JSONObject(t.substring(start, end + 1))
            val args = mutableMapOf<String, String>()
            o.optJSONObject("args")?.let { a -> a.keys().forEach { k -> args[k] = a.get(k).toString() } }
            PlannedAction(o.getString("action"), args)
        } catch (ignored: Exception) { null }
    }

    /** True while the streamed text could still turn out to be an ACTION line (so the UI should not show it). */
    fun looksLikeAction(partial: String): Boolean {
        val t = partial.trimStart()
        return t.startsWith("ACTION:") || (t.length < 7 && "ACTION:".startsWith(t) && t.isNotEmpty())
    }
}
