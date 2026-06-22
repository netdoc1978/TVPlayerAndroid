#!/bin/sh

# Gradle wrapper script

APP_HOME="$(dirname "")"
APP_HOME="$(cd "" && pwd -P)"

CLASSPATH="/gradle/wrapper/gradle-wrapper.jar"

# Determine Java command
if [ -n "" ] ; then
    JAVACMD="/bin/java"
else
    JAVACMD="java"
fi

exec "" -classpath "" org.gradle.wrapper.GradleWrapperMain "$@"