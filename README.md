# hazelcast-etcd-discovery-spi


```
$ ./etcdctl ls hz-discovery-test-cluster
/hz-discovery-test-cluster/hz-discovery-test-cluster-192.168.0.208-192.168.0.208-5701

$ ./etcdctl get /hz-discovery-test-cluster/hz-discovery-test-cluster-192.168.0.208-192.168.0.208-5701
{"ip":"192.168.0.208","port":5701,"hostname":"192.168.0.208"}
```