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

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.crypto.tink.BinaryKeysetReader;
import com.google.crypto.tink.HybridEncrypt;
import com.google.crypto.tink.KeysetHandle;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Optional;

/** Request for the AppMetadataEncoder. */
@AutoValue
public abstract class EncodeAppMetadataRequest {
  // Get the input APK or AAB file.
  public abstract File getInputFile();

  // Get the metadata proto to encode into the app.
  public abstract Metadata getMetadata();

  // Get the type of the metadata to encode into the app.
  public abstract String getType();

  // Get the encryptions to apply to metadata before encoding it into APK.
  public abstract ImmutableList<Encryption> getEncryptions();

  // Convert to Builder.
  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_EncodeAppMetadataRequest.Builder().setEncryptions(ImmutableList.of());
  }

  /** Builder for {@link EncodeAppMetadataRequest} instances */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setInputFile(File inputFile);

    public abstract Builder setMetadata(Metadata metadata);

    public abstract Builder setType(String type);

    public abstract Builder setEncryptions(ImmutableList<Encryption> encryptions);

    public abstract EncodeAppMetadataRequest build();
  }

  /** Contains data necessary for enrypting metadata encoded into APK. */
  @AutoValue
  public abstract static class Encryption {
    // Get the owner that owns the encryption keys.
    public abstract String getEncryptionKeyOwner();

    // Get encryption key.
    abstract Optional<ByteBuffer> getEncryptionKeyOptional();

    // Get encryption key.
    public ByteBuffer getEncryptionKey() {
      return getEncryptionKeyOptional().get();
    }

    // Get encryption key.
    abstract Optional<HybridEncrypt> getEncrypterOptional();

    // Get encrypter.
    public HybridEncrypt getEncrypter() throws AppMetadataEncoderException {
      if (getEncrypterOptional().isPresent()) {
        return getEncrypterOptional().get();
      }
      try {
        byte[] encryptionKeyByteArray = new byte[getEncryptionKey().remaining()];
        getEncryptionKey().get(encryptionKeyByteArray);
        return KeysetHandle.readNoSecret(BinaryKeysetReader.withBytes(encryptionKeyByteArray))
            .getPrimitive(HybridEncrypt.class);
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to read encryption key.", e);
      } catch (GeneralSecurityException e) {
        throw new AppMetadataEncoderException("Failed to parse encryption key.", e);
      }
    }

    public static Builder builder() {
      return new AutoValue_EncodeAppMetadataRequest_Encryption.Builder();
    }

    /** Builder for {@link Encryption} instances */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setEncryptionKeyOwner(String owner);

      abstract Builder setEncryptionKeyOptional(Optional<ByteBuffer> key);

      public Builder setEncryptionKey(ByteBuffer key) {
        return setEncryptionKeyOptional(Optional.of(key));
      }

      abstract Builder setEncrypterOptional(Optional<HybridEncrypt> hybridEncrypt);

      public Builder setEncrypter(HybridEncrypt hybridEncrypt) {
        return setEncrypterOptional(Optional.of(hybridEncrypt));
      }

      abstract Encryption autoBuild(); // not public

      public final Encryption build() {
        Encryption encryption = autoBuild();
        boolean onlyEncrypterSet =
            encryption.getEncrypterOptional().isPresent()
                && !encryption.getEncryptionKeyOptional().isPresent();
        boolean onlyEncryptionKeySet =
            encryption.getEncryptionKeyOptional().isPresent()
                && !encryption.getEncrypterOptional().isPresent();
        Preconditions.checkArgument(
            onlyEncrypterSet || onlyEncryptionKeySet,
            "Exactly one EncryptionKey or Encrypter must be set.");
        return encryption;
      }
    }
  }
}
