package io.github.mbalatsko.emailverifier.platform

import io.ktor.client.engine.HttpClientEngine

internal expect fun httpClientEngine(): HttpClientEngine
