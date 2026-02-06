package com.deskit.deskit.livehost.common.utils;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class BooleanToYNConverter implements AttributeConverter<Boolean, String> {
    // 자바의 boolean을 DB의 char(1)으로 바꾸어 줌
    @Override
    public String convertToDatabaseColumn(Boolean attribute) {
        return (attribute != null && attribute) ? "Y" : "N";
    }

    // DB의 Char(1)을 자바의 boolean으로 바꾸어줌
    @Override
    public Boolean convertToEntityAttribute(String dbData) {
        return "Y".equalsIgnoreCase(dbData);
    }
}
