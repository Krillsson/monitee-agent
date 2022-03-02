### 0.17.0

**GraphQL API Breaking changes**
*The monitors API have been rebuilt for better type-safety.*

 - Adds connectivity check and external IP functionality
 - Several new monitors:
   - Connectivity (opt-out in `configuration.yml`)
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