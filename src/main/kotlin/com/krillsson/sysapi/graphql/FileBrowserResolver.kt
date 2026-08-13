package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.filebrowser.DirectoryListing
import com.krillsson.sysapi.filebrowser.DirectoryListingConnection
import com.krillsson.sysapi.filebrowser.DirectorySize
import com.krillsson.sysapi.filebrowser.FileBrowserManager
import com.krillsson.sysapi.filebrowser.FileEntry
import com.krillsson.sysapi.filebrowser.FileSearchInput
import com.krillsson.sysapi.filebrowser.FileSearchResult
import com.krillsson.sysapi.filebrowser.FileTreeWalker
import com.krillsson.sysapi.filebrowser.TextFileContent
import com.krillsson.sysapi.filebrowser.TrashEntry
import com.krillsson.sysapi.filebrowser.TrashService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
@SchemaMapping(typeName = "FileBrowser")
class FileBrowserResolver(
    private val manager: FileBrowserManager,
    private val treeWalker: FileTreeWalker,
    private val trashService: TrashService
) {

    @SchemaMapping
    fun enabled(): Boolean = manager.enabled

    @SchemaMapping
    fun access() = manager.access

    @SchemaMapping
    fun roots(): List<FileEntry> = manager.roots()

    @SchemaMapping
    fun listDirectory(@Argument path: String): DirectoryListing = manager.listDirectory(path)

    @SchemaMapping
    fun listDirectoryConnection(
        @Argument path: String,
        @Argument after: String?,
        @Argument first: Int?
    ): DirectoryListingConnection = manager.listDirectoryPage(path, after, first)

    @SchemaMapping
    fun openTextFile(@Argument path: String): TextFileContent = manager.openTextFile(path)

    @SchemaMapping
    fun search(@Argument input: FileSearchInput): FileSearchResult = treeWalker.search(input)

    @SchemaMapping
    fun directorySize(@Argument path: String, @Argument maxDepth: Int): DirectorySize =
        treeWalker.directorySize(path, maxDepth)

    @SchemaMapping
    fun trashEnabled(): Boolean = trashService.enabled

    @SchemaMapping
    fun trash(): List<TrashEntry> = trashService.list()
}
