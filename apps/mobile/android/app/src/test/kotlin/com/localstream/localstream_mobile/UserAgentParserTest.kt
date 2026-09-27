package com.localstream.localstream_mobile

import com.localstream.localstream_mobile.server.UserAgentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserAgentParserTest {

    @Test
    fun desktopVlcUaIsVlcKind() {
        val parsed = UserAgentParser.parse(
            "VLC/3.0.20 LibVLC/3.0.20 (Windows NT 10.0; Win64; x86_64)"
        )
        assertEquals("vlc", parsed.kind)
        assertEquals("VLC", parsed.browser)
        assertEquals("3.0.20", parsed.browserVersion)
        assertEquals("Windows", parsed.platform)
    }

    @Test
    fun desktopVlcOnLinuxHasNoDeviceName() {
        val parsed = UserAgentParser.parse(
            "VLC/4.0.0 LibVLC/4.0.0 (Linux x86_64)"
        )
        assertEquals("vlc", parsed.kind)
        assertEquals("Linux", parsed.platform)
        assertNull(parsed.device)
    }

    @Test
    fun androidChromeParsesDeviceModel() {
        val parsed = UserAgentParser.parse(
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/UD1A.230803.041) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36"
        )
        assertEquals("browser", parsed.kind)
        assertEquals("Chrome", parsed.browser)
        assertEquals("Android", parsed.platform)
        assertEquals("Pixel 8", parsed.device)
    }

    @Test
    fun desktopChromeIsBrowserWithoutDevice() {
        val parsed = UserAgentParser.parse(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        )
        assertEquals("browser", parsed.kind)
        assertEquals("Chrome", parsed.browser)
        assertEquals("Windows", parsed.platform)
        assertNull(parsed.device)
    }

    @Test
    fun edgeIsNotChrome() {
        val parsed = UserAgentParser.parse(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.2592.68"
        )
        assertEquals("browser", parsed.kind)
        assertEquals("Edge", parsed.browser)
        assertEquals("126.0.2592.68", parsed.browserVersion)
    }

    @Test
    fun iosSafariIsIosPlatform() {
        val parsed = UserAgentParser.parse(
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) " +
                "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
        )
        assertEquals("browser", parsed.kind)
        assertEquals("Safari", parsed.browser)
        assertEquals("iOS", parsed.platform)
        assertEquals("iPhone", parsed.device)
    }

    @Test
    fun firefoxAndOkHttp() {
        val firefox = UserAgentParser.parse(
            "Mozilla/5.0 (X11; Linux x86_64; rv:127.0) Gecko/20100101 Firefox/127.0"
        )
        assertEquals("Firefox", firefox.browser)
        assertEquals("Linux", firefox.platform)

        val okhttp = UserAgentParser.parse("okhttp/4.12.0")
        assertEquals("app", okhttp.kind)
        assertNull(okhttp.browser)
    }

    @Test
    fun blankOrNullUaIsOther() {
        assertEquals("other", UserAgentParser.parse(null).kind)
        assertEquals("other", UserAgentParser.parse("").kind)
    }
}