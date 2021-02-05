package org.bitsofinfo.hazelcast.discovery.etcd;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.hazelcast.logging.ILogger;
import com.hazelcast.spi.discovery.DiscoveryNode;

/**
 * Defines an interface for an object who's responsibility
 * it is to register (and deregister) this hazelcast instance with Etcd.
 * 
 * @author bitsofinfo
 *
 */
public interface EtcdRegistrator {
	
	/**
	 * Return the service id as registered with Etcd
	 * 
	 * @return
	 */
	public String getMyServiceId();

	/**
	 * Initialize the registrator
	 * 
	 * @param etcUris
	 * @param etcdUsername
	 * @param etcdPassword
	 * @param etcdServiceName
	 * @param localDiscoveryNode
	 * @param registratorConfig
	 * @param logger
	 * @throws Exception
	 */
	public void init(List<URI> etcUris,
	                 String etcdUsername,
	                 String etcdPassword,
			         String etcdServiceName,
			         DiscoveryNode localDiscoveryNode,
			         Map<String, Object> registratorConfig,
			         ILogger logger) throws Exception;
	
	/**
	 * Register this hazelcast instance as a service node
	 * with Etcd
	 * 
	 * @throws Exception
	 */
	public void register() throws Exception;
	
	/**
	 * Deregister this hazelcast instance as a service node
	 * with Etcd
	 * 
	 * @throws Exception
	 */
	public void deregister() throws Exception;
	
}
