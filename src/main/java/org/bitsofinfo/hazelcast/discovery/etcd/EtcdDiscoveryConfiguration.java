package org.bitsofinfo.hazelcast.discovery.etcd;

import com.hazelcast.config.properties.PropertyDefinition;
import com.hazelcast.config.properties.PropertyTypeConverter;
import com.hazelcast.config.properties.SimplePropertyDefinition;

/**
 * Defines constants for our supported Properties
 * 
 * @author bitsofinfo
 *
 */
public class EtcdDiscoveryConfiguration {
	
	public static final PropertyDefinition ETCD_URIS = 
			new SimplePropertyDefinition("etcd-uris", PropertyTypeConverter.STRING);
	
	public static final PropertyDefinition ETCD_SERVICE_NAME = 
			new SimplePropertyDefinition("etcd-service-name", PropertyTypeConverter.STRING);
	
	public static final PropertyDefinition ETCD_REGISTRATOR = 
			new SimplePropertyDefinition("etcd-registrator", true, PropertyTypeConverter.STRING);
	
	public static final PropertyDefinition ETCD_REGISTRATOR_CONFIG = 
			new SimplePropertyDefinition("etcd-registrator-config", true, PropertyTypeConverter.STRING);
	
	public static final PropertyDefinition ETCD_DISCOVERY_DELAY_MS = 
			new SimplePropertyDefinition("etcd-discovery-delay-ms", PropertyTypeConverter.INTEGER);
	

}
