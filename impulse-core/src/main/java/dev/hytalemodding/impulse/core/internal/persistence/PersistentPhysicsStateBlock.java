package dev.hytalemodding.impulse.core.internal.persistence;

import com.github.luben.zstd.Zstd;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.validation.Validators;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import javax.annotation.Nonnull;
import lombok.Getter;
import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;

/**
 * Compressed block for high-cardinality persistent physics state.
 */
public class PersistentPhysicsStateBlock {

    static final String KIND_BODIES = "bodies";
    static final String KIND_JOINTS = "joints";
    static final String CODEC_BSON_ARRAY_V1 = "bson-array-v1";
    static final String COMPRESSION_ZSTD = "zstd";
    private static final String PAYLOAD_ITEMS_KEY = "Items";
    private static final Set<String> SUPPORTED_KINDS = Set.of(KIND_BODIES, KIND_JOINTS);
    private static final int ZSTD_COMPRESSION_LEVEL = 3;
    private static final int MAX_UNCOMPRESSED_BYTES = 64 * 1024 * 1024;
    private static final String UNCOMPRESSED_BYTES_LIMIT_MESSAGE =
        "Persistent physics state block uncompressed size exceeds limit";
    private static final byte[] EMPTY_PAYLOAD = new byte[0];
    private static final Codec<byte[]> BINARY_PAYLOAD_CODEC = new PersistentPhysicsBinaryPayloadCodec();
    private static final BsonDocumentCodec BSON_DOCUMENT_CODEC = new BsonDocumentCodec();
    private static final ArrayCodec<PersistentPhysicsBodyState> BODY_ARRAY_CODEC =
        new ArrayCodec<>(PersistentPhysicsBodyState.CODEC, PersistentPhysicsBodyState[]::new);
    private static final ArrayCodec<PersistentPhysicsJointState> JOINT_ARRAY_CODEC =
        new ArrayCodec<>(PersistentPhysicsJointState.CODEC, PersistentPhysicsJointState[]::new);

    @Nonnull
    public static final BuilderCodec<PersistentPhysicsStateBlock> CODEC = BuilderCodec.builder(
            PersistentPhysicsStateBlock.class,
            PersistentPhysicsStateBlock::new)
        .append(new KeyedCodec<>("Kind", Codec.STRING, false),
            (block, value) -> block.kind = value,
            PersistentPhysicsStateBlock::getKind)
        .addValidator(Validators.nonNull())
        .addValidator(PersistentPhysicsValidation.stringIn(SUPPORTED_KINDS,
            "Persistent physics state block kind is unsupported"))
        .add()
        .append(new KeyedCodec<>("Codec", Codec.STRING, false),
            (block, value) -> block.codec = value,
            PersistentPhysicsStateBlock::getCodec)
        .addValidator(Validators.nonNull())
        .addValidator(PersistentPhysicsValidation.stringEquals(CODEC_BSON_ARRAY_V1,
            "Persistent physics state block codec is unsupported"))
        .add()
        .append(new KeyedCodec<>("Compression", Codec.STRING, false),
            (block, value) -> block.compression = value,
            PersistentPhysicsStateBlock::getCompression)
        .addValidator(Validators.nonNull())
        .addValidator(PersistentPhysicsValidation.stringEquals(COMPRESSION_ZSTD,
            "Persistent physics state block compression is unsupported"))
        .add()
        .append(new KeyedCodec<>("SchemaVersion", Codec.INTEGER, false),
            (block, value) -> block.schemaVersion = value,
            PersistentPhysicsStateBlock::getSchemaVersion)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(PersistentPhysicsWorldResource.CURRENT_SCHEMA_VERSION,
            PersistentPhysicsWorldResource.CURRENT_SCHEMA_VERSION))
        .add()
        .append(new KeyedCodec<>("BlockIndex", Codec.INTEGER, false),
            (block, value) -> block.blockIndex = value,
            PersistentPhysicsStateBlock::getBlockIndex)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(0, Integer.MAX_VALUE))
        .add()
        .append(new KeyedCodec<>("SpaceId", Codec.INTEGER, false),
            (block, value) -> block.spaceId = value,
            PersistentPhysicsStateBlock::getSpaceId)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, Integer.MAX_VALUE))
        .add()
        .append(new KeyedCodec<>("ItemCount", Codec.INTEGER, false),
            (block, value) -> block.itemCount = value,
            PersistentPhysicsStateBlock::getItemCount)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(0, Integer.MAX_VALUE))
        .add()
        .append(new KeyedCodec<>("UncompressedBytes", Codec.INTEGER, false),
            (block, value) -> block.uncompressedBytes = value,
            PersistentPhysicsStateBlock::getUncompressedBytes)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, Integer.MAX_VALUE))
        .addValidator(PersistentPhysicsValidation.intAtMost(MAX_UNCOMPRESSED_BYTES,
            UNCOMPRESSED_BYTES_LIMIT_MESSAGE))
        .add()
        .append(new KeyedCodec<>("CompressedBytes", Codec.INTEGER, false),
            (block, value) -> block.compressedBytes = value,
            PersistentPhysicsStateBlock::getCompressedBytes)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, Integer.MAX_VALUE))
        .add()
        .append(new KeyedCodec<>("Crc32", Codec.LONG, false),
            (block, value) -> block.crc32 = value,
            PersistentPhysicsStateBlock::getCrc32)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(0L, 0xffff_ffffL))
        .add()
        .append(new KeyedCodec<>("Payload", BINARY_PAYLOAD_CODEC, false),
            (block, value) -> block.payload = copyPayload(value),
            PersistentPhysicsStateBlock::getPayload)
        .addValidator(Validators.nonNull())
        .addValidator(PersistentPhysicsValidation.nonEmptyBytes(
            "Persistent physics state block payload cannot be empty"))
        .add()
        .build();

    @Nonnull
    private String kind = "";
    @Nonnull
    private String codec = "";
    @Nonnull
    private String compression = "";
    @Getter
    private int schemaVersion;
    @Getter
    private int blockIndex;
    @Getter
    private int spaceId;
    @Getter
    private int itemCount;
    @Getter
    private int uncompressedBytes;
    @Getter
    private int compressedBytes;
    @Getter
    private long crc32;
    @Nonnull
    private byte[] payload = EMPTY_PAYLOAD;

    public PersistentPhysicsStateBlock() {
    }

    @Nonnull
    static PersistentPhysicsStateBlock[] bodyBlocks(@Nonnull PersistentPhysicsBodyState[] bodies) {
        Map<Integer, List<PersistentPhysicsBodyState>> bySpace = new LinkedHashMap<>();
        for (PersistentPhysicsBodyState body : bodies) {
            bySpace.computeIfAbsent(body.getSpaceId(), ignored -> new ArrayList<>()).add(body.copy());
        }

        List<PersistentPhysicsStateBlock> blocks = new ArrayList<>(bySpace.size());
        int blockIndex = 0;
        for (Map.Entry<Integer, List<PersistentPhysicsBodyState>> entry : bySpace.entrySet()) {
            PersistentPhysicsBodyState[] states = entry.getValue().toArray(PersistentPhysicsBodyState[]::new);
            BsonDocument payloadDocument = new BsonDocument();
            payloadDocument.put(PAYLOAD_ITEMS_KEY, BODY_ARRAY_CODEC.encode(states, new ExtraInfo()));
            blocks.add(compressed(KIND_BODIES, blockIndex++, entry.getKey(), states.length, payloadDocument));
        }
        return blocks.toArray(PersistentPhysicsStateBlock[]::new);
    }

    @Nonnull
    static PersistentPhysicsStateBlock[] jointBlocks(@Nonnull PersistentPhysicsJointState[] joints) {
        Map<Integer, List<PersistentPhysicsJointState>> bySpace = new LinkedHashMap<>();
        for (PersistentPhysicsJointState joint : joints) {
            bySpace.computeIfAbsent(joint.getSpaceId(), ignored -> new ArrayList<>()).add(joint.copy());
        }

        List<PersistentPhysicsStateBlock> blocks = new ArrayList<>(bySpace.size());
        int blockIndex = 0;
        for (Map.Entry<Integer, List<PersistentPhysicsJointState>> entry : bySpace.entrySet()) {
            PersistentPhysicsJointState[] states = entry.getValue().toArray(PersistentPhysicsJointState[]::new);
            BsonDocument payloadDocument = new BsonDocument();
            payloadDocument.put(PAYLOAD_ITEMS_KEY, JOINT_ARRAY_CODEC.encode(states, new ExtraInfo()));
            blocks.add(compressed(KIND_JOINTS, blockIndex++, entry.getKey(), states.length, payloadDocument));
        }
        return blocks.toArray(PersistentPhysicsStateBlock[]::new);
    }

    @Nonnull
    static PersistentPhysicsBodyState[] decodeBodyBlocks(@Nonnull PersistentPhysicsStateBlock[] blocks) {
        List<PersistentPhysicsBodyState> states = new ArrayList<>();
        for (PersistentPhysicsStateBlock block : blocks) {
            BsonValue items = block.inflate(KIND_BODIES).get(PAYLOAD_ITEMS_KEY);
            PersistentPhysicsBodyState[] decoded = BODY_ARRAY_CODEC.decode(items, new ExtraInfo());
            assert decoded != null;
            block.requireItemCount(decoded.length);
            for (PersistentPhysicsBodyState state : decoded) {
                states.add(state.copy());
            }
        }
        return states.toArray(PersistentPhysicsBodyState[]::new);
    }

    @Nonnull
    static PersistentPhysicsJointState[] decodeJointBlocks(@Nonnull PersistentPhysicsStateBlock[] blocks) {
        List<PersistentPhysicsJointState> states = new ArrayList<>();
        for (PersistentPhysicsStateBlock block : blocks) {
            BsonValue items = block.inflate(KIND_JOINTS).get(PAYLOAD_ITEMS_KEY);
            PersistentPhysicsJointState[] decoded = JOINT_ARRAY_CODEC.decode(items, new ExtraInfo());
            assert decoded != null;
            block.requireItemCount(decoded.length);
            for (PersistentPhysicsJointState state : decoded) {
                states.add(state.copy());
            }
        }
        return states.toArray(PersistentPhysicsJointState[]::new);
    }

    @Nonnull
    PersistentPhysicsStateBlock copy() {
        PersistentPhysicsStateBlock copy = new PersistentPhysicsStateBlock();
        copy.kind = kind;
        copy.codec = codec;
        copy.compression = compression;
        copy.schemaVersion = schemaVersion;
        copy.blockIndex = blockIndex;
        copy.spaceId = spaceId;
        copy.itemCount = itemCount;
        copy.uncompressedBytes = uncompressedBytes;
        copy.compressedBytes = compressedBytes;
        copy.crc32 = crc32;
        copy.payload = copyPayload(payload);
        return copy;
    }

    @Nonnull
    public String getKind() {
        return kind;
    }

    @Nonnull
    public String getCodec() {
        return codec;
    }

    @Nonnull
    public String getCompression() {
        return compression;
    }

    @Nonnull
    public byte[] getPayload() {
        return copyPayload(payload);
    }

    @Nonnull
    private static PersistentPhysicsStateBlock compressed(@Nonnull String kind,
        int blockIndex,
        int spaceId,
        int itemCount,
        @Nonnull BsonDocument payloadDocument) {
        byte[] uncompressed = writeBson(payloadDocument);
        requireUncompressedByteLimit(uncompressed.length);
        byte[] compressed = Zstd.compress(uncompressed, ZSTD_COMPRESSION_LEVEL);

        PersistentPhysicsStateBlock block = new PersistentPhysicsStateBlock();
        block.kind = kind;
        block.codec = CODEC_BSON_ARRAY_V1;
        block.compression = COMPRESSION_ZSTD;
        block.schemaVersion = PersistentPhysicsWorldResource.CURRENT_SCHEMA_VERSION;
        block.blockIndex = blockIndex;
        block.spaceId = spaceId;
        block.itemCount = itemCount;
        block.uncompressedBytes = uncompressed.length;
        block.compressedBytes = compressed.length;
        block.crc32 = crc32(uncompressed);
        block.payload = compressed;
        return block;
    }

    @Nonnull
    private BsonDocument inflate(@Nonnull String expectedKind) {
        validateEnvelope(expectedKind);
        byte[] uncompressed = Zstd.decompress(payload, uncompressedBytes);
        if (uncompressed.length != uncompressedBytes) {
            throw new IllegalStateException("Persistent physics state block decompressed to "
                + uncompressed.length + " bytes, expected " + uncompressedBytes);
        }
        long actualCrc32 = crc32(uncompressed);
        if (actualCrc32 != crc32) {
            throw new IllegalStateException("Persistent physics state block checksum mismatch for "
                + kind + " block " + blockIndex);
        }
        return readBson(uncompressed);
    }

    private void validateEnvelope(@Nonnull String expectedKind) {
        if (!expectedKind.equals(kind)) {
            throw new IllegalStateException("Persistent physics state block kind mismatch: expected "
                + expectedKind + ", found " + kind);
        }
        if (!CODEC_BSON_ARRAY_V1.equals(codec)) {
            throw new IllegalStateException("Persistent physics state block codec is unsupported: " + codec);
        }
        if (!COMPRESSION_ZSTD.equals(compression)) {
            throw new IllegalStateException("Persistent physics state block compression is unsupported: " + compression);
        }
        if (schemaVersion != PersistentPhysicsWorldResource.CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("Persistent physics state block schema is unsupported: " + schemaVersion);
        }
        requireUncompressedByteLimit(uncompressedBytes);
        if (payload.length != compressedBytes) {
            throw new IllegalStateException("Persistent physics state block compressed size mismatch for "
                + kind + " block " + blockIndex);
        }
    }

    private static void requireUncompressedByteLimit(int byteCount) {
        if (byteCount > MAX_UNCOMPRESSED_BYTES) {
            throw new IllegalStateException(UNCOMPRESSED_BYTES_LIMIT_MESSAGE
                + ": " + byteCount + " > " + MAX_UNCOMPRESSED_BYTES);
        }
    }

    private void requireItemCount(int decodedCount) {
        if (decodedCount != itemCount) {
            throw new IllegalStateException("Persistent physics state block item count mismatch for "
                + kind + " block " + blockIndex + ": expected " + itemCount + ", decoded " + decodedCount);
        }
    }

    @Nonnull
    private static byte[] writeBson(@Nonnull BsonDocument document) {
        try (BasicOutputBuffer output = new BasicOutputBuffer()) {
            try (BsonBinaryWriter writer = new BsonBinaryWriter(output)) {
                BSON_DOCUMENT_CODEC.encode(writer, document, EncoderContext.builder().build());
            }
            return output.toByteArray();
        }
    }

    @Nonnull
    private static BsonDocument readBson(@Nonnull byte[] bytes) {
        try (BsonBinaryReader reader = new BsonBinaryReader(ByteBuffer.wrap(bytes))) {
            return BSON_DOCUMENT_CODEC.decode(reader, DecoderContext.builder().build());
        }
    }

    private static long crc32(@Nonnull byte[] bytes) {
        CRC32 crc32 = new CRC32();
        crc32.update(bytes);
        return crc32.getValue();
    }

    @Nonnull
    private static byte[] copyPayload(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return EMPTY_PAYLOAD;
        }
        return Arrays.copyOf(payload, payload.length);
    }

}
