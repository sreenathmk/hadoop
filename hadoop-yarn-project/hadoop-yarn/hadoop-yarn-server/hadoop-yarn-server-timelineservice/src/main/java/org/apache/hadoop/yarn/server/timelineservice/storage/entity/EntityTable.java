/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.yarn.server.timelineservice.storage.entity;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.regionserver.BloomType;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.server.timelineservice.storage.common.BaseTable;
import org.apache.hadoop.yarn.server.timelineservice.storage.common.TimelineEntitySchemaConstants;

/**
 * The entity table as column families info, config and metrics. Info stores
 * information about a timeline entity object config stores configuration data
 * of a timeline entity object metrics stores the metrics of a timeline entity
 * object
 *
 * Example entity table record:
 *
 * <pre>
 * |--------------------------------------------------------------------|
 * |  Row       | Column Family           | Column Family| Column Family|
 * |  key       | info                    | metrics      | config       |
 * |--------------------------------------------------------------------|
 * | userName!  | id:entityId             | metricId1:   | configKey1:  |
 * | clusterId! |                         | metricValue1 | configValue1 |
 * | flowId!    | type:entityType         | @timestamp1  |              |
 * | flowRunId! |                         |              | configKey2:  |
 * | AppId!     | created_time:           | metriciD1:   | configValue2 |
 * | entityType!| 1392993084018           | metricValue2 |              |
 * | entityId   |                         | @timestamp2  |              |
 * |            | modified_time:          |              |              |
 * |            | 1392995081012           | metricId2:   |              |
 * |            |                         | metricValue1 |              |
 * |            | i!infoKey:              | @timestamp2  |              |
 * |            | infoValue               |              |              |
 * |            |                         |              |              |
 * |            | r!relatesToKey:         |              |              |
 * |            | id3?id4?id5             |              |              |
 * |            |                         |              |              |
 * |            | s!isRelatedToKey        |              |              |
 * |            | id7?id9?id6             |              |              |
 * |            |                         |              |              |
 * |            | e!eventId?eventInfoKey: |              |              |
 * |            | eventInfoValue          |              |              |
 * |            | @timestamp              |              |              |
 * |            |                         |              |              |
 * |            | flowVersion:            |              |              |
 * |            | versionValue            |              |              |
 * |--------------------------------------------------------------------|
 * </pre>
 */
public class EntityTable extends BaseTable<EntityTable> {
  /** entity prefix */
  private static final String PREFIX =
      YarnConfiguration.TIMELINE_SERVICE_PREFIX + ".entity";

  /** config param name that specifies the entity table name */
  public static final String TABLE_NAME_CONF_NAME = PREFIX + ".table.name";

  /**
   * config param name that specifies the TTL for metrics column family in
   * entity table
   */
  private static final String METRICS_TTL_CONF_NAME = PREFIX
      + ".table.metrics.ttl";

  /** default value for entity table name */
  private static final String DEFAULT_TABLE_NAME = "timelineservice.entity";

  /** default TTL is 30 days for metrics timeseries */
  private static final int DEFAULT_METRICS_TTL = 2592000;

  /** default max number of versions */
  private static final int DEFAULT_METRICS_MAX_VERSIONS = 1000;

  private static final Log LOG = LogFactory.getLog(EntityTable.class);

  public EntityTable() {
    super(TABLE_NAME_CONF_NAME, DEFAULT_TABLE_NAME);
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * org.apache.hadoop.yarn.server.timelineservice.storage.BaseTable#createTable
   * (org.apache.hadoop.hbase.client.Admin,
   * org.apache.hadoop.conf.Configuration)
   */
  public void createTable(Admin admin, Configuration hbaseConf)
      throws IOException {

    TableName table = getTableName(hbaseConf);
    if (admin.tableExists(table)) {
      // do not disable / delete existing table
      // similar to the approach taken by map-reduce jobs when
      // output directory exists
      throw new IOException("Table " + table.getNameAsString()
          + " already exists.");
    }

    HTableDescriptor entityTableDescp = new HTableDescriptor(table);
    HColumnDescriptor infoCF =
        new HColumnDescriptor(EntityColumnFamily.INFO.getBytes());
    infoCF.setBloomFilterType(BloomType.ROWCOL);
    entityTableDescp.addFamily(infoCF);

    HColumnDescriptor configCF =
        new HColumnDescriptor(EntityColumnFamily.CONFIGS.getBytes());
    configCF.setBloomFilterType(BloomType.ROWCOL);
    configCF.setBlockCacheEnabled(true);
    entityTableDescp.addFamily(configCF);

    HColumnDescriptor metricsCF =
        new HColumnDescriptor(EntityColumnFamily.METRICS.getBytes());
    entityTableDescp.addFamily(metricsCF);
    metricsCF.setBlockCacheEnabled(true);
    // always keep 1 version (the latest)
    metricsCF.setMinVersions(1);
    metricsCF.setMaxVersions(DEFAULT_METRICS_MAX_VERSIONS);
    metricsCF.setTimeToLive(hbaseConf.getInt(METRICS_TTL_CONF_NAME,
        DEFAULT_METRICS_TTL));
    entityTableDescp
        .setRegionSplitPolicyClassName("org.apache.hadoop.hbase.regionserver.KeyPrefixRegionSplitPolicy");
    entityTableDescp.setValue("KeyPrefixRegionSplitPolicy.prefix_length",
        TimelineEntitySchemaConstants.USERNAME_SPLIT_KEY_PREFIX_LENGTH);
    admin.createTable(entityTableDescp,
        TimelineEntitySchemaConstants.getUsernameSplits());
    LOG.info("Status of table creation for " + table.getNameAsString() + "="
        + admin.tableExists(table));
  }

  /**
   * @param metricsTTL time to live parameter for the metricss in this table.
   * @param hbaseConf configururation in which to set the metrics TTL config
   *          variable.
   */
  public void setMetricsTTL(int metricsTTL, Configuration hbaseConf) {
    hbaseConf.setInt(METRICS_TTL_CONF_NAME, metricsTTL);
  }

}
