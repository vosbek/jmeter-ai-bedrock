@echo off
call mvn clean package
del C:\Tools\apache-jmeter-5.6.2\lib\ext\validatetg-1.0.1-jar-with-dependencies.jar
copy target\validatetg-1.0.1-jar-with-dependencies.jar C:\Tools\apache-jmeter-5.6.2\lib\ext
@REM C:\Tools\apache-jmeter-5.6.2\bin\jmeter -t C:\Tools\apache-jmeter-5.6.2\bin\GreatClips.jmx
