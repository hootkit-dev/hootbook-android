package dev.hootkit.platform

import dev.hootkit.core.HttpHeader
import dev.hootkit.core.HttpMethod
import dev.hootkit.core.HttpRequest
import dev.hootkit.core.HttpResponse
import dev.hootkit.core.HttpTransport
import dev.hootkit.core.PlatformException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** No logging interceptors; sensitive request data never enters diagnostics. */
class OkHttpTransport(private val client: OkHttpClient = OkHttpClient()) : HttpTransport {
    override suspend fun send(request: HttpRequest): HttpResponse {
        val call = try {
            val builder = Request.Builder().url(request.url)
            if (!builder.build().url.isHttps || request.timeoutMs == 0u) {
                throw PlatformException.Other("HTTPS and a positive timeout are required")
            }
            request.headers.forEach { builder.addHeader(it.name, it.value) }
            val body = when (request.method) {
                HttpMethod.GET -> null
                HttpMethod.POST, HttpMethod.PUT -> request.body.toRequestBody()
                HttpMethod.DELETE -> request.body.takeIf { it.isNotEmpty() }?.toRequestBody()
            }
            builder.method(request.method.name, body)
            client.newBuilder()
                .followRedirects(request.followRedirects)
                .followSslRedirects(false) // Do not downgrade to plaintext HTTP.
                .callTimeout(request.timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .build().newCall(builder.build())
        } catch (failure: PlatformException) {
            throw failure
        } catch (_: Exception) {
            throw PlatformException.Other("Invalid HTTP request")
        }
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(PlatformException.Network("HTTP request failed"))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            val limit = request.maxResponseBytes
                            val body = it.body
                            if (limit != null && body != null && body.contentLength() >= 0 &&
                                body.contentLength().toULong() > limit) throw PlatformException.TooLarge()
                            val output = ByteArrayOutputStream()
                            body?.byteStream()?.use { input ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    if (limit != null && output.size().toULong() + read.toULong() > limit) {
                                        throw PlatformException.TooLarge()
                                    }
                                    output.write(buffer, 0, read)
                                }
                            }
                            HttpResponse(it.code.toUShort(), (0 until it.headers.size).map { index ->
                                HttpHeader(it.headers.name(index), it.headers.value(index))
                            }, output.toByteArray())
                        }
                        continuation.resume(result)
                    } catch (failure: PlatformException) {
                        continuation.resumeWithException(failure)
                    } catch (_: IOException) {
                        continuation.resumeWithException(PlatformException.Network("HTTP response failed"))
                    } catch (_: Exception) {
                        continuation.resumeWithException(PlatformException.Other("HTTP response failed"))
                    }
                }
            })
        }
    }
}
