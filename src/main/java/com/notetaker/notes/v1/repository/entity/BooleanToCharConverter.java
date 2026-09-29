package com.notetaker.notes.v1.repository.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** DANSAC flag mapping: Boolean &lt;-&gt; CHAR(1) 'T'/'F'. */
@Converter
public class BooleanToCharConverter implements AttributeConverter<Boolean, Character> {

  @Override
  public Character convertToDatabaseColumn(Boolean value) {
    return (value != null && value) ? 'T' : 'F';
  }

  @Override
  public Boolean convertToEntityAttribute(Character dbValue) {
    return dbValue != null && (dbValue == 'T' || dbValue == 't');
  }
}
