package com.jinwoo.ai.actions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ActionResult(val speech: String, val endSession: Boolean = false)

/**
 * One device ability. To add a new ability: implement this interface and add it to [ActionRegistry.default].
 * It is automatically (a) matched against spoken commands via [parse] and (b) offered to the AI model through [description].
 */
interface AssistantAction {
    val name: String
    /** One line shown to the AI model: what it does and which args it takes. */
    val description: String
    /** Sensitive actions (calls, messages, ...) always require spoken or button confirmation first. */
    val sensitive: Boolean get() = false
    /** Returns args if this action matches the raw spoken/typed text, else null. Must be side-effect free. */
    fun parse(text: String): Map<String, String>?
    fun confirmationPrompt(args: Map<String, String>): String = "Shall I proceed, Master? Say yes to confirm."
    suspend fun execute(ctx: Context, args: Map<String, String>): ActionResult
}

internal object Txt {
    private val lead = Regex("^(?:hey |ok |okay )?(?:jin woo[, ]+)?(?:please )?(?:can you |could you |would you |will you )?(?:please )?")
    /** Lower-cased, punctuation-light text with polite prefixes removed. Keeps digits, ':' and '.'. */
    fun norm(raw: String): String {
        var s = raw.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9:.@+ ]"), " ").replace(Regex("\\s+"), " ").trim()
        s = lead.replace(s, "").trim()
        return s.removeSuffix(" please").trim()
    }
    fun launch(ctx: Context, intent: Intent) { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    fun view(ctx: Context, url: String) = launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}

class ActionRegistry(val actions: List<AssistantAction>) {
    fun match(raw: String): Pair<AssistantAction, Map<String, String>>? {
        for (a in actions) a.parse(raw)?.let { return a to it }
        return null
    }
    fun byName(name: String): AssistantAction? = actions.firstOrNull { it.name == name }
    fun describeForModel(): String = actions.joinToString("\n") { "- ${it.name}: ${it.description}" }

    companion object {
        // Order matters: more specific parsers first, generic "open <app>" last.
        fun default() = ActionRegistry(
            listOf(
                EndConversationAction(), TimeDateAction(), CalculateAction(), SetTimerAction(), SetAlarmAction(),
                SendMessageAction(), CallAction(), OpenSettingsAction(), YouTubeSearchAction(), OpenUrlAction(),
                WebSearchAction(), OpenAppAction(),
            )
        )
    }
}

class EndConversationAction : AssistantAction {
    override val name = "end_conversation"
    override val description = "End the current conversation and return to sleep. args: {}"
    private val phrases = setOf("stop", "goodbye", "good bye", "bye", "end conversation", "end the conversation", "go to sleep",
        "sleep", "that is all", "thats all", "that's all", "dismiss", "exit", "never mind", "nevermind", "cancel")
    override fun parse(text: String) = if (Txt.norm(text) in phrases) emptyMap<String, String>() else null
    override suspend fun execute(ctx: Context, args: Map<String, String>) =
        ActionResult("Returning to the shadows, Master.", endSession = true)
}

class TimeDateAction : AssistantAction {
    override val name = "time_date"
    override val description = "Tell the current time or date. args: {\"what\": \"time\" | \"date\"}"
    override fun parse(text: String): Map<String, String>? {
        val t = Txt.norm(text)
        return when {
            Regex("""\b(what time|current time|the time|time is it|time now)\b""").containsMatchIn(t) && t.length < 40 -> mapOf("what" to "time")
            Regex("""\b(what date|today s date|todays date|the date|what day|date today|current date)\b""").containsMatchIn(t) && t.length < 40 -> mapOf("what" to "date")
            else -> null
        }
    }
    override suspend fun execute(ctx: Context, args: Map<String, String>): ActionResult {
        val now = LocalDateTime.now()
        return if (args["what"] == "date")
            ActionResult("Today is ${now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault()))}.")
        else ActionResult("It is ${now.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))}.")
    }
}

class CalculateAction : AssistantAction {
    override val name = "calculate"
    override val description = "Evaluate arithmetic. args: {\"expression\": \"12*(3+4)\"} using + - * / ^ % and parentheses"
    override fun parse(text: String): Map<String, String>? = Calculator.extractExpression(text)?.let { mapOf("expression" to it) }
    override suspend fun execute(ctx: Context, args: Map<String, String>): ActionResult {
        val e = args["expression"].orEmpty()
        val v = Calculator.evaluate(e) ?: return ActionResult("I could not calculate that, Master.")
        return ActionResult("The answer is ${Calculator.format(v)}.")
    }
}

class SetTimerAction : AssistantAction {
    override val name = "set_timer"
    override val description = "Start a countdown timer. args: {\"seconds\": \"300\"}"
    override fun parse(text: String): Map<String, String>? {
        var t = Txt.norm(text)
        if (!t.contains("timer")) return null
        t = t.replace(Regex("""\ban? (hour|minute|second)"""), "1 $1").replace("half an hour", "30 minutes")
        var total = 0
        Regex("""(\d+)\s*(hours?|hrs?|minutes?|mins?|seconds?|secs?)""").findAll(t).forEach {
            val n = it.groupValues[1].toInt()
            total += when {
                it.groupValues[2].startsWith("h") -> n * 3600
                it.groupValues[2].startsWith("m") -> n * 60
                else -> n
            }
        }
        return if (total > 0) mapOf("seconds" to total.toString()) else null
    }
    override suspend fun execute(ctx: Context, args: Map<String, String>): ActionResult {
        val sec = args["seconds"]?.toIntOrNull()?.coerceIn(1, 86400) ?: return ActionResult("I did not understand the duration, Master.")
        Txt.launch(ctx, Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, sec).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
        val h = sec / 3600; val m = sec % 3600 / 60; val s = sec % 60
        val spoken = listOfNotNull(h.takeIf { it > 0 }?.let { "$it hour${if (it > 1) "s" else ""}" },
            m.takeIf { it > 0 }?.let { "$it minute${if (it > 1) "s" else ""}" },
            s.takeIf { it > 0 }?.let { "$it second${if (it > 1) "s" else ""}" }).joinToString(" ")
        return ActionResult("Timer set for $spoken.", endSession = true)
    }
}

class SetAlarmAction : AssistantAction {
    override val name = "set_alarm"
    override val description = "Set an alarm. args: {\"hour\": \"0-23\", \"minute\": \"0-59\", \"label\": \"optional\"}"
    override fun parse(text: String): Map<String, String>? {
        val t = Txt.norm(text)
        if (!(t.contains("alarm") || t.startsWith("wake me"))) return null
        val m = Regex("""(?:for|at)\s+(\d{1,2})(?:[: ](\d{2}))?\s*(a m|p m|am|pm|a\.m\.|p\.m\.)?""").find(t) ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        val ap = m.groupValues[3].replace(".", "").replace(" ", "")
        if (ap == "pm" && h < 12) h += 12
        if (ap == "am" && h == 12) h = 0
        return if (h in 0..23 && min in 0..59) mapOf("hour" to h.toString(), "minute" to min.toString()) else null
    }
    override suspend fun execute(ctx: Context, args: Map<String, String>): ActionResult {
        val h = args["hour"]?.toIntOrNull(); val m = args["minute"]?.toIntOrNull() ?: 0
        if (h == null || h !in 0..23 || m !in 0..59) return ActionResult("I did not understand the alarm time, Master.")
        val i = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        args["label"]?.takeIf { it.isNotBlank() }?.let { i.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        Txt.launch(ctx, i)
        return ActionResult("Alarm set for ${"%02d:%02d".format(h, m)}.", endSession = true)
    }
}

class OpenSettingsAction : AssistantAction {
    override val name = "open_settings"
    override val description = "Open an Android settings page. args: {\"page\": \"wifi|bluetooth|display|sound|battery|location|apps|airplane|storage|date|language|accessibility|security|nfc|main\"}"
    private val pages = linkedMapOf(
        "wifi" to Settings.ACTION_WIFI_SETTINGS, "wi fi" to Settings.ACTION_WIFI_SETTINGS, "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "display" to Settings.ACTION_DISPLAY_SETTINGS, "brightness" to Settings.ACTION_DISPLAY_SETTINGS, "sound" to Settings.ACTION_SOUND_SETTINGS,
        "volume" to Settings.ACTION_SOUND_SETTINGS, "battery" to Settings.ACTION_BATTERY_SAVER_SETTINGS, "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        "apps" to Settings.ACTION_APPLICATION_SETTINGS, "airplane" to Settings.ACTION_AIRPLANE_MODE_SETTINGS, "storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
        "date" to Settings.ACTION_DATE_SETTINGS, "language" to Settings.ACTION_LOCALE_SETTINGS, "accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
        "security" to Settings.ACTION_SECURITY_SETTINGS, "nfc" to Settings.ACTION_NFC_SETTINGS, "main" to Settings.ACTION_SETTINGS,
    )
    override fun parse(text: String): Map<String, String>? {
        val t = Txt.norm(text)
        if (!Regex("""\bsettings?\b""").containsMatchIn(t)) return null
        if (!Regex("""^(open|show|go to|take me to|launch|start)\b""").containsMatchIn(t)) return null
        val page = pages.keys.firstOrNull { it != "main" && t.contains(it) } ?: "main"
        return mapOf("page" to page)
    }
    override suspend fun execute(ctx: Context, args: Map<String, String>): ActionResult {
        val action = pages[args["page"].orEmpty().lowercase()] ?: Settings.ACTION_SETTINGS
        Txt.launch(ctx, Intent(action))
        return ActionResult("Opening settings.", endSession = true)
    }
}

class YouTubeSearchAction : AssistantAction {
    override val name = "youtube_search"
    override val description = "Search or play something on YouTube. args: {\"query\": \"...\"}"
    override fun parse(text: String): Map<String, String>? {
        val t = Txt.norm(text)
        Regex("""^(?:search|play|find|watch)\s+(.+?)\s+on youtube$""").find(t)?.let { return mapOf("query" to it.groupValues[1]) }
        Regex("""^(?:open )?youtube (?:and )?(?:search|play|find)(?: for)?\s+(.+)$""").find(t)?.let { return mapOf("query" to it.groupValues[1]) }
        return null
    }
    override suspend fun execute(ctx: Context, args: Map<String, String>): ActionResult {
        val q = args["query"].orEmpty()
        if (q.isBlank()) return ActionResult("What should I search on YouTube, Master?")
        try {
            Txt.launch(ctx, Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra("query", q))
        } catch (ignored: ActivityNotFoundException) {
            Txt.view(ctx, "https://www.youtube.com/results?search_query=${Txt.enc(q)}")
        }
        return ActionResult("Searching YouTube for $q.", endSession = true)
    }
}

class OpenUrlAction : AssistantAction {
    override val name = "open_url"
    override val description = "Open a website. args: {\"url\": \"example.com\"}"
    override fun parse(text: String): Map<String, String>? {
        val m = Regex("""^(?:open|go to|visit|launch)\s+(?:the\s+)?(?:website\s+)?((?:https?://)?[a-z0-9-]+(?:\.[a-z0-9-]+)+(?:/\S*)?)$""").find(Txt.norm(text)) ?: return null
        return mapOf("url" to m.groupValues[1])
    }
    override suspend fun execute(ctx: Context, args: Map<String, String>): ActionResult {
        val u = args["url"].orEmpty().trim()
        if (u.isEmpty()) return ActionResult("Which website, Master?")
        val full = if (u.startsWith("http://") || u.startsWith("https://")) u else "https://$u"
        if (!full.startsWith("https://") && !full.startsWith("http://")) return ActionResult("I can only open web links.")
        Txt.view(ctx, full)
        return ActionResult("Opening $u.", endSession = true)
    }
}

class WebSearchAction : AssistantAction {
    override val name = "web_search"
    override val description = "Search the web in the browser. args: {\"query\": \"...\"}"
    override fun parse(text: String): Map<String, String>? {
        val t = Txt.norm(text)
        Regex("""^(?:search|google|look up|search for|search google for|search the web for)\s+(.+)$""").find(t)?.let { return mapOf("query" to it.groupValues[1]) }
        return null
    }
    override suspend fun execute(ctx: Context, args: Map<String, String>): ActionResult {
        val q = args["query"].orEmpty()
        if (q.isBlank()) return ActionResult("What should I search for, Master?")
        Txt.view(ctx, "https://www.google.com/search?q=${Txt.enc(q)}")
        return ActionResult("Searching for $q.", endSession = true)
    }
}

class OpenAppAction : AssistantAction {
    override val name = "open_app"
    override val description = "Open ANY installed app by its name (YouTube, Chrome, WhatsApp, Spotify, Camera, ...). args: {\"app\": \"app name\"}"
    override fun parse(text: String): Map<String, String>? {
        val m = Regex("""^(?:open|launch|start|run)\s+(?:the\s+)?(?:app\s+)?(.+?)(?:\s+app)?$""").find(Txt.norm(text)) ?: return null
        val app = m.groupValues[1].trim()
        return if (app.isNotEmpty() && app.split(" ").size <= 4) mapOf("app" to app) else null
    }
    override suspend fun execute(ctx: Context, args: Map<String, String>): ActionResult {
        val query = args["app"].orEmpty()
        val found = AppResolver.bestMatch(query, AppResolver.installed(ctx))
        val intent = found?.let { AppResolver.launchIntent(ctx, it.pkg) }
        if (intent != null) {
            ctx.startActivity(intent)
            return ActionResult("Opening ${found.label}.", endSession = true)
        }
        val web = when (AppResolver.norm(query)) {
            "youtube" -> "https://www.youtube.com"; "whatsapp" -> "https://web.whatsapp.com"
            "chrome", "google chrome" -> "https://www.google.com"; else -> null
        }
        if (web != null) { Txt.view(ctx, web); return ActionResult("That app is not installed, so I opened the website.", endSession = true) }
        return ActionResult("I could not find an app named $query, Master.")
    }
}

class CallAction : AssistantAction {
    override val name = "call"
    override val description = "Open the phone dialer with a number (the user still presses call). args: {\"number\": \"+911234567890\"}"
    override val sensitive = true
    override fun parse(text: String): Map<String, String>? {
        val t = Txt.norm(text)
        Regex("""^(?:call|dial|phone)\s+([+\d ]{5,})$""").find(t)?.let { return mapOf("number" to it.groupValues[1].replace(" ", "")) }
        Regex("""^(?:call|dial|phone)\s+(.+)$""").find(t)?.let { return mapOf("number" to "", "name" to it.groupValues[1]) }
        return null
    }
    override fun confirmationPrompt(args: Map<String, String>) =
        if (args["number"].isNullOrBlank()) "I cannot look up contacts, Master. Shall I open the dialer so you can choose ${args["name"].orEmpty()}?"
        else "Open the dialer for ${args["number"]}? Say yes to confirm."
    override suspend fun execute(ctx: Context, args: Map<String, String>): ActionResult {
        val n = args["number"].orEmpty()
        Txt.launch(ctx, Intent(Intent.ACTION_DIAL, if (n.isBlank()) Uri.parse("tel:") else Uri.parse("tel:${Uri.encode(n)}")))
        return ActionResult("Dialer opened. Press call when you are ready.", endSession = true)
    }
}

class SendMessageAction : AssistantAction {
    override val name = "send_message"
    override val description = "Prepare an SMS or WhatsApp message to a phone number (the user still presses send). args: {\"channel\": \"sms|whatsapp\", \"number\": \"+91...\", \"text\": \"message\"}"
    override val sensitive = true
    override fun parse(text: String): Map<String, String>? {
        val raw = text.trim()
        val opts = setOf(RegexOption.IGNORE_CASE)
        Regex("""^(?:send\s+(?:a\s+)?)?whatsapp(?:\s+message)?\s+(?:to\s+)?([+\d ]{6,}?)\s+(?:saying|say|that says|that)\s+(.+)$""", opts).find(raw)?.let {
            return mapOf("channel" to "whatsapp", "number" to it.groupValues[1].replace(" ", ""), "text" to it.groupValues[2])
        }
        Regex("""^(?:send|write)\s+(?:a\s+)?(?:text|message|sms)\s+to\s+([+\d ]{6,}?)\s+(?:saying|say|that says|that)\s+(.+)$""", opts).find(raw)?.let {
            return mapOf("channel" to "sms", "number" to it.groupValues[1].replace(" ", ""), "text" to it.groupValues[2])
        }
        return null
    }
    override fun confirmationPrompt(args: Map<String, String>) =
        "Prepare ${args["channel"]} message to ${args["number"]}: ${args["text"]}. Say yes to confirm."
    override suspend fun execute(ctx: Context, args: Map<String, String>): ActionResult {
        val n = args["number"].orEmpty().filter { it.isDigit() || it == '+' }
        val body = args["text"].orEmpty()
        if (n.isEmpty() || body.isBlank()) return ActionResult("I need a phone number and a message, Master.")
        if (args["channel"] == "whatsapp") Txt.view(ctx, "https://wa.me/${n.removePrefix("+")}?text=${Txt.enc(body)}")
        else Txt.launch(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(n)}")).putExtra("sms_body", body))
        return ActionResult("Message prepared. Press send when you are ready.", endSession = true)
    }
}
