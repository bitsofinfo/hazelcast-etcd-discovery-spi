package org.bitsofinfo.hazelcast.discovery.etcd;

import com.hazelcast.config.ClasspathXmlConfig;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

/**
 * Simple class for manually spawning hz instances and watching what happens
 * as they discover one another
 * 
 * @author bitsofinfo
 *
 */
public class ManualRunner {

	public static void main(String[] args) throws Exception {
		
		Config conf =new ClasspathXmlConfig("hazelcast-etcd-discovery-spi-example.xml");

		HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance(conf);
		
		Thread.currentThread().sleep(30000);
		
		System.exit(0);
	}
}
