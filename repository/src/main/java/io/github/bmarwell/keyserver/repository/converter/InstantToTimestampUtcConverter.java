package io.github.bmarwell.keyserver.repository.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.sql.Timestamp;
import java.time.Instant;

@Converter
public class InstantToTimestampUtcConverter implements AttributeConverter<Instant, Timestamp> {

    @Override
    public Timestamp convertToDatabaseColumn(Instant attribute) {
        return new Timestamp(attribute.getEpochSecond());
    }

    @Override
    public Instant convertToEntityAttribute(Timestamp dbData) {
        return dbData.toInstant();
    }
}
