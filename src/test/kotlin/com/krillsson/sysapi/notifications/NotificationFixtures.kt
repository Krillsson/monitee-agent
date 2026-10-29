package com.krillsson.sysapi.notifications

import com.krillsson.sysapi.notifications.NotificationPolicyStore.StoredNotificationPolicy
import com.krillsson.sysapi.persistence.KeyValueRepository
import io.mockk.every
import io.mockk.mockk

fun inMemoryKeyValueRepository(store: MutableMap<String, String>): KeyValueRepository {
    val repository = mockk<KeyValueRepository>()
    every { repository.get(any()) } answers { store[firstArg()] }
    every { repository.put(any(), anyNullable()) } answers {
        val key = firstArg<String>()
        val value = secondArg<String?>()
        if (value != null) store[key] = value else store.remove(key)
    }
    every { repository.remove(any()) } answers { store.remove(firstArg<String>()) != null }
    return repository
}

fun inMemoryNotificationPolicyStore(
    holder: MutableList<StoredNotificationPolicy> = mutableListOf()
): NotificationPolicyStore {
    val store = mockk<NotificationPolicyStore>()
    every { store.read() } answers { holder.lastOrNull() }
    every { store.write(any()) } answers { holder.add(firstArg()) }
    return store
}
