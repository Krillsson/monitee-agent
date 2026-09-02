package com.krillsson.sysapi.storagepool

interface StoragePoolProvider {
    fun supported(): Boolean
    fun pools(): List<StoragePool>
}
