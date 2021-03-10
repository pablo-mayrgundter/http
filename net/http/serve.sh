#!/bin/bash
INSTALL_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )"/../.. >/dev/null 2>&1 && pwd )"
echo $INSTALL_DIR
(cd $INSTALL_DIR && [ -f net/http/Server.class ] || javac net/http/Server.java)
CLASSPATH=$INSTALL_DIR PORT="${PORT:-8080}" java -Dport=$PORT -Dlog=true net.http.Server

# TODO: ssl
# java -Dport=8080 -Dnet.http.Server.ssl=true -Djavax.net.ssl.trustStore=/Library/Java/JavaVirtualMachines/jdk-10.jdk/Contents/Home/lib/security/cacerts -Dnet.http.Server.keystore=/Users/pablo/.keystore -Djavax.net.debug=ssl net.http.Server
