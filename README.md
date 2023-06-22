# App Metadata Encoder

App Metadata Encoder is a tool to encode metadata into APKs or AABs for Android
app stores (for example Google Play Store) to be read and analysed. It is available as
a command line tool and also as a Java library. It is your responsibility to
notify your users you are using this tool to encode metadata.

## Usage

To use the tool

- Have [Java 11](https://www.oracle.com/java/technologies/downloads/#java11) or later version installed.

- Download the [`app_metadata_encoder.jar`](https://www.gstatic.com/play-apps-publisher-rapid/app-metadata-encoder/prod/app_metadata_encoder.jar).

- (Optionally) you can also download the [source jar](https://www.gstatic.com/play-apps-publisher-rapid/app-metadata-encoder/prod/app_metadata_encoder-src.jar) if you intend to use App Metadata Encoder as a library.

The metadata is a list of key-value pairs that can be specified through
`--metadata=<key>=<value>` repeated flag.

The metadata **type** is a context for the metadata to be encoded into the APK
or AAB. It is used for placing, encrypting and decrypting of the metadata

### AAB
To encode metadata into an AAB file you can run:

```shell
java -jar app_metadata_encoder.jar encode \
  /path/to/app.aab \
  --metadata=key1=value1 \
  --metadata=key2=value2 \
  --type=some_type \
  --output=/path/to/app-out.aab
```

### APK

Because the APK is both the publishing *and* the serving format, in order for
the metadata not to be exposed to end-user, it needs to be encrypted. Because of
that the tool requires an encryption to be specified through the additional flag
`--encryption`. Multiple encryptions can be specified if the APK will be
published to multiple stores.

To encode metadata into an APK file you can run:

```shell
java -jar app_metadata_encoder.jar encode \
  /path/to/app.apk \
  --metadata=key1=value1 \
  --metadata=key2=value2 \
  --type=some_type \
  --encryption=company.com=/path/to/key.bin \
  --output=/path/to/app-out.apk
```

### Additional options

To learn more about each option and the available commands of the command line tool, run the tool with `--help` or `-h`:

```shell
java -jar app_metadata_encoder.jar --help
```

To learn more about each option of the encode command, run the tool with encode command and `--help` or `-h`:

```shell
java -jar app_metadata_encoder.jar encode --help
```

To turn on [verbose mode](https://en.wikipedia.org/wiki/Verbose_mode) for easier
debugging, run the tool with `--verbose` or `-v`:

```shell
java -jar app_metadata_encoder.jar encode \
  /path/to/app.apk \
  --verbose \
  --metadata=key1=value1 \
  --metadata=key2=value2 \
  --type=some_type \
  --encryption=company.com=/path/to/key.bin \
  --output=/path/to/app-out.apk
```

## Encoding DRM metadata

Play Store supports multiple reserved DRM-specific metadata keys that can be
encoded into APKs or AABs by DRM providers:

|Key             |Value                   |
|----------------|------------------------|
|drm.package_name|The identifier of the DRM.<br><br>The package name should have the form of a Java package name (for example `com.company.mydrm`). It must be a reverse of a domain DRM provider owns, and must be the same for all the apps the DRM protects.|
|drm.name        |The human readable and publicly known name of the DRM product.<br><br>For example `Foo App Protection`.|
|drm.version     |The version of the DRM.<br><br>The version string should be in [semantic versioning](https://semver.org/) format, for example `1.2.3`.|

### AAB

Replace `<package-name>`, `<name>` and `<version>` in the command below with your DRM
package name and version:

```shell
java -jar app_metadata_encoder.jar encode \
  /path/to/app.aab \
  --metadata=drm.package_name=<package-name> \
  --metadata=drm.name=<name> \
  --metadata=drm.version=<version> \
  --type=drm \
  --output=/path/to/app-out.aab \
```

### APK

For DRM providers who want Play Store to be able to read the metadata in an APK,
download [Play Store encryption key](https://www.gstatic.com/play-apps-publisher/drm-metadata/prod/play_key.bin)
and provide it to the tool in the flag
`--encryption=com.google.play=/path/to/play_key.bin`. Then, replace
`<package-name>`, `<name>` and `<version>` in the command below with your DRM package name
and version:

```shell
java -jar app_metadata_encoder.jar encode \
  /path/to/app.apk \
  --metadata=drm.package_name=<package-name> \
  --metadata=drm.name=<name> \
  --metadata=drm.version=<version> \
  --type=drm \
  --encryption=com.google.play=/path/to/play_key.bin \
  --output=/path/to/app-out.apk \
```

## Library usage

To use the tool as a Java library, you can add [`app_metadata_encoder.jar`](https://www.gstatic.com/play-apps-publisher-rapid/app-metadata-encoder/prod/app_metadata_encoder.jar) as a dependency in your Java project.

You can also attach [source jar](https://www.gstatic.com/play-apps-publisher-rapid/app-metadata-encoder/prod/app_metadata_encoder-src.jar) of the tool to simplify debugging of your integration.

### AAB

To encode metadata into an AAB using the library:

```java
// Create request object.
EncodeAppMetadataRequest request =
    EncodeAppMetadataRequest.builder()
        .setInputFile(new File("/path/to/app.aab"))
        .setMetadata(
            Metadata.newBuilder()
                .setAppMetadataEncoderVersion(AppMetadataEncoderConstants.CURRENT_VERSION)
                .addMetadataEntries(
                    MetadataEntry.newBuilder().setKey("key").setValue("value").build())
                .addMetadataEntries(
                    MetadataEntry.newBuilder().setKey("key2").setValue("value2").build())
                .build())
        .setType("some_type")
        .build();

// Encode metadata into an AAB.
AppMetadataEncoder encoder = new AppMetadataEncoder();
encoder.encodeInPlace(request);
```

### APK

To encode metadata into an APK using the library:

```java
// Create encryption object with encryption key owner and encryption key.
EncodeAppMetadataRequest.Encryption encryption =
    EncodeAppMetadataRequest.Encryption.builder()
        .setEncryptionKeyOwner("company.domain")
        .setEncryptionKey(
            ByteBuffer.wrap(Files.toByteArray(new File("/path/to/public_key.bin"))))
        .build();

// Create request object.
EncodeAppMetadataRequest request =
    EncodeAppMetadataRequest.builder()
        .setInputFile(
            new File("/path/to/app.apk"))
        .setMetadata(
            Metadata.newBuilder()
                .setAppMetadataEncoderVersion("0.1.0")
                .addMetadataEntries(
                    MetadataEntry.newBuilder().setKey("key").setValue("value").build())
                .addMetadataEntries(
                    MetadataEntry.newBuilder().setKey("key2").setValue("value2").build())
                .build())
        .setType("some_type")
        .setEncryptions(ImmutableList.of(encryption))
        .build();

// Encode metadata into an APK.
AppMetadataEncoder encoder = new AppMetadataEncoder();
encoder.encodeInPlace(request);
```

### Java 8 support

If you need a version of the App Metadata Encoder library that supports Java 8
you can you can use an alternative build of the tool:

- [app_metadata_encoder_java8.jar](https://www.gstatic.com/play-apps-publisher-rapid/app-metadata-encoder/prod/app_metadata_encoder_java8.jar)

- [app_metadata_encoder_java8-src.jar](https://www.gstatic.com/play-apps-publisher-rapid/app-metadata-encoder/prod/app_metadata_encoder_java8-src.jar)

## Generating encryption keys

WARNING: If you are using the App Metadata Encoder to encode metadata into APKs and AABs, it's likely that you **DO NOT NEED** to generate encryption/decryption keys. You should ask the party responsible for reading metadata from APKs and AABs to provide you with public/encryption key so that only they are able decrypt the metadata.

The tool uses [Google Tink](https://developers.google.com/tink/install-tinkey) encryption library.

For the encryption primitive the tool supports [Hybrid encryption](https://developers.google.com/tink/hybrid) and only in the binary format.

To generate compatible key for the tool you can use [tinkey](https://developers.google.com/tink/generate-plaintext-keyset):

```shell
tinkey create-keyset \
	--key-template DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM \
	--out-format binary \
	--out my_generated_key.bin

tinkey create-public-keyset \
	--in-format binary \
	--in my_generated_key.bin \
	--out-format binary \
	--out my_generated_key_public.bin
```

The `create-keyset` command above generates the keyset containing both private and public/encryption key and saves it in binary format in `my_generated_key.bin` file.

The `create-public-keyset` command extracts only the public key from `my_generated_key.bin` and saves it in binary format in `my_generated_key_public.bin` file.

