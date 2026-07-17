package top.colter.dynamic.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import top.colter.dynamic.core.data.PlatformId
import top.colter.dynamic.core.plugin.PublisherLoginMethod
import top.colter.dynamic.core.plugin.PublisherLoginProvider
import top.colter.dynamic.core.plugin.PublisherLoginResult
import top.colter.dynamic.core.plugin.PublisherLoginStatus
import top.colter.dynamic.core.plugin.PublisherQrLoginChallenge
import kotlin.test.Test
import kotlin.test.assertContentEquals

class AdminLoginServiceTest {
    @Test
    fun `qr login preserves plugin supplied image`() = runBlocking {
        val imageBytes = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val service = AdminLoginService(
            loginProviderResolver = { ImageQrLoginPlugin(imageBytes) },
            loginScope = scope,
        )

        try {
            val start = service.startQrLogin("weibo")

            assertContentEquals(imageBytes, service.qrImageBytes(start.loginId))
        } finally {
            scope.cancel()
        }
    }

    private class ImageQrLoginPlugin(
        private val imageBytes: ByteArray,
    ) : PublisherLoginProvider {
        override val platformId: PlatformId = PlatformId.of("weibo")
        override val supportedLoginMethods: Set<PublisherLoginMethod> = setOf(PublisherLoginMethod.QR_CODE)

        override suspend fun loginByQrCode(
            onQrCode: suspend (PublisherQrLoginChallenge) -> Unit,
            onStatusChanged: suspend (PublisherLoginResult) -> Unit,
        ): PublisherLoginResult {
            onQrCode(PublisherQrLoginChallenge(qrImageBytes = imageBytes))
            return PublisherLoginResult(PublisherLoginStatus.SUCCESS, "登录成功")
        }
    }
}
