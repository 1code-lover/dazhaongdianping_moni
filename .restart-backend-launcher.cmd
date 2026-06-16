@echo off
set "MAVEN_OPTS=--add-opens java.base/java.lang.invoke=ALL-UNNAMED"
cd /d "C:\Users\ethan1.zhao\Downloads\dazhaongdianping_moni"
call "C:\Users\ethan1.zhao\maven\apache-maven-3.9.6\bin\mvn.cmd" -Dmaven.test.skip=true spring-boot:run 1>>"C:\Users\ethan1.zhao\Downloads\dazhaongdianping_moni\app.log" 2>>"C:\Users\ethan1.zhao\Downloads\dazhaongdianping_moni\app-error.log"
