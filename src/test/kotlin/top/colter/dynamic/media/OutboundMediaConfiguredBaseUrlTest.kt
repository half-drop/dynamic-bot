package top.colter.dynamic.media

import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import top.colter.dynamic.ImageCacheConfig
import top.colter.dynamic.MainDynamicConfig
import top.colter.dynamic.MediaDeliveryConfig
import top.colter.dynamic.MediaDeliveryProfile
import top.colter.dynamic.MediaDeliverySignedUrlConfig
import top.colter.dynamic.MediaDeliveryType
import top.colter.dynamic.WebAdminConfig
import top.colter.dynamic.core.data.MediaKind
import top.colter.dynamic.core.data.MediaRef
import top.colter.dynamic.core.plugin.MessageSinkMediaDeliveryAdvice
import top.colter.dynamic.core.plugin.MessageSinkMediaDeliveryAdviceRequest
import top.colter.dynamic.core.plugin.MessageSinkMediaDeliveryAdvisor
import top.colter.dynamic.core.plugin.MessageSinkMediaDeliveryConfidence
import top.colter.dynamic.core.plugin.MessageSinkMediaDeliveryMethod
import top.colter.dynamic.core.plugin.MessageSinkMediaDeliveryProbeRequest
import top.colter.dynamic.core.plugin.MessageSinkMediaDeliveryProbeResult

class OutboundMediaConfiguredBaseUrlTest {
    @Test
    fun `auto prefers configured signed url before loopback candidates`() = runTest {
        val renderedRoot = createTempDirectory("outbound-configured-base-url")
        val image = renderedRoot.resolve("demo.png")
        image.writeBytes(byteArrayOf(1, 2, 3))
        val advisor = ConfiguredBaseUrlAdvisor()
        val service = OutboundMediaService(
            configProvider = {
                MainDynamicConfig(
                    webAdmin = WebAdminConfig(host = "0.0.0.0", port = 2233),
                    imageCache = ImageCacheConfig(renderedRoot = renderedRoot.toString()),
                    mediaDelivery = MediaDeliveryConfig(
                        defaultProfileId = "auto",
                        profiles = listOf(
                            MediaDeliveryProfile(
                                id = "auto",
                                type = MediaDeliveryType.AUTO,
                                signedUrl = MediaDeliverySignedUrlConfig(
                                    publicBaseUrl = "http://dynamic-bot:2233",
                                    signingSecret = "secret",
                                    ttlSeconds = 60,
                                ),
                            ),
                        ),
                    ),
                )
            },
            nowEpochSeconds = { 1_000 },
        )
        val context = OutboundMediaRouteContext(
            transportId = "onebot",
            routeId = "onebot:qq:42",
            accountId = "42",
            advisor = advisor,
        )

        val rewritten = service.rewriteMedia(
            MediaRef(uri = image.toString(), kind = MediaKind.IMAGE),
            routeContext = context,
        )

        assertTrue(rewritten.uri.startsWith("http://dynamic-bot:2233/media/outbound/"))
        val signedProbes = advisor.probeRequests.filter { it.method == MessageSinkMediaDeliveryMethod.SIGNED_URL }
        assertEquals(1, signedProbes.size)
        assertTrue(signedProbes.single().uri.startsWith("http://dynamic-bot:2233/"))
    }

    private class ConfiguredBaseUrlAdvisor : MessageSinkMediaDeliveryAdvisor {
        val probeRequests = mutableListOf<MessageSinkMediaDeliveryProbeRequest>()

        override suspend fun adviseMediaDelivery(
            request: MessageSinkMediaDeliveryAdviceRequest,
        ): MessageSinkMediaDeliveryAdvice {
            return MessageSinkMediaDeliveryAdvice(
                localFileConfidence = MessageSinkMediaDeliveryConfidence.UNAVAILABLE,
                signedUrlBaseCandidates = listOf("http://example.com:2233"),
            )
        }

        override suspend fun probeMediaDelivery(
            request: MessageSinkMediaDeliveryProbeRequest,
        ): MessageSinkMediaDeliveryProbeResult {
            probeRequests += request
            return when (request.method) {
                MessageSinkMediaDeliveryMethod.LOCAL_FILE -> MessageSinkMediaDeliveryProbeResult.unavailable()
                MessageSinkMediaDeliveryMethod.SIGNED_URL -> if (request.uri.startsWith("http://dynamic-bot:2233/")) {
                    MessageSinkMediaDeliveryProbeResult.available()
                } else {
                    MessageSinkMediaDeliveryProbeResult.unavailable()
                }
            }
        }
    }
}
