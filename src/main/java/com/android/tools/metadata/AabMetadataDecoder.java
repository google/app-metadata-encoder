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

import com.android.zipflinger.ZipRepo;
import com.google.protobuf.ExtensionRegistry;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;

/** Extracts metadata added with the tool from the AAB. */
@Experimental
public final class AabMetadataDecoder {

  /**
   * Extract metadata added by the tool from the AAB file.
   *
   * <p>Extraction involves identifying a file with metadata inside the AAB, parsing Metadata proto.
   *
   * @return Metadata proto.
   */
  public Optional<Metadata> decode(File aabFile, String type) throws AppMetadataEncoderException {
    try (ZipRepo zipRepo = new ZipRepo(aabFile.toPath())) {
      return decode(zipRepo, type);
    } catch (IOException e) {
      throw new AppMetadataEncoderException("Failed to parse metadata from the AAB.", e);
    }
  }

  /**
   * Extract metadata added by the tool from the AAB zip repo.
   *
   * <p>Extraction involves identifying a file with metadata inside the AAB zip repo, parsing
   * Metadata proto.
   *
   * @return Metadata proto.
   */
  public Optional<Metadata> decode(ZipRepo aabZipRepo, String type)
      throws AppMetadataEncoderException {
    String metadataPath =
        String.format(
            "BUNDLE-METADATA/%s/%s/metadata.pb",
            AppMetadataEncoderConstants.METADATA_NAMESPACE, type);

    // Metadata file doesn't exist in AAB
    if (!aabZipRepo.getEntries().containsKey(metadataPath)) {
      return Optional.empty();
    }

    try {
      ByteBuffer byteBuffer = aabZipRepo.getContent(metadataPath);

      return Optional.of(Metadata.parseFrom(byteBuffer, ExtensionRegistry.getEmptyRegistry()));
    } catch (IOException e) {
      throw new AppMetadataEncoderException("Failed to parse metadata from the AAB.", e);
    }
  }
}
