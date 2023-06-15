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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.zipflinger.BytesSource;
import com.android.zipflinger.ZipArchive;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.GoogleLogger;
import com.google.crypto.tink.HybridEncrypt;
import com.google.crypto.tink.hybrid.HybridConfig;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Optional;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/** Encodes metadata into an APK file. */
final class ApkMetadataEncoder implements MetadataEncoder {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private boolean isTinkConfigured = false;

  /** Encodes metadata into an APK file. */
  @Override
  public void encodeInPlace(EncodeAppMetadataRequest request) throws AppMetadataEncoderException {
    encodeInPlace(
        request.getInputFile(),
        request.getMetadata().toByteArray(),
        request.getType(),
        request.getEncryptions());
  }

  private void encodeInPlace(
      File apkFile,
      byte[] metadata,
      String type,
      ImmutableList<EncodeAppMetadataRequest.Encryption> encryptions)
      throws AppMetadataEncoderException {
    logger.atInfo().log("Encoding metadata into APK.");

    try (ZipArchive archive = new ZipArchive(Paths.get(apkFile.getPath()))) {
      ImmutableMap<String, byte[]> encryptionOwnerToPreparedMetadata =
          prepareMetadata(metadata, type, encryptions);
      for (String encryptionOwner : encryptionOwnerToPreparedMetadata.keySet()) {
        String metadataPath =
            String.format(
                "META-INF/%s/%s/%s/metadata.bin",
                AppMetadataEncoderConstants.METADATA_NAMESPACE, type, encryptionOwner);
        if (archive.listEntries().contains(metadataPath)) {
          throw new AppMetadataEncoderException(
              String.format(
                  "Metadata of type '%s' and owner '%s' already exists.", type, encryptionOwner));
        }
        byte[] preparedMetadata =
            Optional.ofNullable(encryptionOwnerToPreparedMetadata.get(encryptionOwner)).get();

        BytesSource source =
            new BytesSource(preparedMetadata, metadataPath, Deflater.NO_COMPRESSION);

        // Add metadata to output file.
        archive.add(source);
      }

    } catch (IOException e) {
      throw new AppMetadataEncoderException(e);
    }
  }

  /** Compressing and encrypting metadata for every provided encryption. */
  private ImmutableMap<String, byte[]> prepareMetadata(
      byte[] metadata, String type, ImmutableList<EncodeAppMetadataRequest.Encryption> encryptions)
      throws AppMetadataEncoderException {
    ImmutableMap.Builder<String, byte[]> encryptionOwnerToPreparedMetadata = ImmutableMap.builder();
    for (EncodeAppMetadataRequest.Encryption encryption : encryptions) {
      byte[] preparedMetadata = encrypt(compress(metadata), encryption, type);
      encryptionOwnerToPreparedMetadata.put(encryption.getEncryptionKeyOwner(), preparedMetadata);
    }
    return encryptionOwnerToPreparedMetadata.buildOrThrow();
  }

  private byte[] compress(byte[] data) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(outputStream)) {
      deflaterOutputStream.write(data);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to compress metadata.", e);
    }

    return outputStream.toByteArray();
  }

  private byte[] encrypt(
      byte[] data, EncodeAppMetadataRequest.Encryption encryption, String encryptionContext)
      throws AppMetadataEncoderException {
    configureTink();

    try {
      HybridEncrypt hybridEncrypt = encryption.getEncrypter();
      return hybridEncrypt.encrypt(data, encryptionContext.getBytes(UTF_8));
    } catch (GeneralSecurityException e) {
      throw new AppMetadataEncoderException("Failed to encrypt metadata.", e);
    }
  }

  private void configureTink() throws AppMetadataEncoderException {
    if (isTinkConfigured) {
      return;
    }

    try {
      HybridConfig.register();
      isTinkConfigured = true;
    } catch (GeneralSecurityException e) {
      throw new AppMetadataEncoderException("Failed to initialize Tink for encoding.", e);
    }
  }
}
