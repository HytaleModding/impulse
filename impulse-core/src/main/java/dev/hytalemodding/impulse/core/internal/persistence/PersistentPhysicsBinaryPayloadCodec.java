package dev.hytalemodding.impulse.core.internal.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.exception.CodecException;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.codec.schema.config.StringSchema;
import com.hypixel.hytale.codec.util.RawJsonReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import javax.annotation.Nonnull;
import org.bson.BsonBinary;
import org.bson.BsonValue;

/**
 * Binary payload codec for compressed physics state blocks.
 */
final class PersistentPhysicsBinaryPayloadCodec implements Codec<byte[]> {

    private static final byte[] EMPTY_PAYLOAD = new byte[0];

    @Nonnull
    @Override
    public byte[] decode(@Nonnull BsonValue bsonValue, ExtraInfo extraInfo) {
        return copyPayload(bsonValue.asBinary().getData());
    }

    @Nonnull
    @Override
    public BsonValue encode(@Nonnull byte[] bytes, ExtraInfo extraInfo) {
        return new BsonBinary(bytes);
    }

    @Nonnull
    @Override
    public byte[] decodeJson(@Nonnull RawJsonReader reader, ExtraInfo extraInfo) throws IOException {
        reader.consumeWhiteSpace();
        if (reader.peekFor('"')) {
            return Base64.getDecoder().decode(reader.readString());
        }
        return decodeExtendedBinaryObject(reader);
    }

    @Nonnull
    @Override
    public Schema toSchema(@Nonnull SchemaContext context) {
        StringSchema base64 = new StringSchema();
        base64.setPattern(Codec.BASE64_PATTERN);
        base64.setTitle("Binary payload");
        return base64;
    }

    @Nonnull
    private static byte[] decodeExtendedBinaryObject(@Nonnull RawJsonReader reader) throws IOException {
        reader.expect('{');
        reader.consumeWhiteSpace();

        byte[] payload = null;
        while (true) {
            String key = reader.readString();
            reader.consumeWhiteSpace();
            reader.expect(':');
            reader.consumeWhiteSpace();

            if ("$binary".equals(key)) {
                payload = decodeBinaryValue(reader);
            } else {
                reader.skipValue();
            }

            reader.consumeWhiteSpace();
            if (reader.tryConsumeOrExpect('}', ',')) {
                if (payload == null) {
                    throw new CodecException("Expected '$binary' field");
                }
                return payload;
            }
            reader.consumeWhiteSpace();
        }
    }

    @Nonnull
    private static byte[] decodeBinaryValue(@Nonnull RawJsonReader reader) throws IOException {
        if (reader.peekFor('"')) {
            return Base64.getDecoder().decode(reader.readString());
        }

        reader.expect('{');
        reader.consumeWhiteSpace();

        String base64 = null;
        while (true) {
            String key = reader.readString();
            reader.consumeWhiteSpace();
            reader.expect(':');
            reader.consumeWhiteSpace();

            if ("base64".equals(key)) {
                base64 = reader.readString();
            } else {
                reader.skipValue();
            }

            reader.consumeWhiteSpace();
            if (reader.tryConsumeOrExpect('}', ',')) {
                if (base64 == null) {
                    throw new CodecException("Expected '$binary.base64' field");
                }
                return Base64.getDecoder().decode(base64);
            }
            reader.consumeWhiteSpace();
        }
    }

    @Nonnull
    private static byte[] copyPayload(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return EMPTY_PAYLOAD;
        }
        return Arrays.copyOf(payload, payload.length);
    }
}
