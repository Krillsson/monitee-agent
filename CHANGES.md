### Unreleased

### 0.30.0
- Migrated to [spring](https://spring.io) framework instead of Dropwizard.
- [Graal Native Image](https://www.graalvm.org/latest/reference-manual/native-image/) Docker image option. Significant reduction in RAM usage.
- Memory monitor now operates based on "used bytes goes above threshold" compared to the old "available bytes goes below threshold" as this is more intuitive.
- Removed deprecated Disks (Drives still remain)
- Fixed issue with container statistics history
- Removed REST-API

#### Spring
- Introduces an additional config file: _application.properties_.
  - Only required if you want to change ports. Sample config is available in /config in the repository.
- The user _configuration.yml_ from Dropwizard is still compatible. Look in /config for an up-to-date version.

#### Graal Native Image
- RAM usage reduced to around **120-200 MB** compared to **600-800 MB** running the standard way
- Native images are distributed under the _krillsson/sys-api:native_ tag on Docker Hub
- Consider this new variant experimental and sys-API may fail to start with obscure errors. If you encounter this, open an issue.
- No Raspberry PI support: only builds for amd64 can be provided at this time, as [GitHub does not support building for arm64 yet](https://github.com/actions/runner-images/issues/5631)

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
   - java.lang.ClassCastException: java.lang.Integer incompatible with java.lang.Long at com.krillsson.sysapi.graphql.scalars.LongCoercing.serialize
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
   -  json database files as well as keystore files in *data/* sub-directory
   
*if you are migrating from v0.16.0 or earlier, simply move **history.json**, **monitors.json**, **events.json** and **keystorewww.jks** to data/ directory*

*it is recommended to re-apply your configuration changes anew in the new **configuration.yml** rather than re-using your old one*

### 0.16.0

- Support for generating self-signed certificate for increased privacy
  - Certificate names are pre-populated with external and internal IP's by default
  - See `selfSignedCertificates` in `configuration.yml`
  - Please note that this feature is not a substitution for properly signed certificates. It is only there to lower the barrier of entry to https.

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
- Every /history/ endpoint now has optional query parameters to limit the output. E.g: `v2/system/load/history?fromDate=2018-09-23T15:11:55.661&toDate=2018-09-23T15:21:25.659`

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
- Add support for hot reloading SSL certs (i.e Let's Encrypt) [Guide](https://github.com/Krillsson/sys-API/wiki/Let's-Encrypt)

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
