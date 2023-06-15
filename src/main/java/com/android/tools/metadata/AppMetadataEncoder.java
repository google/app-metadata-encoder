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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.io.Files.getFileExtension;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.GoogleLogger;
import java.io.File;
import java.util.HashSet;

/** Encodes metadata into an APK or AAB file. */
public final class AppMetadataEncoder implements MetadataEncoder {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private final ApkMetadataEncoder apkMetadataEncoder = new ApkMetadataEncoder();
  private final AabMetadataEncoder aabMetadataEncoder = new AabMetadataEncoder();

  /** Encodes metadata into an APK or AAB file. */
  @Override
  public void encodeInPlace(EncodeAppMetadataRequest request) throws AppMetadataEncoderException {
    validateRequest(
        request.getInputFile(), request.getType(), request.getMetadata(), request.getEncryptions());

    encodeInPlaceWithoutValidations(request);
  }

  void encodeInPlaceWithoutValidations(EncodeAppMetadataRequest request)
      throws AppMetadataEncoderException {
    // Set app metadata encoder version
    request =
        request.toBuilder()
            .setMetadata(
                request.getMetadata().toBuilder()
                    .setAppMetadataEncoderVersion(AppMetadataEncoderConstants.CURRENT_VERSION)
                    .build())
            .build();

    getEncoder(request).encodeInPlace(request);

    logger.atInfo().log(
        "Metadata successfully encoded at: %s.", request.getInputFile().getAbsolutePath());
  }

  void validateRequest(
      File inputFile,
      String type,
      Metadata metadata,
      ImmutableList<EncodeAppMetadataRequest.Encryption> encryptions) {
    logger.atInfo().log("Validating request.");
    String inputFileName = inputFile.getName();

    checkArgument(
        inputFile.exists(), "Input file does not exist at: %s.", inputFile.getAbsolutePath());
    checkArgument(
        inputFileName.endsWith(".apk") || inputFileName.endsWith(".aab"),
        "Input file '%s' must be either an APK or AAB.",
        inputFileName);
    checkArgument(!type.isEmpty(), "'type' must be provided a value.");
    checkMetadataForDuplicateKeys(metadata);

    // Validate APK required values
    if (inputFileName.endsWith(".apk")) {
      checkArgument(!encryptions.isEmpty(), "At least one Encryption object must be provided.");

      for (EncodeAppMetadataRequest.Encryption encryption : encryptions) {
        checkArgument(
            !encryption.getEncryptionKeyOwner().isEmpty(),
            "'encryptionKeyOwner' must be provided a value.");
        if (!encryption.getEncrypterOptional().isPresent()) {
          checkArgument(
              encryption.getEncryptionKeyOptional().isPresent()
                  && encryption.getEncryptionKey().remaining() != 0,
              "Encryption key with encryption key owner of '%s' does not exist.",
              encryption.getEncryptionKeyOwner());
        }
      }
    }
  }

  private void checkMetadataForDuplicateKeys(Metadata metadata) {
    HashSet<String> keys = new HashSet<>();

    for (MetadataEntry entry : metadata.getMetadataEntriesList()) {
      String key = entry.getKey();
      checkArgument(!keys.contains(key), "Duplicate key '%s' in metadata is found.", key);

      keys.add(key);
    }
  }

  private MetadataEncoder getEncoder(EncodeAppMetadataRequest request) {
    MetadataEncoder encoder;

    switch (getFileExtension(request.getInputFile().getName())) {
      case "apk":
        encoder = apkMetadataEncoder;
        break;
      case "aab":
        encoder = aabMetadataEncoder;
        break;
      default:
        throw new AssertionError("Unreachable");
    }

    return encoder;
  }
}
