package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.logaccess.file.LogFileAccessDeniedException
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.graphql.execution.SubscriptionExceptionResolver
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class LogFileAccessDeniedExceptionResolver : DataFetcherExceptionResolverAdapter(), SubscriptionExceptionResolver {

    override fun resolveToSingleError(ex: Throwable, env: DataFetchingEnvironment): GraphQLError? {
        return forbiddenError(ex)
    }

    override fun resolveException(exception: Throwable): Mono<List<GraphQLError>> {
        return Mono.justOrEmpty(forbiddenError(exception)?.let { listOf(it) })
    }

    private fun forbiddenError(ex: Throwable): GraphQLError? {
        return if (ex is LogFileAccessDeniedException) {
            GraphqlErrorBuilder.newError()
                .errorType(ErrorType.FORBIDDEN)
                .message(ex.message)
                .build()
        } else {
            null
        }
    }
}
