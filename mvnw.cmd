@SETLOCAL
@SET "MAVEN_PROJECTBASEDIR=%~dp0"
@IF NOT "%MAVEN_BASEDIR%"=="" SET "MAVEN_PROJECTBASEDIR=%MAVEN_BASEDIR%"

@REM Strip the trailing backslash so it does not escape the closing quote
@IF "%MAVEN_PROJECTBASEDIR:~-1%"=="\" SET "MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%"

@SET "WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
@SET "DOWNLOAD_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"
@IF NOT EXIST "%WRAPPER_JAR%" powershell -Command "Invoke-WebRequest -Uri '%DOWNLOAD_URL%' -OutFile '%WRAPPER_JAR%'"
@IF "%JAVA_HOME%"=="" (SET "JAVA_EXE=java.exe") ELSE (SET "JAVA_EXE=%JAVA_HOME%\bin\java.exe")
"%JAVA_EXE%" "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" -cp "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
@ENDLOCAL
