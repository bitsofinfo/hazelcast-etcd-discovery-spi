package org.bitsofinfo.hazelcast.discovery.etcd;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.naming.ConfigurationException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.bind.DatatypeConverter;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hazelcast.logging.ILogger;
import com.hazelcast.nio.Address;
import com.hazelcast.spi.discovery.AbstractDiscoveryStrategy;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.discovery.SimpleDiscoveryNode;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
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
	
	public static final String DATE_PATTERN = "yyyy.MM.dd HH:mm:ss.SSS Z";
	
	private static final String TEMPORARY_KEY_PASSWORD = "changeit";
	
	//custom TLS certs and key
	static private String trustedCertsLocation; 
	static private String clientKeyLocation;
	static private String clientCertLocation;

	// how we connect to etcd
	private String etcdUrisString;  
	private List<URI> etcdUris = new ArrayList<URI>();
	private String etcdUsername;
	private String etcdPassword;
	
	// service name we will key under
	private String etcdServiceName = null;
	
	// How we register with Etcd
	private EtcdRegistrator registrator = null;
	
	static {
		Security.addProvider(new BouncyCastleProvider());
	}
	
	
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
		this.etcdUsername = getOrDefault("etcd-username", EtcdDiscoveryConfiguration.ETCD_USERNAME, null);
		this.etcdPassword = getOrDefault("etcd-password", EtcdDiscoveryConfiguration.ETCD_PASSWORD, null);
		this.etcdServiceName = getOrDefault("etcd-service-name",  EtcdDiscoveryConfiguration.ETCD_SERVICE_NAME, "");	
		
		clientCertLocation = getOrDefault("etcd-client-cert-location",  EtcdDiscoveryConfiguration.ETCD_CLIENT_CERT_LOCATION, "");
		clientKeyLocation = getOrDefault("etcd-client-key-location",  EtcdDiscoveryConfiguration.ETCD_CLIENT_KEY_LOCATION, "");
		trustedCertsLocation = getOrDefault("etcd-trusted-cert-location",  EtcdDiscoveryConfiguration.ETCD_TRUSTED_CERT_LOCATION, "");
		
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
			
			registrator.init(etcdUris, etcdUsername, etcdPassword, etcdServiceName, localDiscoveryNode, registratorConfig, logger);;
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
	
	protected static EtcdClient getEtcdClient(List<URI> etcdUris, String username, String password) throws Exception {
        // build our clients 
				
        if (etcdUris.iterator().next().toString().toLowerCase().indexOf("https") != -1) {
            SslContextBuilder builder = SslContextBuilder.forClient();
            
            //create custom SSLContext when certs and key are provided, if not there this will return NULL	
            KeyStore keyStore = readCertsAndCreateKeyStore(clientCertLocation, clientKeyLocation, trustedCertsLocation);
            	
            if(keyStore != null) {
        		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        		kmf.init(keyStore, TEMPORARY_KEY_PASSWORD.toCharArray());
        		TrustManagerFactory tmfactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        		tmfactory.init(keyStore);
            	
            	builder.keyManager(kmf);
            	builder.trustManager(tmfactory);
            }
            SslContext sslContext = builder.build();
            return new EtcdClient(sslContext, username, password, etcdUris.toArray(new URI[]{}));
        } else {
            return new EtcdClient(username, password, etcdUris.toArray(new URI[]{}));
        }
    }

	@Override
	public Iterable<DiscoveryNode> discoverNodes() {
		
		List<DiscoveryNode> toReturn = new ArrayList<DiscoveryNode>();
		
		EtcdClient etcdClient = null;
		
		try {
			etcdClient = getEtcdClient(this.etcdUris, this.etcdUsername, this.etcdPassword);
			
			Gson gson = new GsonBuilder().setDateFormat(DATE_PATTERN).create();
			
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

	private static KeyStore readCertsAndCreateKeyStore(String clientCertLocation, String clientKeyLocation, String trustedCertsLocation) throws ConfigurationException {
		
		if(clientCertLocation == null || clientCertLocation.isEmpty() || clientKeyLocation == null || clientKeyLocation.isEmpty()) {
			return null;
		}
		
		String strTrustedCertsData = null;
		String strClientKeyData = null;
		String strClientCertData = null;
		try (FileInputStream fisTrust = new FileInputStream(trustedCertsLocation)) {
			strTrustedCertsData = new String(IOUtils.toByteArray(fisTrust));
		} catch (Exception e) {
			strTrustedCertsData = null;
		}

		try (FileInputStream fisKey = new FileInputStream(clientKeyLocation)) {
			strClientKeyData = new String(IOUtils.toByteArray(fisKey));
		} catch (Exception e) {
			strClientKeyData = null;
		}

		try (FileInputStream fisCert = new FileInputStream(clientCertLocation)) {
			strClientCertData = new String(IOUtils.toByteArray(fisCert));
		} catch (Exception e) {
			strClientCertData = null;
		}

		if (strClientCertData == null && strClientKeyData == null && strTrustedCertsData == null) {
			return null; // no SSL
		}
    	
    	return getKeyStore(strTrustedCertsData, strClientKeyData, strClientCertData);
	}
	
	private static KeyStore getKeyStore(String trustedCerts, String clientKey, String clientCert)
			throws ConfigurationException {
		try {

			KeyStore keyStore = KeyStore.getInstance("JKS");
			keyStore.load(null, null);

			PrivateKey privateKey = loadPrivateKey(clientKey);

			if (trustedCerts == null) {
				List<Certificate> chainedCerts = loadChainedCertificate(clientCert);
				for (int i = 1; i < chainedCerts.size(); i++) {
					keyStore.setCertificateEntry("ca-cert-" + i, chainedCerts.get(i));
				}
				keyStore.setCertificateEntry("client-cert", chainedCerts.get(0));
				keyStore.setKeyEntry("client-key", privateKey, TEMPORARY_KEY_PASSWORD.toCharArray(),
						new Certificate[] { chainedCerts.get(0) });
			} else {
				Certificate clientCertificate = loadCertificate(clientCert);
				List<Certificate> caCertificates = loadChainedCertificate(trustedCerts);
				for (int i = 0; i < caCertificates.size(); i++) {
					keyStore.setCertificateEntry("ca-cert-" + i, caCertificates.get(i));
				}
				keyStore.setCertificateEntry("client-cert", clientCertificate);
				keyStore.setKeyEntry("client-key", privateKey, TEMPORARY_KEY_PASSWORD.toCharArray(),
						new Certificate[] { clientCertificate });
			}
			return keyStore;
		} catch (GeneralSecurityException | IOException e) {
			ConfigurationException ex = new ConfigurationException("Cannot build keystore");
			ex.setRootCause(e);
			throw ex;
		}
	}

	
	private static List<Certificate> loadChainedCertificate(String clientCerts) throws GeneralSecurityException {
		String beginDelimiter = "-----BEGIN CERTIFICATE-----";
		String endDelimiter = "-----END CERTIFICATE-----";
		CertificateFactory certificateFactory = CertificateFactory.getInstance("X509");

		String[] tokens = clientCerts.split(beginDelimiter);

		List<Certificate> result = new ArrayList<>();
		for (int i = 1; i < tokens.length; i++) {
			String[] tokens2 = tokens[i].split(endDelimiter);
			result.add(certificateFactory
					.generateCertificate(new ByteArrayInputStream(DatatypeConverter.parseBase64Binary(tokens2[0]))));

		}
		return result;
	}

	private static Certificate loadCertificate(String certificatePem) throws IOException, GeneralSecurityException {
		final byte[] content = parseDERFromPEM(certificatePem, "-----BEGIN CERTIFICATE-----",
				"-----END CERTIFICATE-----");
		CertificateFactory certificateFactory = CertificateFactory.getInstance("X509");
		return certificateFactory.generateCertificate(new ByteArrayInputStream(content));
	}

	private static PrivateKey loadPrivateKey(String privateKeyPem) throws IOException, GeneralSecurityException {
		// PKCS#8 format
	    final String PEM_PRIVATE_START = "-----BEGIN PRIVATE KEY-----";
	    final String PEM_PRIVATE_END = "-----END PRIVATE KEY-----";

		// PKCS#1 format
		final String PEM_RSA_PRIVATE_START = "-----BEGIN RSA PRIVATE KEY-----";
		// final String PEM_RSA_PRIVATE_END = "-----END RSA PRIVATE KEY-----";

	    if (privateKeyPem.contains(PEM_PRIVATE_START)) { // PKCS#8 format
	        privateKeyPem = privateKeyPem.replace(PEM_PRIVATE_START, "").replace(PEM_PRIVATE_END, "");
	        privateKeyPem = privateKeyPem.replaceAll("\\s", "");

	        byte[] pkcs8EncodedKey = Base64.getDecoder().decode(privateKeyPem);

	        KeyFactory factory = KeyFactory.getInstance("RSA");
	        return factory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8EncodedKey));
		} else if (privateKeyPem.contains(PEM_RSA_PRIVATE_START)) { // PKCS#1 format
			PEMParser pemParser = new PEMParser(new StringReader(privateKeyPem));
			Object object = pemParser.readObject();
			pemParser.close();
			JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
			KeyPair kp = converter.getKeyPair((PEMKeyPair) object);

			return kp.getPrivate();
		}

		throw new GeneralSecurityException("Not supported format of a private key");
	}

	private static byte[] parseDERFromPEM(String pem, String beginDelimiter, String endDelimiter) {
		String[] tokens = pem.split(beginDelimiter);
		tokens = tokens[1].split(endDelimiter);
		return DatatypeConverter.parseBase64Binary(tokens[0]);
	}

}
