package com.example.routeplanning.mvp.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class LocalApiEndpointTest {
    @Test
    fun `debug accepts loopback emulator and private LAN hosts`() {
        val hosts = listOf(
            "localhost",
            "127.0.0.1",
            "10.0.2.2",
            "10.42.0.5",
            "172.16.0.2",
            "172.31.255.254",
            "192.168.1.157"
        )

        hosts.forEach { host ->
            assertTrue(host, isAllowedApiEndpoint(URI("http://$host:8000"), true))
        }
    }

    @Test
    fun `debug rejects public and non-private cleartext hosts`() {
        val hosts = listOf("api.example.com", "8.8.8.8", "172.15.0.1", "172.32.0.1")

        hosts.forEach { host ->
            assertFalse(host, isAllowedApiEndpoint(URI("http://$host:8000"), true))
        }
    }

    @Test
    fun `release rejects cleartext even on a private host`() {
        assertFalse(isAllowedApiEndpoint(URI("http://192.168.1.157:8000"), false))
    }

    @Test
    fun `https is accepted for a remote host`() {
        assertTrue(isAllowedApiEndpoint(URI("https://api.example.com"), false))
    }
}
