package org.bitsofinfo.hazelcast.discovery.etcd;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import com.hazelcast.config.properties.PropertyDefinition;
import com.hazelcast.logging.ILogger;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.discovery.DiscoveryStrategy;
import com.hazelcast.spi.discovery.DiscoveryStrategyFactory;

public class EtcdDiscoveryStrategyFactory implements DiscoveryStrategyFactory {

	private static final Collection<PropertyDefinition> PROPERTIES =
			Arrays.asList(new PropertyDefinition[]{
						EtcdDiscoveryConfiguration.ETCD_URIS,
						EtcdDiscoveryConfiguration.ETCD_USERNAME,
						EtcdDiscoveryConfiguration.ETCD_PASSWORD,
						EtcdDiscoveryConfiguration.ETCD_SERVICE_NAME,
						EtcdDiscoveryConfiguration.ETCD_REGISTRATOR,
						EtcdDiscoveryConfiguration.ETCD_REGISTRATOR_CONFIG,
						EtcdDiscoveryConfiguration.ETCD_DISCOVERY_DELAY_MS
					});

	public Class<? extends DiscoveryStrategy> getDiscoveryStrategyType() {
		// Returns the actual class type of the DiscoveryStrategy
		// implementation, to match it against the configuration
		return EtcdDiscoveryStrategy.class;
	}

	public Collection<PropertyDefinition> getConfigurationProperties() {
		return PROPERTIES;
	}

	public DiscoveryStrategy newDiscoveryStrategy(DiscoveryNode discoveryNode,
												  ILogger logger,
												  Map<String, Comparable> properties ) {

		return new EtcdDiscoveryStrategy( discoveryNode, logger, properties );                                      
	}   

}
