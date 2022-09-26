#!/bin/bash

#export CLASSPATH=./bin:../../PeerNet/bin:../../PeerNet/lib/jep-2.3.0.jar:../../PeerNet/lib/djep-1.0.0.jar
export CLASSPATH=./out/production/blocknet:./lib/jep-2.3.0.jar:./lib/djep-1.0.0.jar:./lib/peernet.jar

java -Xmx2g -ea peernet.Simulator $*
#java -ea peernet.Simulator $*
