# OPC-UA Client UI
[![Build Status](https://travis-ci.org/comtel2000/opc-ua-client.png)](https://travis-ci.org/comtel2000/opc-ua-client) [![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fcomtel2000%2Fopc-ua-client.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2Fcomtel2000%2Fopc-ua-client?ref=badge_shield)
 [![License](https://img.shields.io/badge/license-Apache_2-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)

![screenshot](https://github.com/comtel2000/opc-ua-client/blob/master/doc/opcua_client.png)


[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fcomtel2000%2Fopc-ua-client.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2Fcomtel2000%2Fopc-ua-client?ref=badge_large)

## How to build and run
What is required:

* Latest stable [Oracle JDK 8](http://www.oracle.com/technetwork/java)
* Latest stable [Apache Maven](http://maven.apache.org)

```shell
mvn clean install
java -jar opcua-ui/target/opc-ua-client-jar-with-dependencies.jar
```
## OPC-UA Simulation Server

|                        Endpoint URL                          |         Link               |
| ------------------------------------------------------------ | -------------------------- |
| opc.tcp://opcua.demo-this.com:51210/UA/SampleServer          | http://opclabs.com         |
| opc.tcp://opcuaserver.com:4841/freeopcua/server              | http://opcuaserver.com     |
| opc.tcp://opcuaserver.com:26543                              | http://opcuaserver.com     |
| opc.tcp://opcuaserver.com:48010                              | http://opcuaserver.com     |
| opc.tcp://opcuaserver.com:51210/UA/SampleServer              | http://opcuaserver.com     |
| opc.tcp://uademo.prosysopc.com:53530/OPCUA/SimulationServer  | http://prosysopc.com       |

## Features
- simple security (user/password)
- read, write, browse, monitor
- list import/export of monitored nodes (requires latest milo snapshot)

## Links
- [Eclipse Milo](https://github.com/eclipse/milo)
- [afterburner.fx](https://github.com/AdamBien/afterburner.fx)

## License
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)