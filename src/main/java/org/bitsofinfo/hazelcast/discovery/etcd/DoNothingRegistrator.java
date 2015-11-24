package org.bitsofinfo.hazelcast.discovery.etcd;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.hazelcast.logging.ILogger;
import com.hazelcast.spi.discovery.DiscoveryNode;

/**
 * Use this EtcdRegistrator if you manage the registration
 * of your hazelcast nodes manually/externally via a local
 * Etcd agent or other means. No registration/deregistration
 * will occur if you use this implementation
 * 
 * @author bitsofinfo
 *
 */
public class DoNothingRegistrator implements EtcdRegistrator {

	@Override
	public String getMyServiceId() {
		return null;
	}

	@Override
	public void init(List<URI> etcdUris, String etcdServiceName, DiscoveryNode localDiscoveryNode,
			Map<String, Object> registratorConfig, ILogger logger) {

	}

	@Override
	public void register() throws Exception {
	}

	@Override
	public void deregister() throws Exception {
	}

}
