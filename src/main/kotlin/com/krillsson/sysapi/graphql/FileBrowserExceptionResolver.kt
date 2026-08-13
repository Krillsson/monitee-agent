package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.filebrowser.FileBrowserException
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.graphql.execution.SubscriptionExceptionResolver
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class FileBrowserExceptionResolver : DataFetcherExceptionResolverAdapter(), SubscriptionExceptionResolver {

    override fun resolveToSingleError(ex: Throwable, env: DataFetchingEnvironment): GraphQLError? {
        return badRequestError(ex)
    }

    override fun resolveException(exception: Throwable): Mono<List<GraphQLError>> {
        return Mono.justOrEmpty(badRequestError(exception)?.let { listOf(it) })
    }

    private fun badRequestError(ex: Throwable): GraphQLError? {
        return if (ex is FileBrowserException) {
            GraphqlErrorBuilder.newError()
                .errorType(ErrorType.BAD_REQUEST)
                .message(ex.message)
                .build()
        } else {
            null
        }
    }
}
