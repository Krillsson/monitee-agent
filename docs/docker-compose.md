Let’s create a installation directory in our servers home folder

```bash
mkdir -p ~/monitee-agent/{config,data} && cd ~/monitee-agent
```

The server expects a *configuration.yml* in the configuration directory. Let’s download the latest one using wget

```bash
cd config
```

```bash
wget https://raw.githubusercontent.com/Krillsson/monitee-agent/refs/heads/master/config/configuration.yml
wget https://raw.githubusercontent.com/Krillsson/monitee-agent/refs/heads/master/config/application.properties
```

Use your favorite text editor to edit the user & password of the config

```bash
nano configuration.yml
```

![](https://raw.githubusercontent.com/Krillsson/monitee-agent/master/docs/images/Screenshot-2023-09-01-at-09.25.00.png)

Then press **CTRL+X** to exit nano (and press **Y** to save)

Now let’s write a *docker-compose.yml* next to those directories

```yaml
services:
  monitee-agent:
    container_name: monitee-agent
    image: krillsson/sys-api:latest
    network_mode: host
    restart: unless-stopped
    volumes:
      - ~/monitee-agent/config:/config
      - ~/monitee-agent/data:/data
      - /etc/localtime:/etc/localtime:ro
      - /etc/os-release:/etc/os-release:ro
```

That gives you CPU, memory, network and file system metrics, with monitors and notifications on top. Everything else stays outside the container until you hand it in. Pick the sections below that you want and paste them into the file.

## Docker containers

Lists and manages the containers running next to the agent, including image update detection.

```yaml
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
```

## Processes

Without this the agent only sees its own process.

```yaml
    pid: "host"
```

## Disks and S.M.A.R.T

Reads disk models, serials and health. Each device you’d like the temperature of needs its own entry under *devices*. List the disks on your system with **lsblk --nodeps -n -o name**

```yaml
    cap_add:
      - SYS_RAWIO
    volumes:
      - /run/udev:/run/udev:ro
      - /dev:/dev:ro
    devices:
      - "/dev/sda:/dev/sda"
```

## systemd services and journal logs

```yaml
    volumes:
      - /run/systemd:/run/systemd
      - /etc/machine-id:/etc/machine-id:ro
      - /run/systemd/journal/socket:/run/systemd/journal/socket:ro
      - /run/log/journal:/run/log/journal:ro
```

## NVIDIA GPU

Requires the NVIDIA Container Toolkit on the host. Substitute *all* with the index of a specific GPU (e.g. “0”) if you have more than one. AMD and Intel GPUs are read through the disk section above and need nothing else.

```yaml
    environment:
      - NVIDIA_VISIBLE_DEVICES=all
      - NVIDIA_DRIVER_CAPABILITIES=utility
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: all
              capabilities: [gpu, utility]
```

On older Compose, replace the *deploy* block with `runtime: nvidia`

## Pending package updates

The package database inside the container belongs to the image, so the host’s has to be mounted read-only under `/host` before the agent can read it. These are the paths on a Debian or Ubuntu host:

```yaml
    volumes:
      - /var/lib/dpkg:/host/var/lib/dpkg:ro
      - /var/lib/apt:/host/var/lib/apt:ro
      - /etc/apt:/host/etc/apt:ro
```

The reading is done by the package manager in the image, which is apt, so this covers Debian, Ubuntu and Raspberry Pi OS hosts. Other distributions report that no package manager in the image can read the database. The count can differ by a package or two from what the host reports itself, since the image’s apt is usually older than the host’s.

`/host` is `packageUpdates.hostRoot` in *configuration.yml*. Installations outside a container read the host directly and need none of this.

## Log files

Every directory you want to read logs from has to be mounted, and listed under `logReader` in *configuration.yml*

```yaml
    volumes:
      - /var/log:/var/log:ro
```

Keep one *volumes:* key per service — merge the lines from each section into it rather than repeating the key. The [docker-compose.yml](https://raw.githubusercontent.com/Krillsson/monitee-agent/master/docker-compose.yml) in the repository has every section in one file if you would rather start from that and delete what you don’t need.

Then we can start the container

```bash
docker compose -f docker-compose.yml up -d
```

Let’s verify the server started correctly

```bash
docker logs --follow --tail 200 monitee-agent
```

If you see this message without any error after it. That means the server is up and running

```bash
[...]: Tomcat started on ports 8443 (https), 8080 (http) with context path '/'
[...]: Started SysAPIApplicationKt in 6.352 seconds (process running for 7.234)
```

Now you are ready to proceed to next step

**Connect App to Server**
