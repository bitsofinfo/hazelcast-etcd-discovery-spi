package org.bitsofinfo.hazelcast.discovery.etcd;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;

import com.hazelcast.config.ClasspathXmlConfig;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.nio.Address;

import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.responses.EtcdKeysResponse;

/**
 * Base test class for the Hazelcast Etcd Discovery SPI strategies
 * writing registrators
 * 
 * @author bitsofinfo
 *
 */
public abstract class RegistratorTestBase {
	
	public static final String ETCD_HOST = "localhost";
	public static final int ETCD_PORT = 4001;
	
	protected abstract void preConstructHazelcast(int instanceNumber) throws Exception;
	
	protected void testRegistrator(String hazelcastConfigXmlFilename, String serviceName) {
	    testRegistrator(hazelcastConfigXmlFilename, serviceName, null, null);
	}

	protected void testRegistrator(String hazelcastConfigXmlFilename, String serviceName, String username, String password) {
		EtcdClient etcdClient = null;
		
		try {
			
			IMap<Object,Object> testMap1 = null;
			IMap<Object,Object> testMap2 = null;
			
			int totalInstancesToTest = 5;
			List<HazelcastInstanceMgr> instances = new ArrayList<HazelcastInstanceMgr>();
			
			System.out.println("#################### IS ETCD RUNNING @ " +
					ETCD_HOST+":"+ETCD_PORT+"? IF NOT THIS TEST WILL FAIL! ####################");
			
			etcdClient = new EtcdClient(username, password, new URI("http://"+ETCD_HOST+":"+ETCD_PORT));

			for (int i=0; i<totalInstancesToTest; i++) {
				
				preConstructHazelcast(i);
				
				HazelcastInstanceMgr mgr = new HazelcastInstanceMgr(hazelcastConfigXmlFilename);
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
			
			// validate we have 5 registered..
			EtcdKeysResponse dirResp = etcdClient.getDir(serviceName)
											.timeout(10, TimeUnit.SECONDS).recursive().send().get();
			
			Assert.assertEquals(totalInstancesToTest,dirResp.node.nodes.size());

			// get the map via each instance and 
			// validate it ensuring they are all talking to one another
			for (HazelcastInstanceMgr mgr : instances) {
				Assert.assertEquals(10, mgr.getInstance().getMap("testMap1").size());
			}
			
			// pick random instance add new map, verify its everywhere
			Random rand = new Random();
			testMap2 = instances.get(rand.nextInt(instances.size()-1)).getInstance().getMap("testMap2");
			for(int j=0; j<10; j++) {
				testMap2.put(j, j);
			}
			
			for (HazelcastInstanceMgr mgr : instances) {
				Assert.assertEquals(10, mgr.getInstance().getMap("testMap2").size());
			}
		
			
			// shutdown one node
			HazelcastInstanceMgr deadInstance = instances.iterator().next();
			deadInstance.shutdown();
			
			//  total is the SAME (because the shutdown above does not invoke the discovery strategies shutdown hook)
			EtcdKeysResponse dirResp2 = etcdClient.getDir(serviceName)
									.timeout(10, TimeUnit.SECONDS).recursive().send().get();
			Assert.assertEquals((totalInstancesToTest),dirResp2.node.nodes.size());
			
			// pick a random instance, add some entries in map, verify
			instances.get(rand.nextInt(instances.size()-1)).getInstance().getMap("testMap2").put("extra1", "extra1");
			
			// should be 11 now
			for (HazelcastInstanceMgr mgr : instances) {
				if (mgr != deadInstance) {
					Assert.assertEquals((10+1), mgr.getInstance().getMap("testMap2").size());
				}
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
	
	protected String determineIpAddress() throws Exception {
	
		InetAddress addr = InetAddress.getLocalHost();
		String ipAdd = addr.getHostAddress();
		
		return ipAdd;

	}
}
