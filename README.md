# hazelcast-etcd-discovery-spi

*IMPORTANT*: This README is relevant for the current *branch* you have selected above.

Provides a Etcd based discovery strategy for Hazlecast 3.6+ enabled applications.
This is an easy to configure plug-and-play Hazlecast DiscoveryStrategy that will optionally register each of your Hazelcast instances with Etcd and enable Hazelcast nodes to dynamically discover one another via Etcd.

* [Status](#status)
* [Releases](#releases)
* [Requirements](#requirements)
* [Maven/Gradle install](#mavengradle)
* [Features](#features)
* [Usage](#usage)
* [Build from source](#building)
* [Unit tests](#tests)
* [Related Info](#related)
* [Todo](#todo)
* [Notes](#notes)
* [Docker info](#docker)

![Diagram of hazelcast etcd discovery strategy](/docs/diag.png "Diagram1")

## <a id="status"></a>Status

This is beta code, tested against Hazelcast 3.6-EA+ through 3.6 stable

## <a id="releases"></a>Releases

* [1.0-RC2](https://github.com/bitsofinfo/hazelcast-etcd-discovery-spi/releases/tag/1.0-RC2): Tested against Hazelcast 3.6-EA through 3.6 stable

* [1.0-RC1](https://github.com/bitsofinfo/hazelcast-etcd-discovery-spi/releases/tag/1.0-RC1): Tested against Hazelcast 3.6-EA and 3.6-RC1

## <a id="requirements"></a>Requirements

* Java 6+
* [Hazelcast 3.6+](https://hazelcast.org/)
* [Etcd](https://github.com/coreos/etcd)

## <a id="mavengradle"></a>Maven/Gradle

To use this discovery strategy in your Maven or Gradle project use the dependency samples below.

### Gradle:

```
repositories {
    jcenter() 
}

dependencies {
	compile 'org.bitsofinfo:hazelcast-etcd-discovery-spi:1.0-RC2'
}
```

### Maven:

```
<dependencies>
    <dependency>
        <groupId>org.bitsofinfo</groupId>
        <artifactId>hazelcast-etcd-discovery-spi</artifactId>
        <version>1.0-RC2</version>
    </dependency>
</dependencies>

<repositories>
    <repository>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
        <id>central</id>
        <name>bintray</name>
        <url>http://jcenter.bintray.com</url>
    </repository>
</repositories>
```

## <a id="features"></a>Features


* Supports two modes of operation:
	* **Read-write**: peer discovery and registration of a hazelcast instance (self registration)
	* **Read-only**: peer discovery only with an manual Etcd key-path setup (no registration by the strategy itself)

* If you don't want to use the built in Etcd registration, just specify the `DoNothingRegistrator` (see below) in your hazelcast discovery-strategy XML config. This will require you to manually create node key-paths against Etcd that defines the hazelcast service; in the format:
	* `/[etcd-service-name]/[hz-instance-id] = {"ip":"xx.xx.xx.xx", "hostname":"my.host", "port":5701}` where `hz-instance-id` can be anything but must be unique. 
	* `etcd-service-name` must match the value of the `<etcd-service-name>` in your hazelcast XML config for this discovery strategy.

* If using self-registration, either `LocalDiscoveryNodeRegistrator` or `ExplicitIpPortRegistrator` which additionally support:
    * Automatic registration of the hazelcast instance with Etcd (under `<etcd-service-name>` key)
    * Control which IP/PORT is published for the hazelcast node in Etcd
    * Configurable discovery delay
    * Automatic Etcd de-registration of instance via ShutdownHook
    
## <a id="usage"></a>Usage

* Ensure your project has the `hazelcast-etcd-discovery-spi` artifact dependency declared in your maven pom or gradle build file as described above. Or build the jar yourself and ensure the jar is in your project's classpath.

* Have Etcd running and available somewhere on your network, start it such as:
```
./etcd
```

* Configure your hazelcast.xml configuration file to use the `EtcdDiscoveryStrategy` (similar to the below): [See hazelcast-etcd-discovery-spi-example.xml](src/main/resources/hazelcast-etcd-discovery-spi-example.xml) for a full example with documentation of options.

* Launch your hazelcast instances, configured with the Etcd discovery-strategy similar to the below: [see ManualRunner.java](src/test/java/org/bitsofinfo/hazelcast/discovery/etcd/ManualRunner.java) example.

```
<network>
  <port auto-increment="true">5701</port>

  <join>
    <multicast enabled="false"/>
    <aws enabled="false"/>
    <tcp-ip enabled="false" />

     <discovery-strategies>
       <discovery-strategy enabled="true"
           class="org.bitsofinfo.hazelcast.discovery.etcd.EtcdDiscoveryStrategy">

         <properties>
              <property name="etcd-uris">http://localhost:4001</property>
              <!-- Optional Username for etcd -->
              <property name="etcd-username">root</property>
              <!-- Optional Password for etcd -->
              <property name="etcd-password">password</property>
		      <property name="etcd-service-name">hz-discovery-test-cluster</property>
              <property name="etcd-registrator">org.bitsofinfo.hazelcast.discovery.etcd.LocalDiscoveryNodeRegistrator</property>
		      <property name="etcd-registrator-config"><![CDATA[
					{
					  "preferPublicAddress":false
					}
              ]]></property>
        </properties>
      </discovery-strategy>
    </discovery-strategies>

  </join>
</network>
```

* Once nodes are joined you can query Etcd to see the auto-registration of hazelcast instances works, the service-id's generated etc

```
$ ./etcdctl ls hz-discovery-test-cluster
/hz-discovery-test-cluster/hz-discovery-test-cluster-192.168.0.208-192.168.0.208-5701

$ ./etcdctl get /hz-discovery-test-cluster/hz-discovery-test-cluster-192.168.0.208-192.168.0.208-5701
{"ip":"192.168.0.208","port":5702,"hostname":"192.168.0.208","registeredAt":"2015.11.25 14:25:59.026 +0000"}
```


## <a id="building"></a>Build from source

* From the root of this project, build a Jar : `./gradlew assemble`

* Include the built jar artifact located at `build/libs/hazelcast-etcd-discovery-spi-[VERSION].jar` in your hazelcast project

* If not already present in your hazelcast application's Maven (pom.xml) or Gradle (build.gradle) dependencies section; ensure that these dependencies are present (versions may vary as appropriate):

```
compile group: 'org.mousio', name: 'etcd4j', version:'2.9.0'
compile group: 'com.google.code.gson', name: 'gson', version:'2.4'
``` 


## <a id="tests"></a>Unit-tests

It may also help you to understand the functionality by checking out and running the unit-tests
located at [src/test/java](src/test/java). **BE SURE TO READ** the comments as some of the tests require
you to setup your local Etcd and edit certain files.

From the command line you can run `TestExplicitIpPortRegistrator` and `TestLocalDiscoveryNodeRegistrator` unit-tests by invoking the `runTests` task using `gradlew` that runs both tests and displays the result on the console.

```
$ ./gradlew runTests
```

The task above will display output indicating the test has started and whether the test has passed or failed.

###### Sample output for passing test:
```
org.bitsofinfo.hazelcast.discovery.etcd.TestExplicitIpPortRegistrator > testExplicitIpPortRegistrator STARTED

org.bitsofinfo.hazelcast.discovery.etcd.TestExplicitIpPortRegistrator > testExplicitIpPortRegistrator PASSED
```

###### Sample output for failing test:
```
org.bitsofinfo.hazelcast.discovery.etcd.TestDoNothingRegistrator > testDoNothingRegistrator STARTED

org.bitsofinfo.hazelcast.discovery.etcd.TestDoNothingRegistrator > testDoNothingRegistrator FAILED
    java.lang.AssertionError at TestDoNothingRegistrator.java:85
```

To run individual unit-test, use the `test.single` argument to provide the unit-test you would like to run. The command below runs the unit test for `TestDoNothingRegistrator`

```
$ ./gradlew test -Dtest.single=TestDoNothingRegistrator
```

##### Note on running `TestDoNothingRegistrator` unit-test
The `TestDoNothingRegistrator` unit-test should be run separately using the `test.single` argument as demonstrated above as it requires you to register a service with your local etcd with 5 nodes/instances. Please **CAREFULLY READ** the comments in `TestDoNothingRegistrator.java` to see how this test should be run.


## <a id="related"></a>Related info

* https://github.com/coreos/etcd
* https://github.com/jurmous/etcd4j
* http://docs.hazelcast.org/docs/3.6/manual/html-single/index.html#discovery-spi
* **Consul** version of this: https://github.com/bitsofinfo/hazelcast-consul-discovery-spi

## <a id="notes"></a> Notes

### <a id="docker"></a>Containerization (Docker) notes

This library may also be helpful to you: [docker-discovery-registrator-consul](https://github.com/bitsofinfo/docker-discovery-registrator-consul)

One of the main drivers for coding this module was for Hazelcast applications that were deployed as Docker containers
that would need to automatically register themselves with Etcd for higher level cluster orchestration of the cluster.

If you are deploying your Hazelcast application as a Docker container, one helpful tip is that you will want to avoid hardwired
configuration in the hazelcast XML config, but rather have your Docker container take startup arguments that would be translated
to `-D` system properties on startup. Convienently Hazelcast can consume these JVM system properties and replace variable placeholders in the XML config. See this documentation for examples: [http://docs.hazelcast.org/docs/3.6/manual/html-single/index.html#using-variables](http://docs.hazelcast.org/docs/3.6/manual/html-single/index.html#using-variables) 

Specifically when using this discovery strategy and Docker, it may be useful for you to use the [ExplicitIpPortRegistrator](src/main/java/org/bitsofinfo/hazelcast/discovery/etcd/ExplicitIpPortRegistrator.java) `EtcdRegistrator` **instead** of the *LocalDiscoveryNodeRegistrator* as the latter relies on hazelcast to determine its IP/PORT and this may end up being the local container IP, and not the Docker host IP, leading to a situation where a unreachable IP/PORT combination is published to Etcd.

**Example:** excerpt from [explicitIpPortRegistrator-example.xml](src/main/resources/explicitIpPortRegistrator-example.xml)
 
Start your hazelcast app such as with the below, this would assume that hazelcast is actually reachable via this configuration
via your Docker host and the port mappings that were specified on `docker run`. (i.e. the IP below would be your docker host/port that is mapped to the actual hazelcast app container and port it exposes for hazelcast). 

This library may also be helpful to you: [docker-discovery-registrator-consul](https://github.com/bitsofinfo/docker-discovery-registrator-consul)

See this [Docker issue for related info](https://github.com/docker/docker/issues/3778) on detecting mapped ports/ip from **within** a container	

`java -jar myHzApp.jar -DregisterWithIpAddress=<dockerHostIp> -DregisterWithPort=<mappedContainerPortOnDockerHost> .... `
 
```
<property name="etcd-registrator-config"><![CDATA[
      {
        "registerWithIpAddress":"${registerWithIpAddress}",
        "registerWithPort":${registerWithPort}
      }
  ]]></property>
```

