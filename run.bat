@echo off
call mvn clean package
if exist C:\Tools\apache-jmeter-5.6.3\lib\ext\jmeter-agent-1.0.2-jar-with-dependencies.jar (
    del C:\Tools\apache-jmeter-5.6.3\lib\ext\jmeter-agent-1.0.2-jar-with-dependencies.jar
)
copy target\jmeter-agent-1.0.2-jar-with-dependencies.jar C:\Tools\apache-jmeter-5.6.3\lib\ext
@REM C:\Tools\apache-jmeter-5.6.3\bin\jmeter -t C:\Tools\apache-jmeter-5.6.3\bin\GreatClips.jmx