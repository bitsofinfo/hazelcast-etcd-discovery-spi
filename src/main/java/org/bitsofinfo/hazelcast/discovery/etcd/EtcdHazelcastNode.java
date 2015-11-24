package org.bitsofinfo.hazelcast.discovery.etcd;

import java.util.Date;

public class EtcdHazelcastNode {

	public String ip;
	public Integer port;
	public String hostname;
	public Date registeredAt;
	
	public EtcdHazelcastNode(String ip, Integer port, String hostname) {
		super();
		this.ip = ip;
		this.port = port;
		this.hostname = hostname;
		this.registeredAt = new Date();
	}
	
}
