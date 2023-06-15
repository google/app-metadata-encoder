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

import com.android.zipflinger.ZipRepo;
import java.io.File;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AabMetadataDecoderTest {
  private static final String METADATA_TYPE = "other_test";
  private static final Metadata METADATA =
      Metadata.newBuilder()
          .setAppMetadataEncoderVersion(AppMetadataEncoderConstants.CURRENT_VERSION)
          .addMetadataEntries(MetadataEntry.newBuilder().setKey("test-key").setValue("test-value"))
          .build();

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  // Under test
  private final AabMetadataDecoder aabMetadataDecoder = new AabMetadataDecoder();

  @Test
  public void decodeMetadataFromFile_succeeds() throws Exception {
    File inputFile = getTestFile("app-with-test-metadata-type.aab", tempFolder);
    new AabMetadataEncoder()
        .encodeInPlace(
            EncodeAppMetadataRequest.builder()
                .setInputFile(inputFile)
                .setMetadata(METADATA)
                .setType(METADATA_TYPE)
                .build());

    Metadata metadata = aabMetadataDecoder.decode(inputFile, METADATA_TYPE).get();

    assertThat(metadata).isEqualTo(METADATA);
  }

  @Test
  public void decodeMetadataFromZipRepo_succeeds() throws Exception {
    File inputFile = getTestFile("app-with-test-metadata-type.aab", tempFolder);
    new AabMetadataEncoder()
        .encodeInPlace(
            EncodeAppMetadataRequest.builder()
                .setInputFile(inputFile)
                .setMetadata(METADATA)
                .setType(METADATA_TYPE)
                .build());

    Metadata metadata =
        aabMetadataDecoder.decode(new ZipRepo(inputFile.getPath()), METADATA_TYPE).get();

    assertThat(metadata).isEqualTo(METADATA);
  }

  @Test
  public void decodeNonExistingMetadataTypeFromFile_returnNoMetadata() throws Exception {
    File inputFile = getTestFile("app-with-test-metadata-type.aab", tempFolder);

    Optional<Metadata> metadata = aabMetadataDecoder.decode(inputFile, METADATA_TYPE);

    assertThat(metadata).isEmpty();
  }

  @Test
  public void decodeNonExistingMetadataTypeFromZipRepo_returnNoMetadata() throws Exception {
    File inputFile = getTestFile("app-with-test-metadata-type.aab", tempFolder);

    Optional<Metadata> metadata =
        aabMetadataDecoder.decode(new ZipRepo(inputFile.getPath()), METADATA_TYPE);

    assertThat(metadata).isEmpty();
  }
}
