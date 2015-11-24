package org.bitsofinfo.hazelcast.discovery.etcd;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hazelcast.logging.ILogger;
import com.hazelcast.nio.Address;
import com.hazelcast.spi.discovery.AbstractDiscoveryStrategy;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.discovery.SimpleDiscoveryNode;

import io.netty.handler.ssl.SslContext;
import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.responses.EtcdKeysResponse;
import mousio.etcd4j.responses.EtcdKeysResponse.EtcdNode;

/**
 * DiscoveryStrategy for Etcd
 * 
 * @author bitsofinfo
 *
 */
public class EtcdDiscoveryStrategy extends AbstractDiscoveryStrategy implements Runnable {

	// how we connect to etcd
	private String etcdUrisString;  
	private List<URI> etcdUris = new ArrayList<URI>();
	
	// service name we will key under
	private String etcdServiceName = null;
	
	// How we register with Etcd
	private EtcdRegistrator registrator = null;
	
	
	/**
	 * Constructor
	 * 
	 * @param localDiscoveryNode
	 * @param logger
	 * @param properties
	 */
	public EtcdDiscoveryStrategy(DiscoveryNode localDiscoveryNode, ILogger logger, Map<String, Comparable> properties ) {

		super( logger, properties );
		
		// get basic properites for the strategy
		this.etcdUrisString = getOrDefault("etcd-uris",  EtcdDiscoveryConfiguration.ETCD_URIS, "http://localhost:4001");
		this.etcdServiceName = getOrDefault("etcd-service-name",  EtcdDiscoveryConfiguration.ETCD_SERVICE_NAME, "");		
		long discoveryDelayMS = getOrDefault("etcd-discovery-delay-ms",  EtcdDiscoveryConfiguration.ETCD_DISCOVERY_DELAY_MS, 30000);
		
		/**
		 * Parse etcd URI strings
		 */
		for (String rawUri : etcdUrisString.split(",")) {
			try {
				etcdUris.add(URI.create(rawUri.trim()));
			} catch(Exception e) {
				logger.severe("Error parsing etcd-uris: " + rawUri + " " + e.getMessage(),e);
			}
		}
		
		
		// our EtcdRegistrator default is DoNothingRegistrator
		String registratorClassName = getOrDefault("etcd-registrator",  
													EtcdDiscoveryConfiguration.ETCD_REGISTRATOR, 
													DoNothingRegistrator.class.getCanonicalName());
		
		// this is optional, custom properties to configure a registrator
		// @see the EtcdRegistrator for a description of supported options 
		String registratorConfigJSON = getOrDefault("etcd-registrator-config",  
													EtcdDiscoveryConfiguration.ETCD_REGISTRATOR_CONFIG, 
													null);
		
		// if JSON config is present attempt to parse it into a map
		Map<String,Object> registratorConfig = null;
		if (registratorConfigJSON != null && !registratorConfigJSON.trim().isEmpty()) {
			try {
				Type type = new TypeToken<Map<String, Object>>(){}.getType();
				registratorConfig = new Gson().fromJson(registratorConfigJSON, type);
			    
			} catch(Exception e) {
				logger.severe("Unexpected error parsing 'etcd-registrator-config' JSON: " + 
							registratorConfigJSON + " error="+e.getMessage(),e);
			}
		}

		
		// Ok, now construct our registrator and register with Etcd
		try {
			registrator = (EtcdRegistrator)Class.forName(registratorClassName).newInstance();
			
			logger.info("Using EtcdRegistrator: " + registratorClassName);
			
			registrator.init(etcdUris, etcdServiceName, localDiscoveryNode, registratorConfig, logger);;
			registrator.register();
			
		} catch(Exception e) {
			logger.severe("Unexpected error attempting to init() EtcdRegistrator and register(): " +e.getMessage(),e);
		}
		
		// register our shutdown hook for deregisteration on shutdown...
		Thread shutdownThread = new Thread(this);
		Runtime.getRuntime().addShutdownHook(shutdownThread);
		
		// finally sleep a bit according to the configured discoveryDelayMS
		try {
			logger.info("Registered our instance w/ Etcd OK.. delaying Hazelcast discovery, sleeping: " + discoveryDelayMS + "ms");
			Thread.sleep(discoveryDelayMS);
		} catch(Exception e) {
			logger.severe("Unexpected error sleeping prior to discovery: " + e.getMessage(),e);
		}
									
	}      
	
	protected static EtcdClient getEtcdClient(List<URI> etcdUris) throws Exception {
		// build our clients 
		
		if (etcdUris.iterator().next().toString().toLowerCase().indexOf("https") != -1) {
			SslContext sslContext = SslContext.newClientContext();
			return new EtcdClient(sslContext,etcdUris.toArray(new URI[]{}));
		} else {
			return new EtcdClient(etcdUris.toArray(new URI[]{}));
		}
	}

	@Override
	public Iterable<DiscoveryNode> discoverNodes() {
		
		List<DiscoveryNode> toReturn = new ArrayList<DiscoveryNode>();
		
		EtcdClient etcdClient = null;
		
		try {
			etcdClient = getEtcdClient(this.etcdUris);
			
			Gson gson = new Gson();
			
			// discover all nodes under key /[etcdServiceName
			EtcdKeysResponse dirResp = etcdClient.getDir(this.etcdServiceName)
											.timeout(10, TimeUnit.SECONDS).recursive().send().get();

            if(dirResp.node != null) {
            	for (EtcdNode node : dirResp.node.nodes) {
            		if (node.value != null && !node.value.trim().isEmpty()) {
            			try {
            				EtcdHazelcastNode etcdNode = (EtcdHazelcastNode)gson.fromJson(node.value, EtcdHazelcastNode.class);
            				toReturn.add(new SimpleDiscoveryNode(new Address(etcdNode.ip,etcdNode.port)));
									
            			} catch(Exception e) {
            				getLogger().severe("Skipping node... error parsing etcd node["+node.key+"] "
            						+ "value: " + node.value + " to EtcdHazelcastNode..." + e.getMessage(),e);
            			}
            		}
            	}
            }
			
		} catch(Exception e) {
			getLogger().severe("discoverNodes() unexpected error: " + e.getMessage(),e);
			
		} finally {
			try { etcdClient.close(); } catch(Exception ignore){}
		}
		
		return toReturn;
	}

	@Override
	public void run() {
		try {
			if (registrator != null) {
				getLogger().info("Deregistering myself from Etcd: " + this.registrator.getMyServiceId());
				registrator.deregister();
			}
		} catch(Throwable e) {
			this.getLogger().severe("Unexpected error in EtcdRegistrator.deregister(): " + e.getMessage(),e);
		}
		
	}
}
