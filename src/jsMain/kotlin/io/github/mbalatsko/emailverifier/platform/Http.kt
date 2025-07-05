package io.github.mbalatsko.emailverifier.platform

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

internal actual fun httpClientEngine(): HttpClientEngine = Js.create()
