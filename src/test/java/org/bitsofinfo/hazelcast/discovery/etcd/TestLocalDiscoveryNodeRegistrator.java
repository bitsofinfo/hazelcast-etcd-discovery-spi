package org.bitsofinfo.hazelcast.discovery.etcd;

import org.junit.Test;

/**
 * Tests for the Hazelcast Etcd Discovery SPI strategy
 * 
 * @author bitsofinfo
 *
 */
public class TestLocalDiscoveryNodeRegistrator extends RegistratorTestBase {
	
	/**
	 * Tests LocalDiscoveryNodeRegistrator functionality
	 * 
	 */
	@Test
	public void testExplicitIpPortRegistrator() {
		testRegistrator("test-LocalDiscoveryNodeRegistrator.xml","test-LocalDiscoveryNodeRegistrator");
	}

	@Override
	protected void preConstructHazelcast(int instanceNumber) {
		// we do nothing
	}
	
}
