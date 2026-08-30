### Unreleased

- `updateDockerContainers` updates a list of containers one at a time, tolerating a failed one and continuing with the rest, and can be stopped mid-way with `abortDockerContainerBatchUpdate`
- `containerUpdateJob` and `containerBatchUpdateJob` let a client discover a container update or batch already in progress, or check on one it lost track of, without needing the id from the mutation that started it
- `DockerContainer.updateEligibility` says up front whether a container can be updated and why not, covering the container monitee-agent itself runs in the same way it already covered Swarm-managed containers and ones another container's network depends on
- Fix: File operations say what went wrong when a copy runs out of disk space or a destination cannot be written, instead of reporting `No space left on device`, a `/source -> /destination` pair or an internal temporary file name
  - `FileOperation`, `FileOperationFailure` and the file browser mutation results carry a `FileBrowserErrorType`
  - A destination the agent cannot write to, and a copy that will not fit on the volume it is going to, are refused before the operation starts
  - A batch reports FAILED rather than COMPLETED when none of its paths worked, and stops early once the volume is full
- Fix: `fileSystems { metrics { totalSpaceBytes } }` no longer re-enumerates every filesystem once per filesystem, which could time out the app's setup query on hosts with hundreds of mounts

### 0.43.0

- Feature: Web server checks are now checks
  - A check has a name, can be turned off without being deleted, and runs on its own interval and timeout instead of sharing one 30 second schedule
  - Read them through the `Check` interface with `checks` and `checkById`, and change them with the create, update, delete, enable and run mutations
  - The `webServerChecks` queries, mutations and types still answer and are marked deprecated
  - In addition to HTTP checks, there are now also TCP, DNS, and PING checks.
  - New monitor type "Check latency", which raises an event when a check keeps answering but takes longer than its threshold
  - Check results are aggregated into hourly and daily buckets once an hour
  - `ignoreCertificateErrors` accepts a self signed certificate for one check without loosening anything else
  - See `sample-queries/Checks.graphql`
- Feature [Beta]: Browse, read, edit, download, upload and manage files in the directories listed under `fileBrowser` in `configuration.yml`
  - This feature is under development and the APIs may change.
  - It is opt-in. Nothing is exposed unless you explicitly enable it and specify directories.
  - `access: READ` exposes browsing, searching, reading, downloading and thumbnails, 
  - `access: READ_WRITE` also allows saving, uploading, copying, moving, deleting, archives and the trash
  - Downloads and uploads are streamed over HTTP at `/files/download` and `/files/upload`
  - A browsed log file can be opened in the log viewer and tailed like any configured one
  - `search` finds a name anywhere under a path
  - `extractArchive` unpacks zip, tar, tar.gz and gz, and `createArchive` packs a zip
  - Copy, move, delete and the archive mutations starts a `FileOperation`. Watch its progress using the subscription `fileOperationProgress`
  - See `sample-queries/FileBrowser.graphql`
- Updating a container now removes the image it was running as a last step, when the update left it untagged and unused
- Fix: Update OSHI to 7.6.0 to bring in a fix for GPU utilization always being 0% on Linux
- Fix: Check push notifications show the check's name instead of its id
- Fix: events left behind by a monitor that no longer exists are purged on startup
- Fix: a day with more than one outage counted only the last one towards its downtime
- Fix: smartctl, upsc, systemctl and journalctl were treated as present on any host whose `/bin/sh` is dash, so the agent kept calling tools that were not installed
- Fix: a network interface that no longer exists is dropped instead of being polled on every refresh, which stopped a docker host filling the log with errors about the veth interfaces of removed containers
- Fix: `openLogFileConnection` and the `tailLogFile` subscription now only read files the `logReader` configuration exposes, instead of any path they are given

### 0.42.1

- Reduced memory footprint: SerialGC with a capped Metaspace and code cache, jemalloc in the docker image and a short metrics cache bring resident memory under sustained query load down from around 550 MB to around 485 MB
- Memory no longer creeps up the longer the agent runs. Under the same load it used to grow by about 75 MB and stay there, because memory the agent had already freed was held onto by the allocator rather than returned to the system
- Fix: the docker image no longer declares a volume for `/var/run/docker.sock`
- The agent no longer starts Spring auto-configuration it does not use, which loads around 800 fewer classes and takes about 3 MB off Metaspace

### 0.42.0

- Feature: Container image updates
  - Compares the image behind every container with the one its registry currently serves. Status per container via `DockerContainer.imageUpdate`, and `DockerAvailable.containersWithImageUpdates` for a badge. Images that cannot be compared are reported as skipped rather than up to date: built locally, pinned to a digest, or private without credentials
  - New monitor type added: "Container update available". An outdated container also raises a generic event, which is what sends the push notification
  - Update a container with the `updateDockerContainer` mutation. It pulls the image and creates the container again from its own configuration, keeping networks, static IPs, aliases, volumes, port bindings and resource limits
  - The mutation answers with a `jobId` right away. Follow the update with the `dockerContainerUpdate` subscription to get every step it goes through and the progress of the image pull
  - Docker cannot update a container in place, so it is replaced and comes back with a new id. Monitors, events and metrics history follow it over
  - Containers managed by Swarm are refused. Compose managed ones work, but the container differs from its compose file until the next `compose up`
  - Configured under the `docker.updateCheck` section in configuration.yml, where `notify` turns the push off while detection keeps running
  - See `sample-queries/ContainerUpdates.graphql`
- Feature: Notifications to a generic webhook
  - `notifications.webhooks` takes a list of receivers, each an url with a method, headers, optional basic auth and an optional body template. Every notification goes to all of them, alongside ntfy
  - Without a template the agent posts its own JSON with the title, message, priority, click url, event type, monitor type, timestamp and server name and id
  - A template substitutes `{{title}}` style placeholders and escapes them for the content type, so a body can be shaped into whatever the receiver expects. 
  - Templating covers Gotify, Discord, Slack, Telegram, Apprise, Nextcloud notify_push and Home Assistant, all with configuration only
  - See [docs/notifications.md](docs/notifications.md)
- Feature: MQTT publishing with Home Assistant discovery
  - `mqtt.enabled` connects to a broker and publishes every metric to one retained JSON topic, with an `online`/`offline` last will next to it and every notification on a topic of its own
  - Each value is announced to Home Assistant, so the server turns up as a device with sensors for CPU, memory, boot time, file systems, disks, network interfaces, GPUs, connectivity and containers. Nothing to install on the Home Assistant side
  - Every monitor added in the app becomes a problem sensor that follows its ongoing event, carrying the value, threshold and start time as attributes
  - The entity list is worked out on every publish, so monitors, containers and disks that come and go appear and disappear without a restart
  - Measurements are sensors and states are binary sensors, so a disk's temperature and its SMART health are two entities rather than two readings with the same name. UPS status, battery, runtime, load and power are published too
  - Diagnostic values are marked as such, and the per-interface link state and the read/write rates are discovered disabled, so a server arrives with a usable default view instead of a wall of entities. Turning one on in Home Assistant starts recording it
  - A binary sensor per container is now off by default, since a busy server runs a lot of them
  - See [docs/home-assistant.md](docs/home-assistant.md)
- Feature: Home Assistant add-on
  - Installs the agent on Home Assistant OS from [its own repository](https://github.com/Krillsson/monitee-home-assistant-addon), so the machine Home Assistant runs on can be watched from the app like any other server
  - It picks up the broker Home Assistant already uses, so the machine turns up as a device without any configuration
  - See [docs/home-assistant.md](docs/home-assistant.md)
- The interfaces docker creates for itself (`docker0`, `br-<network id>`, `veth*`) are no longer reported as network interfaces. `docker.hideContainerNetworks: false` brings them back.
- Feature: ntfy authentication. A protected topic takes either `token` or `username` and `password` under `notifications.ntfy`
- ntfy notifications are now tagged with what happened and what it is about, which ntfy shows as an emoji in front of the title. `notifications.ntfy.emoji: false` sends no tags
- Update OSHI to v7.4.4 (fixes GPU bugs on a Linux host)
- Fix: a monitor going back inside its threshold sent a push worded as if a new event had started. Resolved events now get their own notification saying things are back to normal
- Fix: the native image failed to start on any Linux host with a graphics card, because the NVIDIA NVML binding was missing its native-image proxy registration. GPU sampling can also no longer prevent the agent from starting

### 0.41.1

 - Fix: GPU monitoring inside the docker image. The image now ships `pciutils`, which is needed to enumerate graphics cards
 - NVIDIA GPUs additionally require the container to run under the NVIDIA container runtime with `NVIDIA_VISIBLE_DEVICES` and `NVIDIA_DRIVER_CAPABILITIES` (must include `utility`) set. See `docker-compose.yml`

### 0.41.0

 - Initial support for GPU monitoring. See `gpus`, `gpuById` queries and `gpuMetrics`, `gpuMetricsById` subscriptions in the GraphQL API
 - Update OSHI to v7.3.2

### 0.40.1
 
 - Fix: `packetsReceived` and `packetsSent` can become too large to not fit inside an Integer. These are now Long instead.

### 0.40.0

- Feature: Integration with NUT (Network UPS Tools)
  - List devices using `upsDevices` query method
  - Watch UPS device metrics with upsMetricsById subscription
  - Historical data is available via the `upsMetricsHistoryBetweenTimestamps` query method
  - Three new monitor types added: "UPS Load Percentage", "UPS Load Watts" and "UPS Operational"
  - Requires upsc to be available. The docker image comes with this command installed inside container.
  - Needs opt-in from configuration.yml, see `ups` section.
- Feature: More SMART data points
  - Different data points depending on device type: Hdd, SataSsd or Nvme
  - Basic evaluation of device health (see HealthAnalyzer.kt)
  - New monitor type added: "Disk SMART health"
  - Support passing additional flags to smartctl (see smart section in configuration.yml)
  - Missing a crucial datapoint? Open an issue and I'll see what I can do
- Feature: Internet services availability
  - List services and their ping at server startup with `system.connectivity.internetServicesAvailability`
  - Then subscribe to internetServicesAvailability Subscription endpoint to get updates on their ping
  - Needs opt-in from configuration.yml, see `internetServicesCheck` section.
- Fix: also delete associated monitors while deleting a webserver check
- Fix: resolve start and end timestamps being swapped for past events
- Fix/Breaking: PID monitors no longer cause monitors API to fail with an error. 
  - `Monitor.monitoredItem` and `Monitor.currentValue` are now nullable 

### 0.39.1

- Fix: prevent spinning up disks while querying for temperature using smartctl

### 0.39.0

- Fix: addressed excessive RAM usage by capping heap size in JVM options
- Feature: customize temperature unit in notifications. See formatting section in the configuration.yml file.

### 0.38.0

- Feature: [Ntfy](https://ntfy.sh/) notifications support. 
  - Opt-in by enabling it in the [configuration.yml](https://github.com/Krillsson/monitee-agent/blob/17e43c06f5d740235706b0771e41004a383130f0/config/configuration.yml#L63) 
  - Monitee-agent can now send push notifications to the Monitee app via the third-party service
  - Initially, it sends monitor alerts and notices about new versions of monitee-agent 

### 0.37.6

- Fix: application failed to start with "there was no ServletWebServerFactory bean defined in the context."

### 0.37.5

- Fix: slow response times and poor performance when using monitors. And lots of "Long running query" messages in the
  log
- Feature: API instrumentation logging is now opt-in. Enable it via the graphql instrumentation entry in
  configuration.yml

### 0.37.4

- Fix: another crash related to mDNS on TrueNAS

### 0.37.3

- Fix: UPnP and mDNS now support passing the application ports as ENV vars. Previously they were reading the config.

### 0.37.2

- Feature: add healthcheck endpoint to support TrueNAS
    - Set `management.endpoint.health.enabled=false` in application.properties to disable

### 0.37.0

- Feature: added `monitoredItem` API to Monitor. To support showing monitored item name instead of id.
- Fix: HDD temperatures go missing
- Fix: monitoring history for containers and webserverchecks was missing (failing detailed monitor view for those types)

### 0.36.0

- Feature: HDD temperatures
    - Docker requirement: add devices you'd like to monitor. E.g: --device=/dev/sda
    - Linux standalone requirement: depends on smartctl. `sudo apt-get install smartmontools`
    - Windows caveat: appears to only work with SATA drives that have drive letters (so no NVMe drives)
- Fix: Add more safety checks so systemd is not enabled when it's not supported
- Fix: survey Linux CPU temp sensors at startup and pick the most appropriate one. See configuration.yml to override it.
- Fix: cleanup graphQLPlayGround config from configuration.yml use spring.graphql.graphiql.enabled in
  application.properties
- Fix: InvalidFormatException: Cannot deserialize value of type com.github.dockerjava.api.model.Capability
- Fix: Java.lang.IllegalArgumentException: MonitorManager requires initialization. Call initialize
- Fix: Journalctl: invalid option -- “1”

### 0.35.1

- Fix UpdateChecker not being run
- Application now writes default application.properties and configuration.yml to /config directory if its missing

### 0.35.0

- New feature: log files, container logs & the systemd journal are now paginated
    - Read more about GraphQL pagination [here](https://graphql.org/learn/pagination/)
- New feature: GraphQL Subscription is available for realtime updates of log content

### 0.34.0

- New feature: GraphQL Subscriptions
    - Subscriptions enable server-side push of messages to the client
    - All metrics are now available for subscription, see system.graphqls

### 0.33.0

- New API: Support killing process `killProcess(pid: Int, forcibly: Boolean)`
    - Note: sys-API likely needs to run as root/admin to kill anything other than the current users processes
- New API: `processByPid(pid: Int!)` to support the above. Note that the API will return null if the process is dead.
- Past events are now trimmed to max 100 and according to configured history retention time

### 0.32.0

- Distribution: Project now packaged as a .deb file for easier installation on Debian based systems
    - .deb file depends on openjdk-21-jre-headless and daemon apt packages. Any java 21+ installation will do.
- Distribution: Windows installer with bundled jre for easier installation on Windows
- Systemd service definition is included in the .deb file
- Run sys-API as a Windows service (using [winsw](https://github.com/winsw/winsw))
- Fix long response time while querying monitor history
- Fix right-click "Run as Administrator" in Windows

### 0.31.3

- Fix CPU load not updating properly
- Fix native build

### 0.31.2

- Properly opt-in to OSHIs load average handling on Windows
- More robust handling of data directory and fix error in JarLocation

### 0.31.1

- Fixes for running on windows
- Fixed issue with serialization when using docker-java client

### 0.31.0

- Support webserver checks
    - Calls a webserver endpoint using GET and checks if response is 200 / OK
    - Calculates uptime based on non-200 responses
    - Added monitor type `WEBSERVER_UP`
    - API: Check out the `WebserverCheck` types in **monitoring.graphqls**

### 0.30.0

- Migrated to [spring](https://spring.io) framework instead of Dropwizard.
- [Graal Native Image](https://www.graalvm.org/latest/reference-manual/native-image/) Docker image option. Significant
  reduction in RAM usage.
- Memory monitor now operates based on "used bytes goes above threshold" compared to the old "available bytes goes below
  threshold" as this is more intuitive.
- Removed deprecated Disks (Drives still remain)
- Fixed issue with container statistics history
- Removed REST-API
- This release require version 21 of Java

#### Spring

- Introduces an additional config file: _application.properties_.
    - Only required if you want to change ports. Sample config is available in /config in the repository.
- The user _configuration.yml_ from Dropwizard is still compatible. Look in /config for an up-to-date version.

#### Graal Native Image

- RAM usage reduced to around **120-200 MB** compared to **600-800 MB** running the standard way
- Native images are distributed under the _krillsson/sys-api:native_ tag on Docker Hub
- Consider this new variant experimental and sys-API may fail to start with obscure errors. If you encounter this, open
  an issue.
- No Raspberry PI support: only builds for amd64 can be provided at this time,
  as [GitHub does not support building for arm64 yet](https://github.com/actions/runner-images/issues/5631)

### 0.20.0

- added `deletePastEventsForMonitor`, `closeOngoingEventForMonitor` to the GraphQL-API
- added `Monitor.maxValue` to the GraphQL-API. Useful when displaying monitored value in a graph.
- added start value to past events
- Container metrics support
    - metricsForContainer(id) for near realtime metrics
    - containerMetricsHistoryBetweenTimestamps(id, from, to) for history
    - added monitor types for container cpu load and container memory usage
- Performance updates that should result in lower CPU usage
- Tweaked JVM parameters for performance (update your docker-compose.yml)

### 0.19.3

- fix NPE when querying ContainerNetworkSettings while using podman in rootless mode

### 0.19.2

- Disabled admin interface in configuration.yml
- Support specifying custom docker host (such as podman). See docker section in configuration.yml
- Fixed: "System has not been booted with systemd as init system"...

### 0.19.1

- Fixed: historyBetweenDates query throwing error
- Fixed: db locking issue due to SQLite only allowing one simultaneous connection

### 0.19.0

#### Linux

- List and manage system daemon services (start, stop, reload etc.)
    - From docker: requires new volume mounts. See docker-compose.yml
    - From docker: only works on host systems with systemd
- Read system daemon journal logs
    - Same notices as above

#### Windows

- List and manage services (start, stop, pause etc.)
    - Not supported from within Docker
- Read event logs
    - Same notice as above
- Updates to OpenHardwareMonitor integration to fix CPU metrics

#### Other features

- Read log files from a directory (see sample in configuration.yml)
- Add one, five and fifteen LoadAverages to GraphQL-API.
- Add monitors for load averages
- Add support for automatic port forwarding using UPnP-IGD.
- Generic events concept
    - Update available on GitHub
    - Monitored item disappeared

#### Under the hood

- Query networkInterface and fileSystem by ID
- Query container, system daemon service and windows service by name
- More fine-grained control over periodic tasks

### 0.18.3

- Fixed: querying network interfaces on Windows takes too long
- Fixed: Docker client timeout being unreasonably long (3m)

### 0.18.2

- Fix id field being empty for some Filesystems
    - Stability and uniqueness cannot be guaranteed. Duplicates will be discarded.

### 0.18.1

- Fixed: CPU load and CPU core load freezing after a while for real this time.
- Resolved issue where periodic tasks stopped executing
- Provide ID's for FileSystems
- Fixed: monitored items that disappear causes requests to fail when querying their value

### 0.18.0

- History is now stored in a SQLite file.
- Enabling storage of significantly more history and circumventing storing it in memory
- Docker image for arm64 architecture
- Improved handling of build-date and version in the API
- Add support for mDNS on local network. Making it easier for client discover the server.
- Fixed: not all disks and filesystems show up. This deprecates Drives and introduces separate Disks and FileSystems.
    - Changes to sample _docker-compose.yml_ on how to expose hdd's for monitoring
- Fixed: CPU load and CPU core load freezing after a while

### 0.17.2

- Fix issue with adding memory monitors
- Fix issue with querying speed on NIC's
- Made some improvements to docker-compose file
- Latest OSHI dependency

### 0.17.1

- Fix issue with adding numerical monitors
    - java.lang.ClassCastException: java.lang.Integer incompatible with java.lang.Long at
      com.krillsson.sysapi.graphql.scalars.LongCoercing.serialize
- Fix ongoing events not stopping properly

### 0.17.0

**GraphQL API Breaking changes**

*The monitors API have been rebuilt for better type-safety.*

- Adds connectivity check and external IP functionality
- Several new monitors:
    - Connectivity (opt-out in *configuration.yml*)
    - Drive read/write rate
    - Network upload/download rate
    - External IP changed
    - Process CPU usage
    - Process memory usage
    - Process died (pid disappeared)
- Monitors now have three subgroups
    - Numerical: positive integer values such as Bytes, Temperature, etc
    - Fractional: percentage values such as CPU utilization
    - Conditional: either or values such as network up/down or connected/disconnected
- Monitors now have `currentValue` and `history` fields
- Read logs from a container
- To prepare for dockerization of sys-API:
    - *configuration.yml* now lives in *config/* sub-directory
    - json database files as well as keystore files in *data/* sub-directory

*if you are migrating from v0.16.0 or earlier, simply move **history.json**, **monitors.json**, **events.json**
and **keystorewww.jks** to data/ directory*

*it is recommended to re-apply your configuration changes anew in the new **configuration.yml** rather than re-using
your old one*

### 0.16.0

- Support for generating self-signed certificate for increased privacy
    - Certificate names are pre-populated with external and internal IP's by default
    - See `selfSignedCertificates` in `configuration.yml`
    - Please note that this feature is not a substitution for properly signed certificates. It is only there to lower
      the barrier of entry to https.

### 0.15.2

- Fix issues with docker-java

### 0.15.1

- Reverts standalone image
- Reverts to Java 8 for now

_Sorry for the confusion_

### 0.15.0

- Requires java 11 jre
- Docker support! Opt-in by enabling in configuration.yml
- Persist history to save memory
- History no longer include running processes (it was taking up too much space)
- Added monitors for individual process memory and cpu loads
- Now shipped as a standalone runtime (embedded jre)
- Update OSHI dependency
- Fixed a few serialization errors in GraphQL layer

### 0.14.1

**REST API Breaking changes**

- Optimize GraphQL layer
- Convert more classes to Kotlin
- Fix paths for OHMJNIWrapper
- More robust way of calculating processor utilization

### 0.14

**REST API Breaking changes**

- Migrated project to Gradle
- Add PhysicalMemory to MemoryInfo
- Remove as much nullability from GraphQL schema as possible
- Events persistence
- Update dependencies

### 0.13

- Migration to Kotlin
- GraphQL support
- Events persistence

### 0.12

- Dates are now serialized as: `2019-02-04T22:08:42.048+01:00`
- Latest dropwizard
- Added `GET /monitors/{id}/events` endpoint (get events for a monitor)

### 0.11

**REST API Breaking changes**

- OSX: Fixes related to drives migrated to APFS
- Network speed is now included in NetworkInterface object (/system/ & /nics/)
- Drive object now has a sizeBytes property (/system/ & /drives/)
- /system/load now includes top ten memory consuming processes by default. Configurable via query parameter.
- Every /history/ endpoint now has optional query parameters to limit the output.
  E.g: `v2/system/load/history?fromDate=2018-09-23T15:11:55.661&toDate=2018-09-23T15:21:25.659`

### 0.10

Lot's of new features! **And unfortunately an REST API breaking release.**

Changelog:

- Monitoring
- Split static information from system load information
- Load history
- Caching of values so that each server call does not mean a system call
- Latest dropwizard and OSHI
- More flexible configuration of polling and caching

*Please keep in mind that the API is still in it's early stages and is subject to change*

### 0.9

- Network tx/rx and Disk r/w are now fetched from OpenHardwareMonitor on Windows
- Add support for hot reloading SSL certs (i.e Let's
  Encrypt) [Guide](https://github.com/Krillsson/sys-API/wiki/Let's-Encrypt)

*Please keep in mind that the API is still in it's early stages and is subject to change*

### 0.8

- New feature: GET /CPU/ now returns detailed load per core
- The deliverable now includes a Postman collection that covers most of the functionality of the API
- Fixes issue where APFS storage did not have a OSFileStore
- Updated Dropwizard, OSHI and Jackson

*Please keep in mind that the API is still in it's early stages and is subject to change*

### 0.7

- Fix bug where osFileStore was sometimes missing from JSON payload
- Updated Dropwizard and OSHI

*Please keep in mind that the API is still in it's early stages and is subject to change*

### 0.6

- Changed information source from Sigar to OSHI
- Better calculation of speed (nic dl/upl, disk r/w)
- GUI version is now included

*Please keep in mind that the API is still in it's early stages and is subject to change*

### 0.5

- Support for Raspberry Pi
- Added id's to Filesystems

*Please keep in mind that the API is still in it's early stages and is subject to change*

### 0.4

- Added GET /meta/version and GET /meta/pid
- GET /cpu/ now includes process statistics
- Fixed nasty crash in Sigar
- Fixed NullReferenceException in OpenHardwareMonitorLib.dll

*Please keep in mind that the API is still in it's early stages and is subject to change*

### 0.3

- Added OpenHardwareMonitor support on Windows
- Added configuration option to forward HTTP requests to HTTPS

*Please keep in mind that the API is still in it's early stages and is subject to change*

### 0.2

- This is the first release of System Api. Keep in mind that this is a very early version.

*Please keep in mind that the API is still in it's early stages and is subject to change*
