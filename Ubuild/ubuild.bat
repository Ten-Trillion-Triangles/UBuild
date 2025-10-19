@echo off

REM Get the directory of this batch file
SET "SCRIPT_DIR=%~dp0"

REM Append a path to a java jar to it
SET "JAR_PATH=%SCRIPT_DIR%server\build\libs\server-all.jar"

REM Run the java jar with support for passing arguments
java -jar "%JAR_PATH%" %*
