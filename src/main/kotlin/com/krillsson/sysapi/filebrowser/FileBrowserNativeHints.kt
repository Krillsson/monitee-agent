package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.graphql.mutations.CancelFileOperationInput
import com.krillsson.sysapi.graphql.mutations.CopyFileInput
import com.krillsson.sysapi.graphql.mutations.CopyFilesInput
import com.krillsson.sysapi.graphql.mutations.CreateArchiveInput
import com.krillsson.sysapi.graphql.mutations.CreateDirectoryInput
import com.krillsson.sysapi.graphql.mutations.CreateDirectoryOutput
import com.krillsson.sysapi.graphql.mutations.DeleteFileInput
import com.krillsson.sysapi.graphql.mutations.DeleteFilesInput
import com.krillsson.sysapi.graphql.mutations.EmptyTrashInput
import com.krillsson.sysapi.graphql.mutations.ExtractArchiveInput
import com.krillsson.sysapi.graphql.mutations.FileOperationOutput
import com.krillsson.sysapi.graphql.mutations.MoveFileInput
import com.krillsson.sysapi.graphql.mutations.MoveFilesInput
import com.krillsson.sysapi.graphql.mutations.MoveToTrashInput
import com.krillsson.sysapi.graphql.mutations.MoveToTrashOutput
import com.krillsson.sysapi.graphql.mutations.RestoreFromTrashInput
import com.krillsson.sysapi.graphql.mutations.RestoreFromTrashOutput
import com.krillsson.sysapi.graphql.mutations.SaveTextFileInput
import com.krillsson.sysapi.graphql.mutations.SaveTextFileOutput
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@RegisterReflectionForBinding(
    FileBrowserLimits::class,
    FileEntry::class,
    FileEntryType::class,
    DirectoryListing::class,
    TextFileContent::class,
    FileBrowserError::class,
    SaveTextFileInput::class,
    SaveTextFileOutput::class,
    CopyFileInput::class,
    MoveFileInput::class,
    DeleteFileInput::class,
    CreateDirectoryInput::class,
    CreateDirectoryOutput::class,
    DirectoryListingConnection::class,
    FileEntryEdge::class,
    FileSearchInput::class,
    FileSearchResult::class,
    DirectorySize::class,
    TrashEntry::class,
    ArchiveEntry::class,
    ArchiveListing::class,
    FileOperationFailure::class,
    FileBrowserErrorType::class,
    FileOperation::class,
    FileOperationType::class,
    FileOperationState::class,
    FileOperationOutput::class,
    CancelFileOperationInput::class,
    CopyFilesInput::class,
    MoveFilesInput::class,
    DeleteFilesInput::class,
    ExtractArchiveInput::class,
    CreateArchiveInput::class,
    MoveToTrashInput::class,
    MoveToTrashOutput::class,
    RestoreFromTrashInput::class,
    RestoreFromTrashOutput::class,
    EmptyTrashInput::class,
)
class FileBrowserNativeHints
