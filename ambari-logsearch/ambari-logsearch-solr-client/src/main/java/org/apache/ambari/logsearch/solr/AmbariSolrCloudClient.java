/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ambari.logsearch.solr;

import org.apache.ambari.logsearch.solr.commands.CheckConfigZkCommand;
import org.apache.ambari.logsearch.solr.commands.CreateCollectionCommand;
import org.apache.ambari.logsearch.solr.commands.CreateSaslUsersZkCommand;
import org.apache.ambari.logsearch.solr.commands.CreateShardCommand;
import org.apache.ambari.logsearch.solr.commands.CreateSolrZnodeZkCommand;
import org.apache.ambari.logsearch.solr.commands.DownloadConfigZkCommand;
import org.apache.ambari.logsearch.solr.commands.EnableKerberosPluginSolrZkCommand;
import org.apache.ambari.logsearch.solr.commands.GetShardsCommand;
import org.apache.ambari.logsearch.solr.commands.GetSolrHostsCommand;
import org.apache.ambari.logsearch.solr.commands.ListCollectionCommand;
import org.apache.ambari.logsearch.solr.commands.SecureSolrZNodeZkCommand;
import org.apache.ambari.logsearch.solr.commands.SecureZNodeZkCommand;
import org.apache.ambari.logsearch.solr.commands.SetClusterPropertyZkCommand;
import org.apache.ambari.logsearch.solr.commands.UploadConfigZkCommand;
import org.apache.ambari.logsearch.solr.commands.CheckZnodeZkCommand;
import org.apache.ambari.logsearch.solr.util.ShardUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.cloud.SolrZkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

/**
 * Client for communicate with Solr (and Zookeeper)
 */
public class AmbariSolrCloudClient {

  private static final Logger LOG = LoggerFactory.getLogger(AmbariSolrCloudClient.class);

  private final String zkConnectString;
  private final String collection;
  private final String configSet;
  private final String configDir;
  private final int shards;
  private final int replication;
  private final int retryTimes;
  private final int interval;
  private final CloudSolrClient solrCloudClient;
  private final SolrZkClient solrZkClient;
  private final int maxShardsPerNode;
  private final String routerName;
  private final String routerField;
  private final boolean splitting;
  private final String jaasFile;
  private final String znode;
  private final String saslUsers;
  private final String propName;
  private final String propValue;
  private final boolean secure;

  public AmbariSolrCloudClient(AmbariSolrCloudClientBuilder builder) {
    this.zkConnectString = builder.zkConnectString;
    this.collection = builder.collection;
    this.configSet = builder.configSet;
    this.configDir = builder.configDir;
    this.shards = builder.shards;
    this.replication = builder.replication;
    this.retryTimes = builder.retryTimes;
    this.interval = builder.interval;
    this.jaasFile = builder.jaasFile;
    this.solrCloudClient = builder.solrCloudClient;
    this.solrZkClient = builder.solrZkClient;
    this.maxShardsPerNode = builder.maxShardsPerNode;
    this.routerName = builder.routerName;
    this.routerField = builder.routerField;
    this.splitting = builder.splitting;
    this.znode = builder.znode;
    this.saslUsers = builder.saslUsers;
    this.propName = builder.propName;
    this.propValue = builder.propValue;
    this.secure = builder.secure;
  }

  /**
   * Get Solr collections
   */
  public List<String> listCollections() throws Exception {
    return new ListCollectionCommand(getRetryTimes(), getInterval()).run(this);
  }

  /**
   * Create Solr collection if exists
   */
  public String createCollection() throws Exception {
    List<String> collections = listCollections();
    if (!collections.contains(getCollection())) {
      String collection = new CreateCollectionCommand(getRetryTimes(), getInterval()).run(this);
      LOG.info("Collection '{}' created.", collection);
    } else {
      LOG.info("Collection '{}' already exits.", getCollection());
      if (this.isSplitting()) {
        createShard(null);
      }
    }
    return getCollection();
  }

  /**
   * Set cluster property in clusterprops.json.
   */
  public void setClusterProp() throws Exception {
    LOG.info("Set cluster prop: '{}'", this.getPropName());
    String newPropValue = new SetClusterPropertyZkCommand(getRetryTimes(), getInterval()).run(this);
    LOG.info("Set cluster prop '{}' successfully to '{}'", this.getPropName(), newPropValue);
  }

  /**
   * Add sasl user (only if not exist on the znode).
   */
  public String addSaslUsers() throws Exception {
    LOG.info("Add sasl user to znode: {}", this.saslUsers);
    String newUsers = new CreateSaslUsersZkCommand(getRetryTimes(), getInterval()).run(this);
    if (StringUtils.isNotEmpty(newUsers)) {
      LOG.info("New sasl users added to znode: {}", newUsers);
    }
    return newUsers;
  }

  /**
   * Create a znode only if it does not exist. Return 0 code if it exists.
   */
  public void createZnode() throws Exception {
    boolean znodeExists = isZnodeExists(this.znode);
    if (znodeExists) {
      LOG.info("Znode '{}' already exists.", this.znode);
    } else {
      LOG.info("Znode '{}' does not exist. Creating...", this.znode);
      String newZnode = new CreateSolrZnodeZkCommand(getRetryTimes(), getInterval()).run(this);
      LOG.info("Znode '{}' is created successfully.", newZnode);
    }
  }

  /**
   * Check znode exists or not based on the zookeeper connect string.
   * E.g.: localhost:2181 and znode: /ambari-solr, checks existance of localhost:2181/ambari-solr
   */
  public boolean isZnodeExists(String znode) throws Exception {
    LOG.info("Check '{}' znode exists or not", znode);
    boolean result = new CheckZnodeZkCommand(getRetryTimes(), getInterval(), znode).run(this);
    if (result) {
      LOG.info("'{}' znode exists", znode);
    } else {
      LOG.info("'{}' znode does not exist", znode);
    }
    return result;
  }

  public void setupKerberosPlugin() throws Exception {
    LOG.info("Setup kerberos plugin in security.json");
    new EnableKerberosPluginSolrZkCommand(getRetryTimes(), getInterval()).run(this);
    LOG.info("KerberosPlugin is set in security.json");
  }

  /**
   * Secure solr znode
   */
  public void secureSolrZnode() throws Exception {
    new SecureSolrZNodeZkCommand(getRetryTimes(), getInterval()).run(this);
  }

  /**
   * Secure znode
   */
  public void secureZnode() throws Exception {
    new SecureZNodeZkCommand(getRetryTimes(), getInterval()).run(this);
  }

  /**
   * Upload config set to zookeeper
   */
  public String uploadConfiguration() throws Exception {
    String configSet = new UploadConfigZkCommand(getRetryTimes(), getInterval()).run(this);
    LOG.info("'{}' is uploaded to zookeeper.", configSet);
    return configSet;
  }

  /**
   * Download config set from zookeeper
   */
  public String downloadConfiguration() throws Exception {
    String configDir = new DownloadConfigZkCommand(getRetryTimes(), getInterval()).run(this);
    LOG.info("Config set is download from zookeeper. ({})", configDir);
    return configDir;
  }

  /**
   * Get configuration if exists in zookeeper
   */
  public boolean configurationExists() throws Exception {
    boolean configExits = new CheckConfigZkCommand(getRetryTimes(), getInterval()).run(this);
    if (configExits) {
      LOG.info("Config {} exits", configSet);
    } else {
      LOG.info("Configuration '{}' does not exist", configSet);
    }
    return configExits;
  }

  /**
   * Create shard in collection - create a new one if shard name specified, if
   * not create based on the number of shards logic (with shard_# suffix)
   * 
   * @param shard
   *          name of the created shard
   */
  public Collection<String> createShard(String shard) throws Exception {
    Collection<String> existingShards = getShardNames();
    if (shard != null) {
      new CreateShardCommand(shard, getRetryTimes(), getInterval()).run(this);
      existingShards.add(shard);
    } else {
      List<String> shardList = ShardUtils.generateShardList(getMaxShardsPerNode());
      for (String shardName : shardList) {
        if (!existingShards.contains(shardName)) {
          new CreateShardCommand(shardName, getRetryTimes(), getInterval()).run(this);
          LOG.info("New shard added to collection '{}': {}", getCollection(), shardName);
          existingShards.add(shardName);
        }
      }
    }
    return existingShards;
  }

  /**
   * Get shard names
   */
  public Collection<String> getShardNames() throws Exception {
    Collection<Slice> slices = new GetShardsCommand(getRetryTimes(), getInterval()).run(this);
    return ShardUtils.getShardNamesFromSlices(slices, this.getCollection());
  }

  /**
   * Get Solr Hosts
   */
  public Collection<String> getSolrHosts() throws Exception {
    return new GetSolrHostsCommand(getRetryTimes(), getInterval()).run(this);
  }

  public String getZkConnectString() {
    return zkConnectString;
  }

  public String getCollection() {
    return collection;
  }

  public String getConfigSet() {
    return configSet;
  }

  public String getConfigDir() {
    return configDir;
  }

  public int getShards() {
    return shards;
  }

  public int getReplication() {
    return replication;
  }

  public int getRetryTimes() {
    return retryTimes;
  }

  public int getInterval() {
    return interval;
  }

  public CloudSolrClient getSolrCloudClient() {
    return solrCloudClient;
  }

  public SolrZkClient getSolrZkClient() {
    return solrZkClient;
  }

  public int getMaxShardsPerNode() {
    return maxShardsPerNode;
  }

  public String getRouterName() {
    return routerName;
  }

  public String getRouterField() {
    return routerField;
  }

  public boolean isSplitting() {
    return splitting;
  }

  public String getJaasFile() {
    return jaasFile;
  }

  public String getSaslUsers() {
    return saslUsers;
  }

  public String getZnode() {
    return znode;
  }

  public String getPropName() {
    return propName;
  }

  public String getPropValue() {
    return propValue;
  }

  public boolean isSecure() {
    return secure;
  }
}
