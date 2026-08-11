package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.filebrowser.DirectoryListing
import com.krillsson.sysapi.filebrowser.FileBrowserManager
import com.krillsson.sysapi.filebrowser.FileEntry
import com.krillsson.sysapi.filebrowser.TextFileContent
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
@SchemaMapping(typeName = "FileBrowser")
class FileBrowserResolver(private val manager: FileBrowserManager) {

    @SchemaMapping
    fun enabled(): Boolean = manager.enabled

    @SchemaMapping
    fun access() = manager.access

    @SchemaMapping
    fun roots(): List<FileEntry> = manager.roots()

    @SchemaMapping
    fun listDirectory(@Argument path: String): DirectoryListing = manager.listDirectory(path)

    @SchemaMapping
    fun openTextFile(@Argument path: String): TextFileContent = manager.openTextFile(path)
}
