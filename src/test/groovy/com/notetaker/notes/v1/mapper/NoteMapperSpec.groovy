package com.notetaker.notes.v1.mapper

import com.notetaker.notes.v1.repository.entity.NoteEntity
import spock.lang.Specification

import java.time.Instant

class NoteMapperSpec extends Specification {

  NoteMapper mapper = new NoteMapper() {}

  def "maps a NoteEntity to the Note model preserving all fields"() {
    given:
    def created = Instant.parse('2026-01-01T00:00:00Z')
    def updated = Instant.parse('2026-01-02T00:00:00Z')
    def entity = new NoteEntity()
    entity.id = 1024L
    entity.workspaceId = 12L
    entity.title = 'T'
    entity.content = 'C'
    entity.completed = true
    entity.createdByUserId = 55L
    entity.createdAt = created
    entity.updatedAt = updated

    when:
    def model = mapper.toModel(entity)

    then:
    model.id == 1024L
    model.workspaceId == 12L
    model.title == 'T'
    model.content == 'C'
    model.completed
    model.createdByUserId == 55L
    model.createdAt == created
    model.updatedAt == updated
    !model.deletedAt.isPresent()
  }

  def "maps deletedAt when the note is trashed"() {
    given:
    def deleted = Instant.parse('2026-01-03T00:00:00Z')
    def entity = new NoteEntity()
    entity.id = 1L
    entity.workspaceId = 2L
    entity.title = 'T'
    entity.completed = false
    entity.createdByUserId = 3L
    entity.deletedAt = deleted

    when:
    def model = mapper.toModel(entity)

    then:
    model.deletedAt.isPresent()
    model.deletedAt.get() == deleted
  }

  def "returns null when mapping a null note entity"() {
    expect:
    mapper.toModel(null) == null
  }
}
