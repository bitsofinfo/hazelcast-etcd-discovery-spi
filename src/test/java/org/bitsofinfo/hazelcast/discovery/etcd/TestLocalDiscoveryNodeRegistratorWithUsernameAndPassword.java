package org.bitsofinfo.hazelcast.discovery.etcd;

import org.junit.Test;

/**
 * Tests for the Hazelcast Etcd Discovery SPI strategy
 * 
 * @author bitsofinfo
 *
 */
public class TestLocalDiscoveryNodeRegistratorWithUsernameAndPassword extends RegistratorTestBase {
	
	/**
	 * Tests LocalDiscoveryNodeRegistrator functionality
	 * 
	 */
    @Test
    public void testLocalDiscoveryWithUsernameAndPassword() {
        testRegistrator("test-LocalDiscoveryNodeRegistratorWithUsernameAndPassword.xml","test-LocalDiscoveryNodeRegistratorWithUsernameAndPassword", 
                "root", "password");
    }

	@Override
	protected void preConstructHazelcast(int instanceNumber) throws Exception {
		// we do nothing
	}
	
}
