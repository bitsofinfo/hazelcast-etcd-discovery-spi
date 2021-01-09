package org.bitsofinfo.hazelcast.discovery.etcd;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import com.hazelcast.config.ClasspathXmlConfig;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.cluster.Address;

import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.responses.EtcdKeysResponse;

/**
 * Tests for the Hazelcast Etcd Discovery SPI strategy
 * 
 * @author bitsofinfo
 *
 */
public class TestDoNothingRegistrator {
	
	public static final String ETCD_HOST = "localhost";
	public static final int ETCD_PORT = 8500;

	/**
	 * Tests DoNothingRegistrator functionality
	 * 
	 * IMPORTANT:
	 * 
	 * Prior to this test you must register Etcd key paths with 
	 * 	/test-DoNothingRegistrator/[anything-unique-per-node]
	 * 
	 * With values: {"ip":"IP","port":570[1-5],"hostname":"IP"}
	 * 
	 * EXAMPLE manual registry of kv paths for this test 
	 * 
	 * ./etcdctl set /test-DoNothingRegistrator/node1 '{"ip":"127.0.0.1","port":5701,"hostname":"127.0.0.1"}'
	 * ./etcdctl set /test-DoNothingRegistrator/node2 '{"ip":"127.0.0.1","port":5702,"hostname":"127.0.0.1"}'
	 * ./etcdctl set /test-DoNothingRegistrator/node3 '{"ip":"127.0.0.1","port":5703,"hostname":"127.0.0.1"}'
	 * ./etcdctl set /test-DoNothingRegistrator/node4 '{"ip":"127.0.0.1","port":5704,"hostname":"127.0.0.1"}'
	 * ./etcdctl set /test-DoNothingRegistrator/node5 '{"ip":"127.0.0.1","port":5705,"hostname":"127.0.0.1"}'
	 *
	 * 5 nodes/instances, and corresponding ports from 5701-5705 (same ip, your local ip, localhost)
	 * 
	 * This will startup etcd appropriately for this test to work.
	 * 
	 */
	@Test
	public void testDoNothingRegistrator() {

		EtcdClient etcdClient = null;
		
		try {
			
			IMap<Object,Object> testMap1 = null;
			
			int totalInstancesToTest = 5;
			List<HazelcastInstanceMgr> instances = new ArrayList<HazelcastInstanceMgr>();
			
			System.out.println("#################### IS ETCD RUNNING @ " +
					ETCD_HOST+":"+ETCD_PORT+"? IF NOT THIS TEST WILL FAIL! ####################");
			
			
			etcdClient = new EtcdClient(new URI("http://"+ETCD_HOST+":"+ETCD_PORT));

	
			for (int i=0; i<totalInstancesToTest; i++) {
				HazelcastInstanceMgr mgr = new HazelcastInstanceMgr("test-DoNothingRegistrator.xml");
				instances.add(mgr);
				mgr.start();
				
				// create testMap1 in first instance and populate it w/ 10 entries
				if (i == 0) {
					testMap1 = mgr.getInstance().getMap("testMap1");
					for(int j=0; j<10; j++) {
						testMap1.put(j, j);
					}
				}
				
			}
			
			Thread.currentThread().sleep(20000);
			
			// validate we have 5 registered...(regardless of health)
			EtcdKeysResponse dirResp = etcdClient.getDir("test-DoNothingRegistrator")
											.timeout(10, TimeUnit.SECONDS).recursive().send().get();
			Assert.assertEquals(totalInstancesToTest,dirResp.node.nodes.size());

			// get the map via each instance and 
			// validate it ensuring they are all talking to one another
			for (HazelcastInstanceMgr mgr : instances) {
				Assert.assertEquals(10, mgr.getInstance().getMap("testMap1").size());
			}
		
			// shutdown everything
			for (HazelcastInstanceMgr instance : instances) {
				instance.shutdown();
			}
			
		} catch(Exception e) {
			e.printStackTrace();
			Assert.assertFalse("Unexpected error in test: " + e.getMessage(),false);
			
		} finally {
			try {
				etcdClient.close();
			} catch(Exception ignore){}
		}
		
	}
	
	
	private class HazelcastInstanceMgr {
		
		private HazelcastInstance hazelcastInstance = null;
		private Config conf = null;
		
		public HazelcastInstanceMgr(String hazelcastConfigFile) {
			this.conf =new ClasspathXmlConfig(hazelcastConfigFile);
		}
		
		public HazelcastInstance getInstance() {
			return hazelcastInstance;
		}
		
		public void start() {
			hazelcastInstance = Hazelcast.newHazelcastInstance(conf);
		}
		
		public void shutdown() {
			this.hazelcastInstance.shutdown();
		}
		
		public Address getAddress() {
			return this.hazelcastInstance.getCluster().getLocalMember().getAddress();
		}
		
	}
}
