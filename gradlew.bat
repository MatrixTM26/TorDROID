@rem Gradle startup script for Windows
@rem Copyright 2015 the original author or authors.
@rem Licensed under the Apache License, Version 2.0
@if "%DEBUG%"=="" @echo off

set APP_HOME=%~dp0

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto execute

echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
goto fail

:execute
"%JAVA_EXE%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:fail
exit /b %ERRORLEVEL%
