@echo off
cd /d %~dp0
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0sysapi-launcher.ps1" --spring.config.location=classpath:/config/application.properties,optional:file:config/application.properties
pause