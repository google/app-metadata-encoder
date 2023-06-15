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

import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AabMetadataEncoderTest {
  private static final String METADATA_TYPE = "test";
  private static final String METADATA_TYPE_2 = "other_test";
  private static final Metadata METADATA =
      Metadata.newBuilder()
          .setAppMetadataEncoderVersion(AppMetadataEncoderConstants.CURRENT_VERSION)
          .addMetadataEntries(MetadataEntry.newBuilder().setKey("test-key").setValue("test-value"))
          .build();

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private final AabMetadataDecoder aabMetadataDecoder = new AabMetadataDecoder();

  @Test
  public void encodeMetadata_addsMetadataToAab() throws Exception {
    File inputFile = getTestFile("app-with-test-metadata-type.aab", tempFolder);

    new AabMetadataEncoder()
        .encodeInPlace(
            EncodeAppMetadataRequest.builder()
                .setInputFile(inputFile)
                .setMetadata(METADATA)
                .setType(METADATA_TYPE_2)
                .build());

    Metadata outputMetadata = aabMetadataDecoder.decode(inputFile, METADATA_TYPE_2).get();
    assertThat(outputMetadata).isEqualTo(METADATA);
  }

  @Test
  public void encodeMetadata_preservesExistingMetadata() throws Exception {
    File inputFile = getTestFile("app-with-test-metadata-type.aab", tempFolder);
    Metadata expectedMetadata =
        Metadata.newBuilder()
            // The version of the tool at which the test AAB was generated.
            .setAppMetadataEncoderVersion("0.1.0")
            .addMetadataEntries(MetadataEntry.newBuilder().setKey("key2").setValue("value2"))
            .addMetadataEntries(MetadataEntry.newBuilder().setKey("key").setValue("value"))
            .build();

    new AabMetadataEncoder()
        .encodeInPlace(
            EncodeAppMetadataRequest.builder()
                .setInputFile(inputFile)
                .setMetadata(METADATA)
                .setType(METADATA_TYPE_2)
                .build());

    Metadata outputMetadata = aabMetadataDecoder.decode(inputFile, METADATA_TYPE).get();
    assertThat(outputMetadata).isEqualTo(expectedMetadata);
  }

  @Test
  public void encodesMetadataWithExistingType_throwsException() throws Exception {
    File inputFile = getTestFile("app-with-test-metadata-type.aab", tempFolder);

    EncodeAppMetadataRequest encodeAppMetadataRequest =
        EncodeAppMetadataRequest.builder()
            .setInputFile(inputFile)
            .setMetadata(METADATA)
            .setType(METADATA_TYPE)
            .build();

    AppMetadataEncoderException e =
        assertThrows(
            AppMetadataEncoderException.class,
            () -> new AabMetadataEncoder().encodeInPlace(encodeAppMetadataRequest));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(String.format("Metadata of type '%s' already exists.", METADATA_TYPE));
  }

}
