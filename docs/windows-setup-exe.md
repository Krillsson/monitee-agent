## 1. Installation

Download the latest setup.exe from [https://github.com/Krillsson/monitee-agent/releases/latest](https://github.com/Krillsson/monitee-agent/releases/latest) and run it

Files will be installed to *C:/Program Files/sysapi/*

Leave *Install and start sysapi as a Windows service* checked to have setup register the service and start it when it finishes.

## 2. Configuration

Use your favorite text editor to edit the user & password of the configuration.yml inside the config directory *C:/Program Files/sysapi/config/configuration.yml*

```yaml
user: 
  username: user # <--- CHANGE ME
  password: password  # <--- AND ME
```

If you need to change the default application ports *8080* and *8443*, you can do so in the *C:/Program Files/sysapi/config/application.properties* file

Restart the *sys-api* service in the Services program for the changes to take effect.

To try the native build, which uses less memory, put the word `auto` in a file named *runtime* in the *config* directory and restart the service. From an administrator Command Prompt:

```cmd
echo auto> "C:\Program Files\sysapi\config\runtime"
```

It reads sensors without OpenHardwareMonitor.

![](https://raw.githubusercontent.com/Krillsson/monitee-agent/master/docs/images/Screenshot-2024-09-29-092341.png)

## 3. Checking that it runs

The service writes its output to *sys-API-servicew.out.log* in the installation directory. It will pause for a long time at:  
**“Initializing OpenHardwareMonitor.”**

This is normal.

When you see:

```bash
[...]: Tomcat started on ports 8443 (https), 8080 (http) with context path '/'
[...]: Started SysAPIApplicationKt in 6.352 seconds (process running for 7.234)
```

We know that the server is up

If you chose not to install the service, you can run *sysapi.exe* directly instead. A console window will pop-up and show the same output.

![](https://raw.githubusercontent.com/Krillsson/monitee-agent/master/docs/images/Screenshot-2024-09-29-090902.png)

The service can also be managed with the *install-service.bat*, *start-service.bat*, *stop-service.bat* and *uninstall-service.bat* files in the installation directory. They are just calling *sys-API-servicew.exe* with different arguments. You can take a look at [winsw documentation](https://github.com/winsw/winsw) if you want to learn more.

## 4. Upgrading

Run the setup.exe of the newer version. It stops the service, replaces the files and starts the service again. Your *config* directory is left as it is.

Now you are ready to proceed to next step

**Connect App to Server**
