@echo off
rem Minimal bootstrap wrapper (see gradlew). Open this folder in Android Studio, or install Gradle and run: gradle wrapper
set APP_HOME=%~dp0
if exist "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" (
  java -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
  exit /b %ERRORLEVEL%
)
where gradle >nul 2>nul
if %ERRORLEVEL%==0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
echo ERROR: gradle-wrapper.jar is missing and Gradle is not installed.
echo Open this folder in Android Studio, or install Gradle and run: gradle wrapper
exit /b 1
