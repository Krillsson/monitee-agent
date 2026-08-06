The agent can publish everything it measures to an MQTT broker, and announce each value to Home Assistant so the server turns up as a device with sensors. Nothing has to be installed on the Home Assistant side beyond its MQTT integration, which most installs already have.

Everything here lives under `mqtt` in `configuration.yml`. It is off by default.

## Getting started

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

- **CPU** — load, temperature, process and thread count, load averages
- **Memory** — usage, used, total, swap
- **System** — boot time, internet connectivity, external IP
- **File systems** — usage and free space per file system
- **Disks** — temperature, SMART health, read and write rate per disk
- **Network** — link state, receive and send rate per interface that is up
- **GPUs** — load, temperature, VRAM used
- **Containers** — how many are running, how many have an image update, and whether each one is running
- **Monitors** — a problem sensor per monitor you have added in the app, on for as long as the monitor is outside its threshold, with its value, threshold and start time as attributes

Load averages, totals and the like are marked as diagnostic, so Home Assistant keeps them out of the way rather than on the main card.

The entity list is worked out fresh every interval. Add a monitor in the app, pull a disk or stop a container and the matching entity appears or disappears on the next publish without a restart. A host running a lot of containers or docker networks can end up with a lot of entities — `containers: false` and `networkInterfaces: false` leave those out while everything else keeps working.

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
  containers: true
  networkInterfaces: true
  homeAssistant:
    enabled: true
    discoveryPrefix: homeassistant
```

`homeAssistant.enabled: false` keeps publishing to `state` and `event` without announcing anything, which is what you want when something other than Home Assistant is reading the topics. `discoveryPrefix` only needs changing if the MQTT integration was set up with a prefix other than the default.

## Troubleshooting

**Nothing appears in Home Assistant.** Check the agent log at startup: a broker it cannot reach is logged once with the reason, and it keeps retrying. `notificationServices` in the GraphQL API reports whether the agent believes it is connected, along with the topics it publishes to.

**Entities are there but say unavailable.** That is the `status` topic saying `offline` — the agent lost its connection to the broker, or the broker dropped the retained message.

**Entities are left over after turning something off.** Discovery messages are retained, so a config the agent no longer publishes stays on the broker until something clears it. The agent clears the ones it knows about, but entities from a topic prefix or a server id you have since changed have to be deleted in Home Assistant, or the topics emptied on the broker.
