#!/usr/bin/env sh
# Wrapper launcher. The standard gradle-wrapper.jar is generated with:
#   gradle wrapper --gradle-version 8.13
# This source bundle intentionally does not fabricate or vendor a binary wrapper JAR.
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
  if [ -n "${JAVA_HOME:-}" ]; then JAVACMD="$JAVA_HOME/bin/java"; else JAVACMD=java; fi
  exec "$JAVACMD" -Dorg.gradle.appname=gradlew -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  echo "[LabelCraft] gradle-wrapper.jar is absent; using installed Gradle $(gradle --version | sed -n '3p' | tr -d '\r')." >&2
  exec gradle "$@"
fi
echo "Gradle wrapper JAR is not bundled and no system Gradle was found." >&2
echo "Install Gradle 8.13, then run: gradle wrapper --gradle-version 8.13" >&2
exit 127
