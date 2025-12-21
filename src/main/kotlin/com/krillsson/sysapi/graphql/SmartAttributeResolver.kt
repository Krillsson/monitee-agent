package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.smart.SmartData
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

/**
 * Exposes SmartData.rawAttributes.
 *
 * Domain uses a Map<Int, SmartAttribute>, but GraphQL has no Map type, so the schema exposes
 * a list of SmartAttributeEntry { id, attribute }.
 */
@Controller
@SchemaMapping(typeName = "SmartData")
class SmartDataRawAttributesResolver {

    @SchemaMapping
    fun rawAttributes(data: SmartData): List<SmartAttributeEntry> {
        return data.rawAttributes
            .toSortedMap()
            .map { (id, attribute) -> SmartAttributeEntry(id = id, attribute = attribute) }
    }
}

/** Matches schema type SmartAttributeEntry */
data class SmartAttributeEntry(
    val id: Int,
    val attribute: SmartData.SmartAttribute
)
