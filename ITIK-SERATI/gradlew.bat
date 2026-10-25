@echo off
set APP_HOME=%~dp0
set JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
if exist "%JAR%" (
  java -Dorg.gradle.appname=gradlew -classpath "%JAR%" org.gradle.wrapper.GradleWrapperMain %*
) else (
  echo gradle-wrapper.jar tiada; menggunakan gradle pada PATH. 1>&2
  gradle %*
)
