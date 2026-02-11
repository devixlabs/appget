package dev.appget.util;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.MessageOrBuilder;

/**
 * Builds sample protobuf messages with generic default values.
 * Uses DynamicMessage to populate fields based on descriptor metadata.
 */
public class TestDataBuilder {

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
            default:
                return field.getDefaultValue();
        }
    }
}
