package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.storagepool.StoragePool
import com.krillsson.sysapi.storagepool.StoragePoolService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
@SchemaMapping(typeName = "StoragePoolInfoAvailable")
class StoragePoolResolver(
    val storagePoolService: StoragePoolService
) {

    @SchemaMapping
    fun pools(): List<StoragePool> {
        return storagePoolService.pools()
    }

    @SchemaMapping
    fun poolById(@Argument id: String): StoragePool? {
        return storagePoolService.poolById(id)
    }
}
