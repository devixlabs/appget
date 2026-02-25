package dev.appget.util;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.MessageOrBuilder;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Builds sample protobuf messages with generic default values.
 * Uses DynamicMessage to populate fields based on descriptor metadata.
 */
public class DefaultDataBuilder {

    public MessageOrBuilder buildSampleMessage(Descriptors.Descriptor descriptor) {
        DynamicMessage.Builder builder = DynamicMessage.newBuilder(descriptor);

        for (Descriptors.FieldDescriptor field : descriptor.getFields()) {
            builder.setField(field, getDefaultValueForField(field));
        }

        return builder.build();
    }

    private Object getDefaultValueForField(Descriptors.FieldDescriptor field) {
        switch (field.getJavaType()) {
            case STRING:
                return "Sample_" + field.getName();
            case INT:
                return 42;
            case LONG:
                return 42L;
            case DOUBLE:
                return 42.0;
            case BOOLEAN:
                return true;
            case MESSAGE: {
                // Handle appget.common.Decimal message type
                Descriptors.Descriptor msgDesc = field.getMessageType();
                if ("Decimal".equals(msgDesc.getName())) {
                    return buildSampleDecimal(msgDesc);
                }
                // For all other message types (e.g. google.protobuf.Timestamp), return an empty instance
                return DynamicMessage.newBuilder(msgDesc).build();
            }
            default:
                return field.getDefaultValue();
        }
    }

    /**
     * Build a sample Decimal message representing the value 42.0.
     * Decimal { bytes unscaled = 1; int32 scale = 2; }
     */
    private Object buildSampleDecimal(Descriptors.Descriptor decimalDesc) {
        DynamicMessage.Builder decimalBuilder = DynamicMessage.newBuilder(decimalDesc);
        Descriptors.FieldDescriptor unscaledField = decimalDesc.findFieldByName("unscaled");
        Descriptors.FieldDescriptor scaleField = decimalDesc.findFieldByName("scale");
        if (unscaledField != null && scaleField != null) {
            BigDecimal bd = new BigDecimal("42.0");
            BigInteger unscaled = bd.unscaledValue();
            int scale = bd.scale();
            decimalBuilder.setField(unscaledField, ByteString.copyFrom(unscaled.toByteArray()));
            decimalBuilder.setField(scaleField, scale);
        }
        return decimalBuilder.build();
    }
}
