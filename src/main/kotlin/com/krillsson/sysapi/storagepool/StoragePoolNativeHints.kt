package com.krillsson.sysapi.storagepool

import com.krillsson.sysapi.graphql.domain.StoragePoolInfoUnavailable
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@RegisterReflectionForBinding(
    StoragePool::class,
    StoragePoolDevice::class,
    StoragePoolScan::class,
    StoragePoolKind::class,
    StoragePoolState::class,
    StoragePoolDeviceRole::class,
    StoragePoolDeviceState::class,
    StoragePoolScanKind::class,
    StoragePoolScanState::class,
    StoragePoolDataSource::class,
    StoragePoolInfoUnavailable::class,
)
class StoragePoolNativeHints
