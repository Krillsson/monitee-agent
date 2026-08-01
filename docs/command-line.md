# Command line

## Linux

Log onto your server

First, let’s make sure the server has java installed

```bash
java -version
```

**The version need to be at least java 21**

Does it say “**-bash: java: command not found**“?

Lets install openjdk:

```bash
sudo apt-get install openjdk-21-jre-headless
```

This will install openjdk version 21. But there are lots of alternatives available. [SDKMAN](https://sdkman.io/install) is a great tool to install and manage java versions.

Now that we have java, let’s continue

Let’s create a installation directory in our servers home folder

```bash
mkdir ~/sys-api && cd ~/sys-api
```

Then go to [GitHub](https://github.com/Krillsson/sys-api/releases/latest) ![](https://raw.githubusercontent.com/Krillsson/monitee-agent/master/docs/images/GitHub-Mark-Light-120px-plus.png) in a browser to find latest release download

Copy the URL of the file *sysapi-shadow-x.x.x.zip*

Then you can download that on your server

**Remember to change x.x.x to the actual version when copy pasting**

```bash
wget https://github.com/Krillsson/sys-API/releases/download/x.x.x/sysapi-shadow-x.x.x.zip
```

Then we can unzip the content into the folder

```bash
unzip sysapi-shadow-x.x.x.zip
```

Move into that directory

```bash
cd sysapi-shadow-x.x.x
```

Use your favorite text editor to edit the user & password of the config

```bash
nano config/configuration.yml
```

![](https://raw.githubusercontent.com/Krillsson/monitee-agent/master/docs/images/Screenshot-2023-09-01-at-09.25.00.png)

Then press **CTRL+X** to exit nano (and press **Y** to save)

Now we can use `nohup` to run the process disconnected from the terminal

```bash
nohup ./run.sh &
```

Then press **CTRL+C** on your keyboard to get back to your terminal input

Now we can verify that the server started correctly by reading and following the latest lines in the output file

Let’s verify the server started correctly

```bash
tail -f nohup.out
```

If you see this message without any error after it. That means the server is up and running

```bash
[...]: Tomcat started on ports 8443 (https), 8080 (http) with context path '/'
[...]: Started SysAPIApplicationKt in 6.352 seconds (process running for 7.234)
```

Again, press **CTRL+C** to get back to the terminal

Now you are ready to proceed to next step

[Connect App to Server](connect-app-to-server.md)
