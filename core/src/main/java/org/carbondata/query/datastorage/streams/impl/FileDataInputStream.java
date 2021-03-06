/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.carbondata.query.datastorage.streams.impl;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import org.carbondata.common.logging.LogService;
import org.carbondata.common.logging.LogServiceFactory;
import org.carbondata.core.constants.CarbonCommonConstants;
import org.carbondata.core.datastorage.store.compression.ValueCompressionModel;
import org.carbondata.core.datastorage.store.impl.FileFactory;
import org.carbondata.core.metadata.BlockletInfoColumnar;
import org.carbondata.core.reader.CarbonFooterReader;
import org.carbondata.core.util.CarbonMetadataUtil;
import org.carbondata.core.util.ValueCompressionUtil;
import org.carbondata.query.schema.metadata.Pair;

public class FileDataInputStream extends AbstractFileDataInputStream {

  /**
   * Attribute for Carbon LOGGER
   */
  private static final LogService LOGGER =
      LogServiceFactory.getLogService(FileDataInputStream.class.getName());
  /**
   * HIERARCHY_FILE_EXTENSION
   */
  private static final String HIERARCHY_FILE_EXTENSION = ".hierarchy";
  /**
   *
   */
  protected boolean hasFactCount;
  /**
   *
   */
  private String persistenceFileLocation;
  /**
   *
   */
  private String tableName;

  private FileChannel channel;

  private ValueCompressionModel valueCompressionModel;

  private BufferedInputStream in;

  private long fileSize;

  public FileDataInputStream(String filesLocation, int mdkeysize, int msrCount,
      boolean hasFactCount, String persistenceFileLocation, String tableName) {
    super(filesLocation, mdkeysize, msrCount);
    this.hasFactCount = hasFactCount;
    this.filesLocation = filesLocation;
    this.mdkeysize = mdkeysize;
    this.msrCount = msrCount;
    //        this.lastKey = null;
    this.persistenceFileLocation = persistenceFileLocation;
    this.tableName = tableName;
    fileHolder = FileFactory.getFileHolder(FileFactory.getFileType(filesLocation));
  }

  @Override public void initInput() {
    //
    try {
      LOGGER
          .info("Reading from file: " + filesLocation);
      FileInputStream fileInputStream = new FileInputStream(filesLocation);
      channel = fileInputStream.getChannel();
      in = new BufferedInputStream(new FileInputStream(filesLocation));
      // Don't need the following calculation for hierarchy file
      // Hence ignore for hierarchy files
      if (!filesLocation.endsWith(HIERARCHY_FILE_EXTENSION)) {
        fileSize = channel.size() - CarbonCommonConstants.LONG_SIZE_IN_BYTE;
        offSet = fileHolder.readDouble(filesLocation, fileSize);
        //
        valueCompressionModel = ValueCompressionUtil.getValueCompressionModel(
            this.persistenceFileLocation + CarbonCommonConstants.MEASURE_METADATA_FILE_NAME
                + tableName + CarbonCommonConstants.MEASUREMETADATA_FILE_EXT, msrCount);
        this.totalMetaDataLength = (int) (fileSize - offSet);
      }
    } catch (FileNotFoundException f) {
      LOGGER.error("@@@@ Hirarchy file is missing @@@@ : " + filesLocation);
    } catch (IOException e) {
      LOGGER.error("@@@@ Error while reading hirarchy @@@@ : " + filesLocation);
    }
  }

  /**
   * This method will be used to read leaf meta data format of meta data will be
   * <entrycount><keylength><keyoffset><measure1length><measure1offset>
   *
   * @return will return blocklet info which will have all the meta data
   * related to data file
   */
  public List<BlockletInfoColumnar> getBlockletInfoColumnar() {
    //
    List<BlockletInfoColumnar> listOfNodeInfo =
        new ArrayList<BlockletInfoColumnar>(CarbonCommonConstants.CONSTANT_SIZE_TEN);
    CarbonFooterReader metaDataReader = new CarbonFooterReader(filesLocation, offSet);
    try {
      listOfNodeInfo = CarbonMetadataUtil.convertBlockletInfo(metaDataReader.readFooter());
    } catch (IOException e) {
      LOGGER.error("Problem while reading metadata :: " + filesLocation);
    }
    for (BlockletInfoColumnar infoColumnar : listOfNodeInfo) {
      infoColumnar.setFileName(filesLocation);
    }
    // if fact file empty then list size will 0 then it will throw index out of bound exception
    // if memory is less and cube loading failed that time list will be empty so it will throw
    // out of bound exception
    if (listOfNodeInfo.size() > 0) {
      startKey = listOfNodeInfo.get(0).getStartKey();
    }
    return listOfNodeInfo;
  }

  @Override public void closeInput() {
    if (channel != null) {
      //
      try {
        channel.close();
      } catch (IOException e) {
        LOGGER.error(e,
            "Could not close input stream for location : " + filesLocation);
      }
    }
    if (null != fileHolder) {
      fileHolder.finish();
    }
    if (null != in) {
      try {
        in.close();
      } catch (IOException e) {
        LOGGER.error(e,
            "Could not close input stream for location : " + filesLocation);
      }
    }
  }

  @Override public ValueCompressionModel getValueCompressionMode() {
    return valueCompressionModel;
  }

  @Override public Pair getNextHierTuple() {
    // We are adding surrogate key also with mdkey.
    int lineLength = mdkeysize + 4;
    byte[] line = new byte[lineLength];
    byte[] mdkey = new byte[mdkeysize];
    try {
      //
      if (in.read(line, 0, lineLength) != -1) {
        System.arraycopy(line, 0, mdkey, 0, mdkeysize);
        Pair data = new Pair();
        data.setKey(mdkey);
        return data;
      }

    } catch (IOException e) {
      LOGGER.error(e,
          "Problem While Reading the Hier File : ");
    }
    return null;
  }

  public byte[] getStartKey() {
    return startKey;
  }
}
