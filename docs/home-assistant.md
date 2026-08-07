The agent can publish everything it measures to an MQTT broker, and announce each value to Home Assistant so the server turns up as a device with sensors. Nothing has to be installed on the Home Assistant side beyond its MQTT integration, which most installs already have.

If the machine you want to watch is the one Home Assistant runs on, the add-on below installs the agent and sets all of this up on its own. For any other server, turn on `mqtt` in its `configuration.yml`.

## The add-on

Add the repository under *Settings → Apps → Install app*, three dots in the top right, *Repositories*:

```
https://github.com/Krillsson/monitee-home-assistant-addon
```

Install **Monitee agent** from the list that appears, then:

1. Set a **password** under *Configuration*. There is no default and it will not start without one.
2. Turn off **Protection mode** under *Info*. Add-ons are kept away from the host by default, which leaves the agent unable to see the host's processes, its physical disks or its containers. Everything else works either way.
3. Start it.

With the Mosquitto broker add-on installed there is nothing else to do — the add-on asks Home Assistant which broker to use, credentials and all, and the machine appears as a device within a minute. Point the app at the Home Assistant machine on port **8443**, with the username and password from *Configuration*.

The add-on writes `configuration.yml` from a handful of options covering what most installs need. Turning on **Custom configuration** hands the file back to you, in `/addon_configs/xxxxxxxx_monitee-agent/`, and everything below applies as usual. It runs on amd64 and aarch64.

## Getting started

Everything here lives under `mqtt` in `configuration.yml`. It is off by default.

```yaml
mqtt:
  enabled: true
  url: "tcp://192.168.0.10:1883"
  username: mqtt
  password: password
```

`url` is the only field without a useful default — `tcp://host:1883` for a plain broker, `ssl://host:8883` for one with TLS, whose certificate has to be trusted by the machine the agent runs on. There is no default, since a broker running next to the agent is still not reachable at `localhost` from inside the docker image.

Within a minute the server appears in Home Assistant under *Settings → Devices & services → MQTT*, named after `notifications.serverName` or the hostname.

## Topics

Everything is published under `<topicPrefix>/<server id>`, `monitee/<server id>` unless `topicPrefix` says otherwise. The server id is the one the app shows under the server's settings.

| Topic | Retained | What it carries |
|---|---|---|
| `.../status` | yes | `online` while the agent is connected, `offline` once it is not |
| `.../state` | yes | every metric as one JSON object, republished every `intervalSeconds` |
| `.../event` | no | one message per notification, as it happens |

`status` is the broker's last will, so it flips to `offline` on its own when the agent is killed or the machine goes down, and every entity goes unavailable with it.

`state` is one object rather than a topic per value, so a host with twenty disks and forty containers still costs one message per interval:

```json
{
  "cpu_load": 12.4,
  "cpu_temperature": 47.0,
  "memory_usage": 38.1,
  "memory_used": 6537216000,
  "fs_mnt_user_usage": 71.2,
  "disk_sda_temperature": 34,
  "container_plex_running": "ON",
  "monitor_3f2a_..._attributes": { "monitor_type": "CPU_LOAD", "threshold": 80.0 }
}
```

`event` gets the same JSON the webhooks post — title, message, priority, click url, event type, monitor type, timestamp, server name and id. See [notifications.md](notifications.md) for the shape and the emoji, which `emoji: false` turns off here too. It makes a good MQTT trigger in an automation.

## What shows up in Home Assistant

Measurements are sensors and states are binary sensors, so a temperature and the health of the
same disk stay two separate things.

| | Sensors | Binary sensors |
|---|---|---|
| **CPU** | load, temperature, processes, threads, load averages | |
| **Memory** | usage, used, total, swap | |
| **System** | boot time, external IP, agent version, operating system | internet connectivity |
| **File systems** | usage, free space | |
| **Disks** | temperature, read and write rate | SMART health |
| **Network** | receive and send rate | link state |
| **GPUs** | load, temperature, VRAM used | |
| **Containers** | how many run, how many have an image update | whether each container runs |
| **UPS** | battery charge, runtime, load, power | whether it is operating normally |
| **Monitors** | | one per monitor added in the app |

A monitor's binary sensor is on for as long as that monitor is outside its threshold, and carries
its value, threshold and start time as attributes. It is named after the monitor with *alert* on
the end — `CPU load alert` — so it does not read like a second copy of the `CPU load` sensor.

### Keeping the default view usable

Three things stop a busy server from arriving as a wall of entities:

- **Diagnostics.** Boot time, load averages, process and thread counts, memory total, swap,
  external IP, agent version, operating system and disk health are marked as diagnostic, so
  Home Assistant files them under the device rather than on the main card.
- **Discovered but disabled.** Per-interface link state and the read/write and receive/send rates
  arrive disabled. They are on the device, and switching one on in Home Assistant starts recording
  it — nothing is lost, it is just out of the way until asked for.
- **Off entirely.** A binary sensor per container is off until `containers: true`, since a server
  can easily run fifty of them. `networkInterfaces: false` drops the per-interface entities.

Interfaces docker creates for itself never reach Home Assistant at all — see
`docker.hideContainerNetworks` below.

### Naming

Names come from whatever the agent knows that a person would recognise. A file system is named
after its label, falling back to its mount point and only then to the device, so a labelled array
reads `Array usage` rather than `/dev/md1p1 usage`. Containers use their container name. Entity
ids are derived from the device or interface name instead, so a relabelled file system keeps its
history.

The entity list is worked out fresh every interval. Add a monitor in the app, pull a disk or stop
a container and the matching entity appears or disappears on the next publish without a restart.

## Every option

```yaml
mqtt:
  enabled: false
  url: ""
  clientId: monitee_<server id>
  username: null
  password: null
  topicPrefix: monitee
  intervalSeconds: 30
  qos: 0
  emoji: true
  containers: false
  networkInterfaces: true
  homeAssistant:
    enabled: true
    discoveryPrefix: homeassistant
```

`homeAssistant.enabled: false` keeps publishing to `state` and `event` without announcing anything, which is what you want when something other than Home Assistant is reading the topics. `discoveryPrefix` only needs changing if the MQTT integration was set up with a prefix other than the default.

## Docker's own network interfaces

A host running docker grows an interface per network and per container — `docker0`,
`br-0fef9fbb2e3d`, `veth2cdce89` — and on a server like Unraid there can be dozens. They are
matched by name rather than by address, because the address says very little: docker's default
pool sits inside `172.16.0.0/12`, which is also where plenty of real networks live, and the pool
is configurable anyway. The names are not. `br0` and other host bridges are deliberately left
alone, since on Unraid that is where the LAN address lives.

```yaml
docker:
  hideContainerNetworks: true
```

This is not an MQTT setting — it applies everywhere the agent reports network interfaces,
including the app and the GraphQL API. Set it to `false` to get them back. A monitor already
watching one of these interfaces keeps working either way; only the lists it can be picked from
are filtered.

## Troubleshooting

**Nothing appears in Home Assistant.** Check the agent log at startup: a broker it cannot reach is logged once with the reason, and it keeps retrying. `notificationServices` in the GraphQL API reports whether the agent believes it is connected, along with the topics it publishes to.

**Entities are there but say unavailable.** That is the `status` topic saying `offline` — the agent lost its connection to the broker, or the broker dropped the retained message.

**Entities are left over after turning something off.** Discovery messages are retained, so a config the agent no longer publishes stays on the broker until something clears it. The agent clears the ones it knows about, but entities from a topic prefix or a server id you have since changed have to be deleted in Home Assistant, or the topics emptied on the broker.
