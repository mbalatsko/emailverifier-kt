package io.github.mbalatsko.emailverifier.platform

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO

internal actual fun httpClientEngine(): HttpClientEngine = CIO.create()
