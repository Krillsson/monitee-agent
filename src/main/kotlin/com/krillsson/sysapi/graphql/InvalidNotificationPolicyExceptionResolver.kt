package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.notifications.InvalidNotificationPolicyException
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.stereotype.Component

@Component
class InvalidNotificationPolicyExceptionResolver : DataFetcherExceptionResolverAdapter() {

    override fun resolveToSingleError(ex: Throwable, env: DataFetchingEnvironment): GraphQLError? {
        return if (ex is InvalidNotificationPolicyException) {
            GraphqlErrorBuilder.newError(env)
                .errorType(ErrorType.BAD_REQUEST)
                .message(ex.message)
                .build()
        } else {
            null
        }
    }
}
