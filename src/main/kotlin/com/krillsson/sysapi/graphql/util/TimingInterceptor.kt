package com.krillsson.sysapi.graphql.util

import com.krillsson.sysapi.config.YAMLConfigFile
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.graphql.server.WebGraphQlInterceptor
import org.springframework.graphql.server.WebGraphQlRequest
import org.springframework.graphql.server.WebGraphQlResponse
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class TimingInterceptor(val yamlConfigFile: YAMLConfigFile) : WebGraphQlInterceptor {
    override fun intercept(request: WebGraphQlRequest, chain: WebGraphQlInterceptor.Chain): Mono<WebGraphQlResponse> {
        val startTime = System.currentTimeMillis()
        return chain.next(request).doOnSuccess { response ->
            val endTime = System.currentTimeMillis()
            val requestTime = (endTime - startTime)
            if (requestTime > 500 && yamlConfigFile.graphQl.instrumentation) {
                logger.info("Long running query {} took : {} ms", request.operationName, (endTime - startTime))
            }
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(TimingInterceptor::class.java)
    }
}
