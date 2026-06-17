package dev.hytalemodding.impulse.core.internal.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.codec.util.RawJsonReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class PersistentBodyRuntimeStateCodec implements Codec<PersistentBodyRuntimeStateDto> {

    static final PersistentBodyRuntimeStateCodec INSTANCE =
        new PersistentBodyRuntimeStateCodec();

    private static final byte PACKED_VERSION = 1;
    private static final int PACKED_FLOATS = 13;
    private static final int PACKED_BYTES = 1 + PACKED_FLOATS * Float.BYTES + 1;

    private PersistentBodyRuntimeStateCodec() {
    }

    @Override
    public PersistentBodyRuntimeStateDto decode(@Nonnull BsonValue value,
        @Nonnull ExtraInfo extraInfo) {
        if (Codec.isNullBsonValue(value)) {
            return new PersistentBodyRuntimeStateDto();
        }
        if (value.isString()) {
            return decodePacked(value.asString().getValue());
        }
        if (value.isDocument()) {
            return decodeLegacyDocument(value.asDocument());
        }
        throw new IllegalArgumentException("Persistent body runtime state must be a string");
    }

    @Override
    public BsonValue encode(@Nonnull PersistentBodyRuntimeStateDto value,
        @Nonnull ExtraInfo extraInfo) {
        return new BsonString(encodePacked(value));
    }

    @Override
    public PersistentBodyRuntimeStateDto decodeJson(@Nonnull RawJsonReader reader,
        @Nonnull ExtraInfo extraInfo) throws IOException {
        reader.consumeWhiteSpace();
        int next = reader.peek();
        if (next == '"') {
            return decodePacked(reader.readString());
        }
        if (next == 'n') {
            readNullToken(reader);
            return new PersistentBodyRuntimeStateDto();
        }
        if (next == '{') {
            return decodeLegacyObject(reader);
        }
        throw new IOException("Persistent body runtime state must be a string or object");
    }

    @Override
    public Schema toSchema(@Nonnull SchemaContext context) {
        return Codec.STRING.toSchema(context);
    }

    @Nonnull
    private static String encodePacked(@Nonnull PersistentBodyRuntimeStateDto dto) {
        Objects.requireNonNull(dto, "dto");
        ByteBuffer buffer = ByteBuffer.allocate(PACKED_BYTES).order(ByteOrder.BIG_ENDIAN);
        buffer.put(PACKED_VERSION);
        Vector3f position = dto.getPosition();
        buffer.putFloat(position.x).putFloat(position.y).putFloat(position.z);
        Quaternionf rotation = dto.getRotation();
        buffer.putFloat(rotation.x).putFloat(rotation.y).putFloat(rotation.z).putFloat(rotation.w);
        Vector3f linearVelocity = dto.getLinearVelocity();
        buffer.putFloat(linearVelocity.x).putFloat(linearVelocity.y).putFloat(linearVelocity.z);
        Vector3f angularVelocity = dto.getAngularVelocity();
        buffer.putFloat(angularVelocity.x).putFloat(angularVelocity.y).putFloat(angularVelocity.z);
        buffer.put((byte) (dto.isSleeping() ? 1 : 0));
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    @Nonnull
    private static PersistentBodyRuntimeStateDto decodePacked(@Nonnull String encoded) {
        byte[] bytes = Base64.getDecoder().decode(encoded);
        if (bytes.length != PACKED_BYTES) {
            throw new IllegalArgumentException("Packed runtime state has invalid length");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        byte version = buffer.get();
        if (version != PACKED_VERSION) {
            throw new IllegalArgumentException("Unsupported packed runtime state version");
        }
        Vector3f position = new Vector3f(buffer.getFloat(), buffer.getFloat(), buffer.getFloat());
        Quaternionf rotation = new Quaternionf(buffer.getFloat(),
            buffer.getFloat(),
            buffer.getFloat(),
            buffer.getFloat());
        Vector3f linearVelocity = new Vector3f(buffer.getFloat(),
            buffer.getFloat(),
            buffer.getFloat());
        Vector3f angularVelocity = new Vector3f(buffer.getFloat(),
            buffer.getFloat(),
            buffer.getFloat());
        boolean sleeping = buffer.get() != 0;
        return new PersistentBodyRuntimeStateDto(position,
            rotation,
            linearVelocity,
            angularVelocity,
            sleeping);
    }

    @Nonnull
    private static PersistentBodyRuntimeStateDto decodeLegacyDocument(@Nonnull BsonDocument document) {
        return new PersistentBodyRuntimeStateDto(vector(document.getDocument("Position",
                new BsonDocument())),
            quaternion(document.getDocument("Rotation", new BsonDocument())),
            vector(document.getDocument("LinearVelocity", new BsonDocument())),
            vector(document.getDocument("AngularVelocity", new BsonDocument())),
            document.getBoolean("Sleeping", BsonBoolean.FALSE).getValue());
    }

    @Nonnull
    private static Vector3f vector(@Nonnull BsonDocument document) {
        return new Vector3f(number(document, "X", 0.0f),
            number(document, "Y", 0.0f),
            number(document, "Z", 0.0f));
    }

    @Nonnull
    private static Quaternionf quaternion(@Nonnull BsonDocument document) {
        return new Quaternionf(number(document, "X", 0.0f),
            number(document, "Y", 0.0f),
            number(document, "Z", 0.0f),
            number(document, "W", 1.0f));
    }

    private static float number(@Nonnull BsonDocument document,
        @Nonnull String key,
        float fallback) {
        BsonValue value = document.get(key);
        return value != null && value.isNumber() ? (float) value.asNumber().doubleValue() : fallback;
    }

    @Nonnull
    private static PersistentBodyRuntimeStateDto decodeLegacyObject(@Nonnull RawJsonReader reader)
        throws IOException {
        Vector3f position = new Vector3f();
        Quaternionf rotation = new Quaternionf();
        Vector3f linearVelocity = new Vector3f();
        Vector3f angularVelocity = new Vector3f();
        boolean sleeping = false;
        reader.expect('{');
        reader.consumeWhiteSpace();
        if (reader.tryConsume('}')) {
            return new PersistentBodyRuntimeStateDto(position,
                rotation,
                linearVelocity,
                angularVelocity,
                sleeping);
        }
        while (true) {
            reader.consumeWhiteSpace();
            String key = reader.readString();
            reader.consumeWhiteSpace();
            reader.expect(':');
            reader.consumeWhiteSpace();
            switch (key) {
                case "Position" -> position = readVector(reader);
                case "Rotation" -> rotation = readQuaternion(reader);
                case "LinearVelocity" -> linearVelocity = readVector(reader);
                case "AngularVelocity" -> angularVelocity = readVector(reader);
                case "Sleeping" -> sleeping = readBooleanToken(reader);
                default -> reader.skipValue();
            }
            reader.consumeWhiteSpace();
            if (reader.tryConsume('}')) {
                break;
            }
            reader.expect(',');
        }
        return new PersistentBodyRuntimeStateDto(position,
            rotation,
            linearVelocity,
            angularVelocity,
            sleeping);
    }

    @Nonnull
    private static Vector3f readVector(@Nonnull RawJsonReader reader) throws IOException {
        float x = 0.0f;
        float y = 0.0f;
        float z = 0.0f;
        reader.expect('{');
        reader.consumeWhiteSpace();
        if (reader.tryConsume('}')) {
            return new Vector3f();
        }
        while (true) {
            reader.consumeWhiteSpace();
            String key = reader.readString();
            reader.consumeWhiteSpace();
            reader.expect(':');
            reader.consumeWhiteSpace();
            switch (key) {
                case "X" -> x = readFloatToken(reader);
                case "Y" -> y = readFloatToken(reader);
                case "Z" -> z = readFloatToken(reader);
                default -> reader.skipValue();
            }
            reader.consumeWhiteSpace();
            if (reader.tryConsume('}')) {
                break;
            }
            reader.expect(',');
        }
        return new Vector3f(x, y, z);
    }

    @Nonnull
    private static Quaternionf readQuaternion(@Nonnull RawJsonReader reader) throws IOException {
        float x = 0.0f;
        float y = 0.0f;
        float z = 0.0f;
        float w = 1.0f;
        reader.expect('{');
        reader.consumeWhiteSpace();
        if (reader.tryConsume('}')) {
            return new Quaternionf();
        }
        while (true) {
            reader.consumeWhiteSpace();
            String key = reader.readString();
            reader.consumeWhiteSpace();
            reader.expect(':');
            reader.consumeWhiteSpace();
            switch (key) {
                case "X" -> x = readFloatToken(reader);
                case "Y" -> y = readFloatToken(reader);
                case "Z" -> z = readFloatToken(reader);
                case "W" -> w = readFloatToken(reader);
                default -> reader.skipValue();
            }
            reader.consumeWhiteSpace();
            if (reader.tryConsume('}')) {
                break;
            }
            reader.expect(',');
        }
        return new Quaternionf(x, y, z, w);
    }

    private static float readFloatToken(@Nonnull RawJsonReader reader) throws IOException {
        StringBuilder token = new StringBuilder(32);
        while (true) {
            int next = reader.peek();
            if (!isNumberCharacter(next)) {
                break;
            }
            token.append((char) reader.read());
        }
        if (token.isEmpty()) {
            throw new IOException("Expected persisted float value");
        }
        try {
            return Float.parseFloat(token.toString());
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid persisted float value: " + token, exception);
        }
    }

    private static boolean readBooleanToken(@Nonnull RawJsonReader reader) throws IOException {
        String token = readWordToken(reader);
        return switch (token.toString()) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IOException("Invalid persisted boolean value: " + token);
        };
    }

    private static void readNullToken(@Nonnull RawJsonReader reader) throws IOException {
        String token = readWordToken(reader);
        if (!"null".equals(token)) {
            throw new IOException("Invalid persisted null value: " + token);
        }
    }

    @Nonnull
    private static String readWordToken(@Nonnull RawJsonReader reader) throws IOException {
        StringBuilder token = new StringBuilder(5);
        while (true) {
            int next = reader.peek();
            if (!isWordCharacter(next)) {
                break;
            }
            token.append((char) reader.read());
        }
        return token.toString();
    }

    private static boolean isWordCharacter(int value) {
        return value >= 'a' && value <= 'z' || value >= 'A' && value <= 'Z';
    }

    private static boolean isNumberCharacter(int value) {
        return value == '-'
            || value == '+'
            || value == '.'
            || value == 'e'
            || value == 'E'
            || value >= '0' && value <= '9';
    }
}
