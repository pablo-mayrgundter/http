#!/bin/bash
INSTALL_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )"/../.. >/dev/null 2>&1 && pwd )"
echo $INSTALL_DIR
export CLASSPATH=$INSTALL_DIR
(cd $INSTALL_DIR && [ -f net/http/Server.class ] || javac net/http/Server.java)
java -Dport=8090 -Dlog=true net.http.Server
#java -Dport=8090 -Dnet.http.Server.ssl=true -Djavax.net.ssl.trustStore=/Library/Java/JavaVirtualMachines/jdk-10.jdk/Contents/Home/lib/security/cacerts -Dnet.http.Server.keystore=/Users/pablo/.keystore -Djavax.net.debug=ssl net.http.Server
