package com.krillsson.sysapi.docker

import com.github.dockerjava.api.model.AuthConfig
import com.github.dockerjava.core.DockerConfigFile
import com.krillsson.sysapi.core.domain.docker.ContainerImageUpdate
import com.krillsson.sysapi.core.domain.docker.ContainerUpdateEligibility
import com.krillsson.sysapi.core.domain.docker.ContainerUpdateStep
import com.krillsson.sysapi.core.domain.docker.ImagePullLayer
import com.krillsson.sysapi.core.domain.docker.ImagePullLayerPhase
import com.krillsson.sysapi.core.domain.docker.ImageUpdateStatus
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateContainerFinished
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateContainerSkipped
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateContainerStarted
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateFinished
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateJob
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateJobState
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateStarted
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateFailed
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateImagePullProgress
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateJob
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateJobState
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateStepChanged
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateSucceeded
import com.krillsson.sysapi.graphql.mutations.AbortDockerContainerBatchUpdateInput
import com.krillsson.sysapi.graphql.mutations.AbortDockerContainerBatchUpdateOutputAccepted
import com.krillsson.sysapi.graphql.mutations.AbortDockerContainerBatchUpdateOutputFailed
import com.krillsson.sysapi.graphql.mutations.UpdateDockerContainerInput
import com.krillsson.sysapi.graphql.mutations.UpdateDockerContainerOutputFailed
import com.krillsson.sysapi.graphql.mutations.UpdateDockerContainerOutputStarted
import com.krillsson.sysapi.graphql.mutations.UpdateDockerContainersInput
import com.krillsson.sysapi.graphql.mutations.UpdateDockerContainersOutputFailed
import com.krillsson.sysapi.graphql.mutations.UpdateDockerContainersOutputStarted
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@RegisterReflectionForBinding(
    DockerConfigFile::class,
    AuthConfig::class,
    ContainerImageUpdate::class,
    ImageUpdateStatus::class,
    ContainerUpdateEligibility::class,
    ContainerUpdateStep::class,
    ImagePullLayer::class,
    ImagePullLayerPhase::class,
    UpdateDockerContainerInput::class,
    UpdateDockerContainerOutputStarted::class,
    UpdateDockerContainerOutputFailed::class,
    DockerContainerUpdateStepChanged::class,
    DockerContainerUpdateImagePullProgress::class,
    DockerContainerUpdateSucceeded::class,
    DockerContainerUpdateFailed::class,
    DockerContainerUpdateJob::class,
    DockerContainerUpdateJobState::class,
    UpdateDockerContainersInput::class,
    UpdateDockerContainersOutputStarted::class,
    UpdateDockerContainersOutputFailed::class,
    AbortDockerContainerBatchUpdateInput::class,
    AbortDockerContainerBatchUpdateOutputAccepted::class,
    AbortDockerContainerBatchUpdateOutputFailed::class,
    DockerContainerBatchUpdateStarted::class,
    DockerContainerBatchUpdateContainerStarted::class,
    DockerContainerBatchUpdateContainerFinished::class,
    DockerContainerBatchUpdateContainerSkipped::class,
    DockerContainerBatchUpdateFinished::class,
    DockerContainerBatchUpdateJob::class,
    DockerContainerBatchUpdateJobState::class,
)
class DockerNativeHints
