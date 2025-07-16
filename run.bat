@echo off
echo.
echo ====================================================
echo  Feather Wand - JMeter AI Plugin Build Script
echo ====================================================
echo.

REM Build the project
echo Building project with Maven...
call mvn clean package
if %ERRORLEVEL% neq 0 (
    echo ERROR: Maven build failed!
    pause
    exit /b 1
)

REM Check for JMETER_HOME environment variable first
if defined JMETER_HOME (
    set "JMETER_LIB_EXT=%JMETER_HOME%\lib\ext"
    echo Using JMETER_HOME: %JMETER_HOME%
) else (
    REM Try common JMeter installation locations
    if exist "C:\Tools\apache-jmeter-5.6.3\lib\ext" (
        set "JMETER_LIB_EXT=C:\Tools\apache-jmeter-5.6.3\lib\ext"
        echo Found JMeter at: C:\Tools\apache-jmeter-5.6.3
    ) else if exist "C:\apache-jmeter\lib\ext" (
        set "JMETER_LIB_EXT=C:\apache-jmeter\lib\ext"
        echo Found JMeter at: C:\apache-jmeter
    ) else if exist "C:\Program Files\Apache\apache-jmeter\lib\ext" (
        set "JMETER_LIB_EXT=C:\Program Files\Apache\apache-jmeter\lib\ext"
        echo Found JMeter at: C:\Program Files\Apache\apache-jmeter
    ) else if exist "%USERPROFILE%\apache-jmeter\lib\ext" (
        set "JMETER_LIB_EXT=%USERPROFILE%\apache-jmeter\lib\ext"
        echo Found JMeter at: %USERPROFILE%\apache-jmeter
    ) else (
        echo.
        echo ERROR: JMeter installation not found!
        echo.
        echo Please set the JMETER_HOME environment variable to your JMeter installation directory
        echo or ensure JMeter is installed in one of these locations:
        echo   - C:\Tools\apache-jmeter-5.6.3
        echo   - C:\apache-jmeter
        echo   - C:\Program Files\Apache\apache-jmeter
        echo   - %USERPROFILE%\apache-jmeter
        echo.
        echo Example: set JMETER_HOME=C:\your\jmeter\path
        echo.
        pause
        exit /b 1
    )
)

REM Check if lib/ext directory exists
if not exist "%JMETER_LIB_EXT%" (
    echo ERROR: JMeter lib/ext directory not found: %JMETER_LIB_EXT%
    pause
    exit /b 1
)

REM Remove old version if it exists
if exist "%JMETER_LIB_EXT%\jmeter-agent-1.0.10-jar-with-dependencies.jar" (
    echo Removing old plugin version...
    del "%JMETER_LIB_EXT%\jmeter-agent-1.0.10-jar-with-dependencies.jar"
)

REM Copy new version
echo Copying plugin to JMeter lib/ext directory...
copy "target\jmeter-agent-1.0.10-jar-with-dependencies.jar" "%JMETER_LIB_EXT%"
if %ERRORLEVEL% neq 0 (
    echo ERROR: Failed to copy plugin to JMeter directory!
    pause
    exit /b 1
)

echo.
echo ====================================================
echo  Plugin successfully installed to:
echo  %JMETER_LIB_EXT%
echo ====================================================
echo.
echo Restart JMeter to use the Feather Wand plugin.
echo.
pause