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

import com.android.zipflinger.ZipRepo;
import com.google.crypto.tink.HybridDecrypt;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.hybrid.HybridConfig;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Optional;
import java.util.zip.InflaterOutputStream;

/** Extracts metadata added with the tool from the APK. */
@Experimental
public final class ApkMetadataDecoder {

  private boolean isTinkConfigured = false;

  /**
   * Extract metadata added by the tool from the APK file.
   *
   * <p>Extraction involves identifying a file with metadata inside the APK, decrypting the bytes in
   * the file using the provided `KeysetHandle`s, decompressing using ZIP inflater, parsing Metadata
   * proto.
   *
   * @return Metadata proto decoded from the APK.
   */
  public Optional<Metadata> decode(
      File apkFile, String type, String encryptionKeyOwner, KeysetHandle decryptionKeysetHandle)
      throws AppMetadataEncoderException {
      configureTink();

    try (ZipRepo zipRepo = new ZipRepo(apkFile.toPath())) {
      HybridDecrypt hybridDecrypt = decryptionKeysetHandle.getPrimitive(HybridDecrypt.class);
      return decode(zipRepo, type, encryptionKeyOwner, hybridDecrypt);
    } catch (IOException e) {
      throw new AppMetadataEncoderException("Failed to parse metadata from the APK.", e);
    } catch (GeneralSecurityException e) {
      throw new AppMetadataEncoderException("Failed to parse decryption key.", e);
    }
  }

  /**
   * Extract metadata added by the tool from the APK zip repo.
   *
   * <p>Extraction involves identifying a file with metadata inside the APK zip repo, decrypting the
   * bytes in the file using the provided `KeysetHandle`s, decompressing using ZIP inflater, parsing
   * Metadata proto.
   *
   * @return Metadata proto decoded from the APK.
   */
  public Optional<Metadata> decode(
      ZipRepo apkZipRepo, String type, String encryptionKeyOwner, HybridDecrypt hybridDecrypt)
      throws AppMetadataEncoderException {
    String metadataPath =
        String.format(
            "META-INF/%s/%s/%s/metadata.bin",
            AppMetadataEncoderConstants.METADATA_NAMESPACE, type, encryptionKeyOwner);

    // Metadata file doesn't exist in APK
    if (!apkZipRepo.getEntries().containsKey(metadataPath)) {
      return Optional.empty();
    }

    try {
      ByteBuffer byteBuffer = apkZipRepo.getContent(metadataPath);
      byte[] byteArray = new byte[byteBuffer.remaining()];
      byteBuffer.get(byteArray);

      return Optional.of(decodeBytes(byteArray, type, hybridDecrypt));
    } catch (IOException e) {
      throw new AppMetadataEncoderException("Failed to decode Metadata from the APK.", e);
    }
  }

  public Metadata decodeBytes(byte[] encodedMetadata, String type, HybridDecrypt hybridDecrypt)
      throws AppMetadataEncoderException {
    byte[] metadataBytes = uncompress(decrypt(encodedMetadata, hybridDecrypt, type));
    try {
      return Metadata.parseFrom(metadataBytes, ExtensionRegistry.getEmptyRegistry());
    } catch (InvalidProtocolBufferException e) {
      throw new AppMetadataEncoderException(
          "Failed to parse metadata proto from decrypted bytes.", e);
    }
  }

  private static byte[] uncompress(byte[] data) throws AppMetadataEncoderException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    try (InflaterOutputStream inflaterOutputStream = new InflaterOutputStream(outputStream)) {
      inflaterOutputStream.write(data);
    } catch (IOException e) {
      throw new AppMetadataEncoderException("Failed to parse uncompress metadata.", e);
    }
    return outputStream.toByteArray();
  }

  private static byte[] decrypt(byte[] data, HybridDecrypt hybridDecrypt, String encryptionContext)
      throws AppMetadataEncoderException {
    try {
      return hybridDecrypt.decrypt(data, encryptionContext.getBytes(UTF_8));
    } catch (GeneralSecurityException e) {
      throw new AppMetadataEncoderException("Failed to decrypt metadata.", e);
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
