package org.bitsofinfo.hazelcast.discovery.etcd;

import java.util.Map;

import com.hazelcast.nio.Address;
import com.hazelcast.spi.discovery.DiscoveryNode;

/**
 * @see BaseRegistrator
 * 
 * The IP/PORT that it registers with is whatever is specified by
 * in the `etcd-registrator-config` config options `ipAddress` and `port`
 * described below.
 * 
 * Custom options (specified as JSON value for the 'etcd-registrator-config')
 * These are in ADDITION to those commonly defined in BaseRegistrator (base-class)
 * 	
 *   			           
 *	 - registerWithIpAddress: the explicit IP address that this node should be registered
 *                			  with Etcd as its ServiceAddress 
 *                
 *   - registerWithPort: the explicit PORT that this node should be registered
 *            			 with Etcd as its ServiceAddress
 *   			           		 
 * @author bitsofinfo
 *
 */
public class ExplicitIpPortRegistrator extends BaseRegistrator {
	
	// properties that are supported in the JSON value for the 'etcd-registrator-config' config property
	// in ADDITION to those defined in BaseRegistrator
	public static final String CONFIG_PROP_REGISTER_WITH_IP_ADDRESS = "registerWithIpAddress";
	public static final String CONFIG_PROP_REGISTER_WITH_PORT = "registerWithPort";

	@Override 
	public Address determineMyLocalAddress(DiscoveryNode localDiscoveryNode, Map<String, Object> registratorConfig) throws Exception {

		String registerWithIpAddress = (String)registratorConfig.get(CONFIG_PROP_REGISTER_WITH_IP_ADDRESS);
		Integer registerWithPort = ((Double)registratorConfig.get(CONFIG_PROP_REGISTER_WITH_PORT)).intValue();
		
		logger.info("Registrator config properties: " + CONFIG_PROP_REGISTER_WITH_IP_ADDRESS +":"+registerWithIpAddress 
											    + " " +  CONFIG_PROP_REGISTER_WITH_PORT + ":" + registerWithPort +
											    ", will attempt to register with this IP/PORT...");

		
		
		return new Address(registerWithIpAddress, registerWithPort);
	}
}
