# hazelcast-etcd-discovery-spi

Provides a Etcd based discovery strategy for Hazlecast 3.6-EA+ enabled applications.
This is an easy to configure plug-and-play Hazlecast DiscoveryStrategy that will optionally register each of your Hazelcast instances with Etcd and enable Hazelcast nodes to dynamically discover one another via Etcd.

* [Status](#status)
* [Requirements](#requirements)
* [Features](#features)
* [Build/Usage](#usage)
* [Unit tests](#tests)
* [Related Info](#related)
* [Notes](#notes)
* [Docker info](#docker)


## <a id="status"></a>Status

This is beta code.

## <a id="requirements"></a>Requirements

* Java 6+
* [Hazelcast 3.6-EA+](https://hazelcast.org/)
* [Etcd](https://github.com/coreos/etcd)

## <a id="features"></a>Features


* Supports two modes of operation:
	* **Read-write**: peer discovery and registration of a hazelcast instance (self registration)
	* **Read-only**: peer discovery only with an manual Etcd key-path setup (no registration by the strategy itself)

* If you don't want to use the built in Etcd registration, just specify the `DoNothingRegistrator` (see below) in your hazelcast discovery-strategy XML config. This will require you to manually create node key-paths against Etcd defines the hazelcast service; in the format
`/[etcd-service-name]/[hz-instance-id] = { ip:xx.xx.xx.xx, hostname:my.host, port: xx, ...}` where `hz-instance-id` can be anything but should be unique. `etcd-service-name` must match the value of the `<etcd-service-name>` in your hazelcast XML config for this discovery strategy.

* If using self-registration, either `LocalDiscoveryNodeRegistrator` or `ExplicitIpPortRegistrator` which additionally support:
    * Automatic registration of the hazelcast instance with Etcd
    * Control which IP/PORT is published for the hazelcast node in Etcd
    * Configurable discovery delay
    * Automatic Etcd de-registration of instance via ShutdownHook

## <a id="usage"></a>Build & Usage

* Have Etcd running and available somewhere on your network, start it such as:
```
./etcd
```
* From the root of this project, build a Jar : `./gradlew assemble`

* Include the built jar artifact located at `build/libs/hazelcast-etcd-discovery-spi-1.0.0.jar` in your hazelcast project

* If not already present in your hazelcast application's Maven (pom.xml) or Gradle (build.gradle) dependencies section; ensure that these dependencies are present (versions may vary as appropriate):

```
compile group: 'org.mousio', name: 'etcd4j', version:'2.7.0'
compile group: 'com.google.code.gson', name: 'gson', version:'2.4'
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
{"ip":"192.168.0.208","port":5701,"hostname":"192.168.0.208"}
```


## <a id="tests"></a>Unit-tests

It may also help you to understand the functionality by checking out and running the unit-tests
located at [src/test/java](src/test/java). Be sure to read the comments as some of the tests require
you to setup your local Etcd and edit certain files.

## <a id="related"></a>Related info

* https://github.com/coreos/etcd
* https://github.com/jurmous/etcd4j
* http://docs.hazelcast.org/docs/3.6-EA/manual/html-single/index.html#discovery-spi

## <a id="notes"></a> Notes

### <a id="docker"></a>Containerization (Docker) notes

One of the main drivers for coding this module was for Hazelcast applications that were deployed as Docker containers
that would need to automatically register themselves with Etcd for higher level cluster orchestration of the cluster.

If you are deploying your Hazelcast application as a Docker container, one helpful tip is that you will want to avoid hardwired
configuration in the hazelcast XML config, but rather have your Docker container take startup arguments that would be translated
to `-D` system properties on startup. Convienently Hazelcast can consume these JVM system properties and replace variable placeholders in the XML config. See this documentation for examples: [http://docs.hazelcast.org/docs/3.6-EA/manual/html-single/index.html#using-variables](http://docs.hazelcast.org/docs/3.6-EA/manual/html-single/index.html#using-variables) 

Specifically when using this discovery strategy and Docker, it may be useful for you to use the [ExplicitIpPortRegistrator](src/main/java/org/bitsofinfo/hazelcast/discovery/etcd/ExplicitIpPortRegistrator.java) `EtcdRegistrator` **instead** of the *LocalDiscoveryNodeRegistrator* as the latter relies on hazelcast to determine its IP/PORT and this may end up being the local container IP, and not the Docker host IP, leading to a situation where a unreachable IP/PORT combination is published to Etcd.

**Example:** excerpt from [explicitIpPortRegistrator-example.xml](src/main/resources/explicitIpPortRegistrator-example.xml)
 
Start your hazelcast app such as with the below, this would assume that hazelcast is actually reachable via this configuration
via your Docker host and the port mappings that were specified on `docker run`. (i.e. the IP below would be your docker host/port that is mapped to the actual hazelcast app container and port it exposes for hazelcast). 

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

