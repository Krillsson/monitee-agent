![header](header.png)
[![Docker hub](https://badgen.net/badge/icon/docker?icon=docker&label)](https://hub.docker.com/r/krillsson/sys-api)

# [monitee.app](https://monitee.app/)

Monitee agent (formerly sys-API) provide a [GraphQL API](https://graphql.org/) to your computers hardware.

It publishes and monitors values from [OSHI](https://github.com/oshi/oshi) with the help of [Spring](https://spring.io/). On Windows the information is supplemented with
[OpenHardwareMonitor](https://github.com/openhardwaremonitor/openhardwaremonitor) with a bit of help from [OhmJni4Net](https://github.com/Krillsson/ohmjni4net).

>This is the server backend for the Android app Monitee.
>
><a href="https://play.google.com/store/apps/details?id=com.krillsson.monitee"><img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" alt="Get it on Play Store" height="80"></a>

## Get started

Monitee is two pieces: the app on your phone, and an agent you run on the server you want to watch. The agent does the work — install it first, then point the app at it.

### 1. Install the agent on your server

- [Docker Compose](docs/docker-compose.md)
- [Docker](docs/docker.md)
- [Unraid](docs/unraid.md)
- [TrueNAS Scale](docs/truenas.md)
- [Deb package](docs/deb-package.md)
- [Command line](docs/command-line.md)
- [Windows setup.exe](docs/windows-setup-exe.md)

### 2. Add the server in the app

Install Monitee from Google Play and tap **+**. On your own network it usually finds the server by itself — the agent announces itself over DNS-SD, so there is often nothing to type at all.

- [Connection guide](docs/connect-app-to-server.md)
- [SSL troubleshooting](docs/ssl-troubleshooting.md)

## What can it do?

Query for:
- CPU usage & info
- Memory usage
- List running processes
- List all network interfaces
- Show info from sensors and fans
- Motherboard information
- Manage docker containers
- Manage systemd services
- Manage windows services
- Read logs from files, systemd journal and Windows events 

### Monitoring

Currently, you can monitor

- CPU load
- CPU temperature
- Memory usage
- Network up
- Network upload/download rate
- Drive space
- Drive read/write rate
- Process ID CPU or Memory
- Docker container in running state
- Connectivity
- External IP changed

### GraphQL

GraphQL is available through the `/graphql` endpoint. Checkout the [schema](src/main/resources/). There's also a set of sample queries in the _sample-queries_ directory

A web-UI for trying out the GraphQL-API is also available at `<IP>:8080/graphiql`. If you don't want to expose this functionality. It can be disabled via the configuration.

```yaml
graphQLPlayGround:
  enabled: false
```

## Running

Referr to the [setup guides](#1-install-the-agent-on-your-server) for how to run it on your system.

## Configuration
The application expects a user config file (_configuration.yml_) and a spring configuration file (_application.properties_) in the _/config_ directory.
See the sample files in the [/config](/config) repository directory.

### Self-signed certificates
By default, the Monitee agent will generate a self-signed certificate to enable HTTPS. This is to lower the barrier for encrypted traffic between the client and the server.
Please note that using properly signed certificates is better. Let's Encrypt is a free and good alternative.

For convenience, the certificates are persisted using a java keystore. If you wish to re-generate the certificates, delete the _keystorewww.jks_ file. But note that this will require re-adding the server in Monitee.

## Development
Setup
```sh
git clone [this repo] monitee-agent
```
```sh
./gradlew run
```

License
-------

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
