package org.bitsofinfo.hazelcast.discovery.etcd;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hazelcast.logging.ILogger;
import com.hazelcast.nio.Address;
import com.hazelcast.spi.discovery.DiscoveryNode;

import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.responses.EtcdKeysResponse;

/**
 * Use derivatives of this EtcdRegistrator if you want auto-registration
 * of this hazelcast instance in etcd under the key-path
 * 
 * /[etcd-service-name]/[hz-instance-id (generated)] = 
 * 	{ ip:xx.xx.xx.xx, hostname:my.host, port: xx, ...}
 * 
 * The IP/PORT that it registers with is generally dictated by classes
 * that derive from this class and override the determineMyLocalAddress()
 * method.
 * 
 * It will also de-register the service if invoked to do so.
 * 
 * Common custom options (specified as JSON value for the 'etcd-registrator-config')
 * which are available to all derivative classes
 *
 *	 - None at this time
 *   			           		 
 * @author bitsofinfo
 *
 */
public abstract class BaseRegistrator implements EtcdRegistrator {
	
	protected ILogger logger = null;
	protected Address myLocalAddress = null;
	protected String etcdServiceName = null;
	protected String etcdUriStrings = null;
	protected List<URI> etcdUris = new ArrayList<URI>();
	
	private String myServiceId = null;
	private String myEtcdKey = null;
	
	protected abstract Address determineMyLocalAddress(DiscoveryNode localDiscoveryNode,  
													   Map<String, Object> registratorConfig) throws Exception;

	@Override
	public void init(List<URI> etcdUris,
			         String etcdServiceName,
			         DiscoveryNode localDiscoveryNode,
			         Map<String, Object> registratorConfig,
			         ILogger logger) throws Exception {
		
		this.logger = logger;
		this.etcdServiceName = etcdServiceName;
		this.etcdUris = etcdUris;

		try {
			/**
			 * Determine my local address
			 */
			this.myLocalAddress = determineMyLocalAddress(localDiscoveryNode, registratorConfig);
			logger.info("Determined local DiscoveryNode address to use: " + myLocalAddress);
			
		} catch(Exception e) {
			String msg = "Unexpected error in configuring LocalDiscoveryNodeRegistration: " + e.getMessage();
			logger.severe(msg,e);
			throw new Exception(msg,e);
		}
		
	}
	
	@Override
	public String getMyServiceId() {
		return this.myServiceId;
	}

	@Override
	public void register() throws Exception {
		
		EtcdClient etcdClient = null;
		
		try {
			
			// create our client
			etcdClient = EtcdDiscoveryStrategy.getEtcdClient(etcdUris);
			
			// generate service id
			this.myServiceId = this.etcdServiceName + "-" + 
							   this.myLocalAddress.getInetAddress().getHostAddress() +"-" + 
							   this.myLocalAddress.getHost() + "-" + 
							   this.myLocalAddress.getPort();
			
			// etcd key
			this.myEtcdKey = "/"+ this.etcdServiceName + "/" + this.myServiceId;
			
			// our value object
			EtcdHazelcastNode node = new EtcdHazelcastNode(this.myLocalAddress.getInetAddress().getHostAddress(),
								  						   this.myLocalAddress.getPort(),
								  						   this.myLocalAddress.getHost());
			
			// put it
			EtcdKeysResponse response = etcdClient.put(this.myEtcdKey, 
					new GsonBuilder().setDateFormat(EtcdDiscoveryStrategy.DATE_PATTERN).create().toJson(node)).send().get();
			
			this.logger.info("Registered with Etcd["+etcdUriStrings+"] response=" + response.toString() + ", key: " + this.myEtcdKey);
			
		} catch(Exception e) {
			String msg = "Unexpected error in register(serviceId:"+myServiceId+"): " + e.getMessage();
			logger.severe(msg,e);
			throw new Exception(msg,e);
			
		} finally {
			try { etcdClient.close(); } catch(Exception ignore){}
		}
	}
	
	@Override
	public void deregister() throws Exception {
		EtcdClient etcdClient = null;
		
		try {
			// create our client
			etcdClient = EtcdDiscoveryStrategy.getEtcdClient(etcdUris);
			
			// delete
			etcdClient.delete(this.myEtcdKey).send().get();
			
		} catch(Exception e) {
			String msg = "Unexpected error in etcdClient.delete(key:"+this.myEtcdKey+"): " + e.getMessage();
			logger.severe(msg,e);
			throw new Exception(msg,e);
			
		} finally {
			try { etcdClient.close(); } catch(Exception ignore){}
		}
	}


}
