@rem
@rem Gradle startup script for Windows
@rem
@if "%DEBUG%"=="" @echo off
setlocal enabledelayedexpansion

set DIRNAME=%~dp0
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

set GRADLE_HOME=%APP_HOME%\gradle
set CLASSPATH=%GRADLE_HOME%\wrapper\gradle-wrapper.jar

@rem Find java.exe
set JAVA_CMD=java
if exist "%JAVA_HOME%\bin\java.exe" set JAVA_CMD="%JAVA_HOME%\bin\java.exe"

"%JAVA_CMD%" -jar "%CLASSPATH%" %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL%==0 goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the script to exit with error
if not ""=="%GRADLE_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
