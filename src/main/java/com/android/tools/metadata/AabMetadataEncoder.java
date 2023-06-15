/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.metadata;

import com.android.zipflinger.BytesSource;
import com.android.zipflinger.ZipArchive;
import com.google.common.flogger.GoogleLogger;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.zip.Deflater;

/** Encodes metadata into an AAB file. */
final class AabMetadataEncoder implements MetadataEncoder {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  /** Encodes metadata into an AAB file. */
  @Override
  public void encodeInPlace(EncodeAppMetadataRequest encodeAppMetadataRequest)
      throws AppMetadataEncoderException {
    logger.atInfo().log("Encoding metadata into AAB.");

    try (ZipArchive archive =
        new ZipArchive(Paths.get(encodeAppMetadataRequest.getInputFile().getPath()))) {
      String metadataPath =
          String.format(
              "BUNDLE-METADATA/%s/%s/metadata.pb",
              AppMetadataEncoderConstants.METADATA_NAMESPACE, encodeAppMetadataRequest.getType());

      if (archive.listEntries().contains(metadataPath)) {
        throw new AppMetadataEncoderException(
            String.format(
                "Metadata of type '%s' already exists.", encodeAppMetadataRequest.getType()));
      }

      // Convert metadata into BytesSource.
      byte[] protoBytes = encodeAppMetadataRequest.getMetadata().toByteArray();
      BytesSource source = new BytesSource(protoBytes, metadataPath, Deflater.BEST_COMPRESSION);

      // Add metadata to output file.
      archive.add(source);
    } catch (IOException e) {
      throw new AppMetadataEncoderException(e);
    }
  }
}
