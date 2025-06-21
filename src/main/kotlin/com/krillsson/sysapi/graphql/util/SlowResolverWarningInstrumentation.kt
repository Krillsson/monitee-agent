import com.krillsson.sysapi.config.YAMLConfigFile
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant


@Component
class SlowResolverWarningInstrumentation(private val config: YAMLConfigFile) : SimplePerformantInstrumentation() {

    private val logger = LoggerFactory.getLogger(SlowResolverWarningInstrumentation::class.java)
    private val thresholdMs = 200L

    override fun beginFieldFetch(
        parameters: InstrumentationFieldFetchParameters,
        state: InstrumentationState?
    ): InstrumentationContext<Any> {
        if (!config.graphQl.instrumentation) {
            return NO_OP
        }
        val startTime: Instant? = Instant.now()
        return object : InstrumentationContext<Any> {
            override fun onDispatched() {
            }

            override fun onCompleted(result: Any?, t: Throwable?) {
                if (startTime == null) return
                val duration = java.time.Duration.between(startTime, Instant.now()).toMillis()
                if (duration > thresholdMs) {
                    val path = parameters.executionStepInfo.path
                    val field = parameters.executionStepInfo.field.name
                    val arguments = parameters.executionStepInfo.arguments
                    val operationName = parameters.executionContext.operationDefinition?.name ?: "UnnamedOperation"

                    logger.warn(
                        "⚠️ Slow resolver detected: operation='{}', path={}, field='{}', args={}, duration={}ms",
                        operationName,
                        path,
                        field,
                        arguments,
                        duration
                    )
                }
            }
        }
    }

    companion object {
        val NO_OP = object : InstrumentationContext<Any> {
            override fun onDispatched() = Unit
            override fun onCompleted(result: Any?, t: Throwable?) = Unit
        }
    }
}
