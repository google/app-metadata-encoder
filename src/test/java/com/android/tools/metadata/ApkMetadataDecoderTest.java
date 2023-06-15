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

import static com.android.tools.metadata.TestData.getTestFile;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.zipflinger.ZipRepo;
import com.google.common.collect.ImmutableList;
import com.google.crypto.tink.BinaryKeysetWriter;
import com.google.crypto.tink.HybridDecrypt;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.hybrid.HybridConfig;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ApkMetadataDecoderTest {
  private static final String METADATA_TYPE = "some_type";
  private static final String ENCRYPTION_KEY_OWNER = "some.domain";
  private static final Metadata METADATA =
      Metadata.newBuilder()
          .setAppMetadataEncoderVersion(AppMetadataEncoderConstants.CURRENT_VERSION)
          .addMetadataEntries(MetadataEntry.newBuilder().setKey("some.key").setValue("some-value"))
          .addMetadataEntries(MetadataEntry.newBuilder().setKey("other.key").setValue("some-value"))
          .build();

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private final ApkMetadataEncoder encoder = new ApkMetadataEncoder();
  private KeysetHandle privateKeysetHandle;
  private byte[] publicKeyBytes;

  // Under test
  private final ApkMetadataDecoder apkMetadataDecoder = new ApkMetadataDecoder();

  @Before
  public void setUp() throws Exception {
    // Generate the private key material.
    HybridConfig.register();
    privateKeysetHandle =
        KeysetHandle.generateNew(
            KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_128_GCM"));

    // Obtain the public key material.
    KeysetHandle publicKeysetHandle = privateKeysetHandle.getPublicKeysetHandle();
    ByteArrayOutputStream publicKeyByteArrayOutputStream = new ByteArrayOutputStream();
    publicKeysetHandle.writeNoSecret(
        BinaryKeysetWriter.withOutputStream(publicKeyByteArrayOutputStream));
    publicKeyBytes = publicKeyByteArrayOutputStream.toByteArray();
  }

  @Test
  public void decodeMetadataFromFile_succeeds() throws Exception {
    File inputFile = getTestFile("two-signers.apk", tempFolder);
    encoder.encodeInPlace(buildRequest(inputFile));

    Metadata metadata =
        apkMetadataDecoder
            .decode(inputFile, METADATA_TYPE, ENCRYPTION_KEY_OWNER, privateKeysetHandle)
            .get();

    assertThat(metadata).isEqualTo(METADATA);
  }

  @Test
  public void decodeMetadataFromZipRepo_succeeds() throws Exception {
    File inputFile = getTestFile("two-signers.apk", tempFolder);
    encoder.encodeInPlace(buildRequest(inputFile));
    HybridDecrypt hybridDecrypt = privateKeysetHandle.getPrimitive(HybridDecrypt.class);

    Metadata metadata =
        apkMetadataDecoder
            .decode(
                new ZipRepo(inputFile.getPath()),
                METADATA_TYPE,
                ENCRYPTION_KEY_OWNER,
                hybridDecrypt)
            .get();

    assertThat(metadata).isEqualTo(METADATA);
  }

  @Test
  public void decodeNonExistingMetadataTypeFromFile_returnNoMetadata() throws Exception {
    File inputFile = getTestFile("two-signers.apk", tempFolder);

    Optional<Metadata> metadata =
        apkMetadataDecoder.decode(
            inputFile, METADATA_TYPE, ENCRYPTION_KEY_OWNER, privateKeysetHandle);

    assertThat(metadata).isEmpty();
  }

  @Test
  public void decodeNonExistingMetadataTypeFromZipRepo_returnNoMetadata() throws Exception {
    File inputFile = getTestFile("two-signers.apk", tempFolder);
    HybridDecrypt hybridDecrypt = privateKeysetHandle.getPrimitive(HybridDecrypt.class);

    Optional<Metadata> metadata =
        apkMetadataDecoder.decode(
            new ZipRepo(inputFile.getPath()), METADATA_TYPE, ENCRYPTION_KEY_OWNER, hybridDecrypt);

    assertThat(metadata).isEmpty();
  }

  @Test
  public void decodeMetadataWithOtherDecryptionKeyFromFile_throws() throws Exception {
    File inputFile = getTestFile("two-signers.apk", tempFolder);
    encoder.encodeInPlace(buildRequest(inputFile));
    KeysetHandle otherKeysetHandle =
        KeysetHandle.generateNew(
            KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_128_GCM"));

    AppMetadataEncoderException e =
        assertThrows(
            AppMetadataEncoderException.class,
            () ->
                apkMetadataDecoder
                    .decode(inputFile, METADATA_TYPE, ENCRYPTION_KEY_OWNER, otherKeysetHandle)
                    .get());

    assertThat(e).hasMessageThat().contains("Failed to decrypt metadata");
  }

  @Test
  public void decodeMetadataWithOtherDecryptionKeyFromZipRepo_throws() throws Exception {
    File inputFile = getTestFile("two-signers.apk", tempFolder);
    encoder.encodeInPlace(buildRequest(inputFile));
    KeysetHandle otherKeysetHandle =
        KeysetHandle.generateNew(
            KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_128_GCM"));
    HybridDecrypt hybridDecrypt = otherKeysetHandle.getPrimitive(HybridDecrypt.class);

    AppMetadataEncoderException e =
        assertThrows(
            AppMetadataEncoderException.class,
            () ->
                apkMetadataDecoder
                    .decode(
                        new ZipRepo(inputFile.getPath()),
                        METADATA_TYPE,
                        ENCRYPTION_KEY_OWNER,
                        hybridDecrypt)
                    .get());

    assertThat(e).hasMessageThat().contains("Failed to decrypt metadata");
  }

  private EncodeAppMetadataRequest buildRequest(File inputFile) {
    return EncodeAppMetadataRequest.builder()
        .setInputFile(inputFile)
        .setMetadata(METADATA)
        .setType(METADATA_TYPE)
        .setEncryptions(
            ImmutableList.of(
                EncodeAppMetadataRequest.Encryption.builder()
                    .setEncryptionKeyOwner(ENCRYPTION_KEY_OWNER)
                    .setEncryptionKey(ByteBuffer.wrap(publicKeyBytes))
                    .build()))
        .build();
  }
}
