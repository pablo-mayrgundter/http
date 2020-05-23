#!/bin/bash
INSTALL_DIR=~/http
export CLASSPATH=$INSTALL_DIR
(cd $INSTALL_DIR && [ -f net/http/Server.class ] || javac net/http/Server.java)
java -Dport=8090 -Dnet.http.Server.ssl=true -Djavax.net.ssl.trustStore=/Library/Java/JavaVirtualMachines/jdk-10.jdk/Contents/Home/lib/security/cacerts -Dnet.http.Server.keystore=/Users/pablo/.keystore -Djavax.net.debug=ssl net.http.Server
#java -Dport=8090 -Dnet.http.Server.ssl=true net.http.Server
