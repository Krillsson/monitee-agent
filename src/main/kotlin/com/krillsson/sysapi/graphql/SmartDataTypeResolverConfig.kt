package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.smart.SmartData
import graphql.schema.TypeResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.RuntimeWiringConfigurer

/**
 * Ensures GraphQL can resolve the concrete runtime type behind the SmartData interface.
 *
 * Without this, queries that include inline fragments (e.g. `... on HddSmartData`) can fail at
 * runtime with: "Abstract type 'SmartData' must resolve to an Object type".
 */
@Configuration
class SmartDataTypeResolverConfig {

    @Bean
    fun smartDataRuntimeWiringConfigurer(): RuntimeWiringConfigurer {
        val resolver = TypeResolver { env ->
            val src = env.getObject<Any?>()

            when (src) {
                is SmartData.Hdd -> env.schema.getObjectType("HddSmartData")
                is SmartData.SataSsd -> env.schema.getObjectType("SataSsdSmartData")
                is SmartData.Nvme -> env.schema.getObjectType("NvmeSmartData")
                // Returning null will trigger the exact runtime error you saw, so we keep it strict.
                else -> null
            }
        }

        return RuntimeWiringConfigurer { wiring ->
            wiring.type("SmartData") { typeWiring ->
                typeWiring.typeResolver(resolver)
            }
        }
    }
}

