package com.jinwoo.ai

import com.jinwoo.ai.actions.ActionRegistry
import com.jinwoo.ai.actions.AppResolver
import com.jinwoo.ai.ai.Persona
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionParsingTest {
    private val reg = ActionRegistry.default()
    private fun match(t: String) = reg.match(t)

    @Test fun openYoutube() { val m = match("Open YouTube")!!; assertEquals("open_app", m.first.name); assertEquals("youtube", m.second["app"]) }
    @Test fun openAnyApp() { val m = match("please open Spotify")!!; assertEquals("spotify", m.second["app"]) }
    @Test fun settingsBeatsOpenApp() { val m = match("open wifi settings")!!; assertEquals("open_settings", m.first.name); assertEquals("wifi", m.second["page"]) }
    @Test fun timer() { val m = match("set a timer for 1 hour 30 minutes")!!; assertEquals("5400", m.second["seconds"]) }
    @Test fun timerAnHour() { assertEquals("3600", match("set a timer for an hour")!!.second["seconds"]) }
    @Test fun alarmPm() { val m = match("set an alarm for 6:30 pm")!!; assertEquals("18", m.second["hour"]); assertEquals("30", m.second["minute"]) }
    @Test fun alarmAmTwelve() { assertEquals("0", match("set alarm for 12 am")!!.second["hour"]) }
    @Test fun time() = assertEquals("time_date", match("what time is it")!!.first.name)
    @Test fun calc() = assertEquals("calculate", match("calculate 5 plus 3 times 2")!!.first.name)
    @Test fun generalQuestionGoesToAi() = assertNull(match("what is artificial intelligence"))
    @Test fun callIsSensitive() { val m = match("call 9876543210")!!; assertTrue(m.first.sensitive); assertEquals("9876543210", m.second["number"]) }
    @Test fun smsIsSensitive() { val m = match("send a text to 9876543210 saying I am late")!!; assertTrue(m.first.sensitive); assertEquals("I am late", m.second["text"]) }
    @Test fun whatsapp() { assertEquals("whatsapp", match("whatsapp 9876543210 saying hello there")!!.second["channel"]) }
    @Test fun youtubeSearch() { assertEquals("lofi music", match("play lofi music on youtube")!!.second["query"]) }
    @Test fun endConversation() = assertEquals("end_conversation", match("goodbye")!!.first.name)

    @Test fun appResolverFuzzy() {
        val apps = listOf(AppResolver.AppInfo("YouTube", "com.google.android.youtube"), AppResolver.AppInfo("WhatsApp", "com.whatsapp"),
            AppResolver.AppInfo("Camera", "com.cam"), AppResolver.AppInfo("Spotify", "com.spotify.music"))
        assertEquals("com.whatsapp", AppResolver.bestMatch("whatsapp", apps)!!.pkg)
        assertEquals("com.spotify.music", AppResolver.bestMatch("spotify", apps)!!.pkg)
        assertEquals("com.cam", AppResolver.bestMatch("cam", apps)!!.pkg)
        assertEquals("com.whatsapp", AppResolver.bestMatch("whatsap", apps)!!.pkg)
        assertNull(AppResolver.bestMatch("photoshop express pro", apps))
    }

    @Test fun parsePlannedAction() {
        val p = Persona.parseAction("""ACTION: {"action":"set_timer","args":{"seconds":300}}""")!!
        assertEquals("set_timer", p.name); assertEquals("300", p.args["seconds"])
        assertNull(Persona.parseAction("Sure, Master."))
        assertNotNull(Persona.system(reg, "en-US"))
    }
}
