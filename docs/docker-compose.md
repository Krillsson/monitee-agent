Let’s create a installation directory in our servers home folder

```bash
mkdir ~/sys-api && cd ~/sys-api
```

Then let’s create the *config*/ and *data*/ directories

```bash
mkdir config && mkdir data
```

The server expects a *configuration.yml* in the configuration directory. Let’s download the latest one using wget

```bash
cd config
```

```bash
wget https://raw.githubusercontent.com/Krillsson/sys-API/refs/heads/master/config/configuration.yml
wget https://raw.githubusercontent.com/Krillsson/sys-API/refs/heads/master/config/application.properties
```

Use your favorite text editor to edit the user & password of the config

```bash
nano configuration.yml
```

![](https://raw.githubusercontent.com/Krillsson/monitee-agent/master/docs/images/Screenshot-2023-09-01-at-09.25.00.png)

Then press **CTRL+X** to exit nano (and press **Y** to save)

Now we can download the latest docker-compose.yml and make our edits to it using nano

```bash
wget https://raw.githubusercontent.com/Krillsson/sys-API/master/docker-compose.yml
```

Remember to swap out the volume mounts for the *config*/ and *data*/ directories

Want to monitor the temperature of disks? Each disk you’d like to monitor has to be added to a devices section in the compose file. List disks on your system using the command **lsblk --nodeps -n -o name**

```yaml
devices:
  - "/dev/sda:/dev/sda"
```

Then we can start the container

```bash
docker compose -f docker-compose.yml up
```

Let’s verify the server started correctly

```bash
docker logs --follow --tail 200 sys-api
```

If you see this message without any error after it. That means the server is up and running

```bash
[...]: Tomcat started on ports 8443 (https), 8080 (http) with context path '/'
[...]: Started SysAPIApplicationKt in 6.352 seconds (process running for 7.234)
```

Now you are ready to proceed to next step

**Connect App to Server**
