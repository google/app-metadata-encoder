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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.crypto.tink.BinaryKeysetWriter;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.hybrid.HybridConfig;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AppMetadataEncoderTest {
  private static final String METADATA_TYPE = "some_type";
  private static final ByteBuffer ENCRYPTION_KEY = ByteBuffer.wrap(new byte[] {1, 2, 3});
  private static final ByteBuffer EMPTY_ENCRYPTION_KEY = ByteBuffer.wrap(new byte[] {});
  private static final Metadata METADATA =
      Metadata.newBuilder()
          .setAppMetadataEncoderVersion(AppMetadataEncoderConstants.CURRENT_VERSION)
          .addMetadataEntries(MetadataEntry.newBuilder().setKey("key").setValue("value"))
          .build();
  private static final ImmutableList<EncodeAppMetadataRequest.Encryption> ENCRYPTIONS =
      ImmutableList.of(
          EncodeAppMetadataRequest.Encryption.builder()
              .setEncryptionKeyOwner("com.test")
              .setEncryptionKey(ENCRYPTION_KEY)
              .build());

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private final AppMetadataEncoder encoder = new AppMetadataEncoder();

  @Test
  public void encodeAabWithoutVersionInRequest_succeed() throws Exception {
    AabMetadataDecoder aabMetadataDecoder = new AabMetadataDecoder();
    File inputFile = getTestFile("app-with-test-metadata-type.aab", tempFolder);
    EncodeAppMetadataRequest encodeAppMetadataRequest =
        EncodeAppMetadataRequest.builder()
            .setMetadata(
                METADATA.toBuilder().clearAppMetadataEncoderVersion().build()) // Clear version
            .setInputFile(inputFile)
            .setType(METADATA_TYPE)
            .build();

    encoder.encodeInPlace(encodeAppMetadataRequest);

    Metadata outputMetadata = aabMetadataDecoder.decode(inputFile, METADATA_TYPE).get();
    assertThat(outputMetadata).isEqualTo(METADATA);
  }

  @Test
  public void encodeAabWithEmptyType_throwsException() throws Exception {
    EncodeAppMetadataRequest encodeAppMetadataRequest =
        EncodeAppMetadataRequest.builder()
            .setMetadata(METADATA)
            .setInputFile(getTestFile("app-with-test-metadata-type.aab", tempFolder))
            .setType("")
            .build();

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> encoder.encodeInPlace(encodeAppMetadataRequest));
    assertThat(e).hasMessageThat().isEqualTo("'type' must be provided a value.");
  }

  @Test
  public void encodeAabWithDuplicateKeysInMetadata_throwsException() throws Exception {
    EncodeAppMetadataRequest encodeAppMetadataRequest =
        EncodeAppMetadataRequest.builder()
            .setMetadata(createMetadataWithDuplicateKeys())
            .setInputFile(getTestFile("app-with-test-metadata-type.aab", tempFolder))
            .setType(METADATA_TYPE)
            .build();

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> encoder.encodeInPlace(encodeAppMetadataRequest));
    assertThat(e).hasMessageThat().isEqualTo("Duplicate key 'key' in metadata is found.");
  }

  @Test
  public void encodeAabWithNonExistentInputFile_throwsException() throws Exception {
    File inputFile = tempFolder.newFolder().toPath().resolve("/non-existent.aab").toFile();
    EncodeAppMetadataRequest encodeAppMetadataRequest =
        EncodeAppMetadataRequest.builder()
            .setInputFile(inputFile)
            .setMetadata(METADATA)
            .setType(METADATA_TYPE)
            .build();

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> encoder.encodeInPlace(encodeAppMetadataRequest));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(String.format("Input file does not exist at: %s.", inputFile.getAbsolutePath()));
  }

  @Test
  public void encodeAabWithInvalidInputFileType_throwsException() throws Exception {
    EncodeAppMetadataRequest encodeAppMetadataRequest =
        EncodeAppMetadataRequest.builder()
            .setInputFile(getTestFile("blank.txt", tempFolder))
            .setMetadata(METADATA)
            .setType(METADATA_TYPE)
            .build();

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> encoder.encodeInPlace(encodeAppMetadataRequest));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo("Input file 'blank.txt' must be either an APK or AAB.");
  }

  @Test
  public void encodeApkWithoutVersionInRequest_succeed() throws Exception {
    // Generate the private key material.
    HybridConfig.register();
    KeysetHandle privateKeysetHandle =
        KeysetHandle.generateNew(
            KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_128_GCM"));
    // Obtain the public key material.
    KeysetHandle publicKeysetHandle = privateKeysetHandle.getPublicKeysetHandle();
    ByteArrayOutputStream publicKeyByteArrayOutputStream = new ByteArrayOutputStream();
    publicKeysetHandle.writeNoSecret(
        BinaryKeysetWriter.withOutputStream(publicKeyByteArrayOutputStream));
    byte[] publicKeyBytes = publicKeyByteArrayOutputStream.toByteArray();
    // Encode metadata
    File inputFile = getTestFile("v1-only-two-signers.apk", tempFolder);
    EncodeAppMetadataRequest encodeAppMetadataRequest =
        EncodeAppMetadataRequest.builder()
            .setInputFile(inputFile)
            .setMetadata(
                METADATA.toBuilder().clearAppMetadataEncoderVersion().build()) // Clear version
            .setType(METADATA_TYPE)
            .setEncryptions(
                ImmutableList.of(
                    EncodeAppMetadataRequest.Encryption.builder()
                        .setEncryptionKeyOwner("com.test")
                        .setEncryptionKey(ByteBuffer.wrap(publicKeyBytes))
                        .build()))
            .build();

    encoder.encodeInPlace(encodeAppMetadataRequest);

    ApkMetadataDecoder apkMetadataDecoder = new ApkMetadataDecoder();
    Metadata metadata =
        apkMetadataDecoder.decode(inputFile, METADATA_TYPE, "com.test", privateKeysetHandle).get();
    assertThat(metadata).isEqualTo(METADATA);
  }

  @Test
  public void encodeApkWithEmptyType_throwsException() throws Exception {
    EncodeAppMetadataRequest encodeAppMetadataRequest =
        EncodeAppMetadataRequest.builder()
            .setInputFile(getTestFile("v1-only-two-signers.apk", tempFolder))
            .setMetadata(METADATA)
            .setType("")
            .setEncryptions(ENCRYPTIONS)
            .build();

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> encoder.encodeInPlace(encodeAppMetadataRequest));
    assertThat(e).hasMessageThat().isEqualTo("'type' must be provided a value.");
  }

  @Test
  public void encodeApkWithDuplicateKeysInMetadata_throwsException() throws Exception {
    EncodeAppMetadataRequest encodeAppMetadataRequest =
        EncodeAppMetadataRequest.builder()
            .setInputFile(getTestFile("v1-only-two-signers.apk", tempFolder))
            .setMetadata(createMetadataWithDuplicateKeys())
            .setType(METADATA_TYPE)
            .setEncryptions(ENCRYPTIONS)
            .build();

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> encoder.encodeInPlace(encodeAppMetadataRequest));
    assertThat(e).hasMessageThat().isEqualTo("Duplicate key 'key' in metadata is found.");
  }

  @Test
  public void encodeApkWithEmptyEncryptions_throwsException() throws Exception {
    EncodeAppMetadataRequest encodeAppMetadataRequest =
        EncodeAppMetadataRequest.builder()
            .setInputFile(getTestFile("v1-only-two-signers.apk", tempFolder))
            .setMetadata(METADATA)
            .setType(METADATA_TYPE)
            .build();

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> encoder.encodeInPlace(encodeAppMetadataRequest));
    assertThat(e).hasMessageThat().isEqualTo("At least one Encryption object must be provided.");
  }

  @Test
  public void encodeApkWithEmptyEncryptionKeyOwner_throwsException() throws Exception {
    EncodeAppMetadataRequest encodeAppMetadataRequest =
        EncodeAppMetadataRequest.builder()
            .setInputFile(getTestFile("v1-only-two-signers.apk", tempFolder))
            .setMetadata(METADATA)
            .setType(METADATA_TYPE)
            .setEncryptions(
                ImmutableList.of(
                    EncodeAppMetadataRequest.Encryption.builder()
                        .setEncryptionKeyOwner("")
                        .setEncryptionKey(ENCRYPTION_KEY)
                        .build()))
            .build();

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> encoder.encodeInPlace(encodeAppMetadataRequest));
    assertThat(e).hasMessageThat().isEqualTo("'encryptionKeyOwner' must be provided a value.");
  }

  @Test
  public void encodeApkWithEmptyEncryptionKey_throwsException() throws Exception {
    EncodeAppMetadataRequest encodeAppMetadataRequest =
        EncodeAppMetadataRequest.builder()
            .setInputFile(getTestFile("v1-only-two-signers.apk", tempFolder))
            .setMetadata(METADATA)
            .setType(METADATA_TYPE)
            .setEncryptions(
                ImmutableList.of(
                    EncodeAppMetadataRequest.Encryption.builder()
                        .setEncryptionKeyOwner("com.test")
                        .setEncryptionKey(EMPTY_ENCRYPTION_KEY)
                        .build()))
            .build();

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> encoder.encodeInPlace(encodeAppMetadataRequest));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo("Encryption key with encryption key owner of 'com.test' does not exist.");
  }

  @Test
  public void encodeApkWithNonExistentInputFile_throwsException() throws Exception {
    File inputFile = tempFolder.newFolder().toPath().resolve("/non-existent.apk").toFile();
    EncodeAppMetadataRequest encodeAppMetadataRequest =
        EncodeAppMetadataRequest.builder()
            .setInputFile(inputFile)
            .setMetadata(METADATA)
            .setType(METADATA_TYPE)
            .setEncryptions(ENCRYPTIONS)
            .build();

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> encoder.encodeInPlace(encodeAppMetadataRequest));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(String.format("Input file does not exist at: %s.", inputFile.getAbsolutePath()));
  }

  @Test
  public void encodeApkWithInvalidInputFileType_throwsException() throws Exception {
    File inputFile = getTestFile("blank.txt", tempFolder);
    EncodeAppMetadataRequest encodeAppMetadataRequest =
        EncodeAppMetadataRequest.builder()
            .setInputFile(inputFile)
            .setMetadata(METADATA)
            .setType(METADATA_TYPE)
            .setEncryptions(ENCRYPTIONS)
            .build();

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> encoder.encodeInPlace(encodeAppMetadataRequest));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            String.format("Input file '%s' must be either an APK or AAB.", inputFile.getName()));
  }

  private static Metadata createMetadataWithDuplicateKeys() {
    return Metadata.newBuilder()
        .setAppMetadataEncoderVersion(AppMetadataEncoderConstants.CURRENT_VERSION)
        .addMetadataEntries(MetadataEntry.newBuilder().setKey("key").setValue("value"))
        .addMetadataEntries(MetadataEntry.newBuilder().setKey("key").setValue("value2"))
        .build();
  }
}
