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

Now we can start the container.

```bash
docker run -d \
    --net=host \
    --pid=host \
    --name monitee-agent \
    --cap-add SYS_RAWIO \
    -v ~/monitee-agent/data:/data \
    -v ~/monitee-agent/config:/config \
    -v /etc/localtime:/etc/localtime:ro \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v /run/udev:/run/udev:ro \
    -v /run/systemd:/run/systemd \
    -v /etc/machine-id:/etc/machine-id:ro \
    -v /run/systemd/journal/socket:/run/systemd/journal/socket:ro \
    -v /run/log/journal:/run/log/journal:ro \
    -v /dev:/dev:ro \
    -v /srv:/srv:ro \
    -v /etc/os-release:/etc/os-release:ro \
    krillsson/sys-api
```

Want to monitor the temperature of disks? Each device you’d like to monitor has to be added to the command above through **--device=/dev/sdX** (sda, sdb, etc). List disks on your system with **lsblk --nodeps -n -o name**

Want to monitor a nvidia GPU? Also add this to the command:  
-e 'NVIDIA_VISIBLE_DEVICES'='all'  
-e 'NVIDIA_DRIVER_CAPABILITIES'='utility'  
--runtime=nvidia

or substitute all with the specific gpu index (e.g “0”)

### Browsing files

The file browser is off by default. It can only reach the directories listed under `roots` in
`configuration.yml`, so a container needs the host directory bind mounted first. Add the mount to
the command above:

```bash
    -v /mnt/user:/storage \
```

and then list the path **inside the container** in `configuration.yml`:

```yaml
fileBrowser:
  enabled: true
  access: READ
  roots:
    - /storage
```

`access: READ` gives browsing, reading, searching, downloading and thumbnails. `access: READ_WRITE`
also allows editing, uploading, copying, moving, deleting, unpacking and creating archives, and the
trash. A few things worth knowing before turning that on:

- The container runs as root, so `READ_WRITE` means the agent password is write access to
  everything you mount. Mount the directories you actually want to reach, never `/`.
- Mount the volume `:ro` while you are on `access: READ`, and the kernel enforces the read only
  part for you.
- Looking inside a zip and reading one file out of it are reads, so they work under
  `access: READ`. Unpacking one writes, and needs `READ_WRITE`.
- The `native` image tag has no image decoder, so thumbnails are only served by the JVM
  image. Everything else in the browser works on both.
- Keep `/config` and `/data` out of the roots. If they are reachable, `READ_WRITE` lets a caller
  edit the agent's own configuration and database, and the agent says so in the log at startup.

Let’s verify the server started correctly

```bash
docker logs --follow --tail 200 monitee-agent
```

When you see the following, we know that the server is up and running:

```bash
[...]: Tomcat started on ports 8443 (https), 8080 (http) with context path '/'
[...]: Started SysAPIApplicationKt in 6.352 seconds (process running for 7.234)
```

Now you are ready to proceed to next step

**Connect App to Server**
