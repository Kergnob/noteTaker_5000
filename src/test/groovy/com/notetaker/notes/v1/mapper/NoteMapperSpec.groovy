package com.notetaker.notes.v1.mapper

import com.notetaker.model.SharePermission
import com.notetaker.notes.v1.repository.entity.NoteEntity
import com.notetaker.notes.v1.repository.entity.NoteShareEntity
import spock.lang.Specification

import java.time.Instant

class NoteMapperSpec extends Specification {

  NoteMapper mapper = new NoteMapper() {}

  def "maps a NoteEntity to the Note model preserving all fields"() {
    given:
    def created = Instant.parse('2026-01-01T00:00:00Z')
    def updated = Instant.parse('2026-01-02T00:00:00Z')
    def entity = new NoteEntity()
    entity.id = 'n1'
    entity.ownerId = '111'
    entity.title = 'T'
    entity.content = 'C'
    entity.completed = true
    entity.createdAt = created
    entity.updatedAt = updated

    when:
    def model = mapper.toModel(entity)

    then:
    model.id == 'n1'
    model.ownerId == '111'
    model.title == 'T'
    model.content == 'C'
    model.completed
    model.createdAt == created
    model.updatedAt == updated
  }

  def "maps a NoteShareEntity to the NoteShare model with #permission permission"() {
    given:
    def entity = new NoteShareEntity('n1', '222', permission)
    entity.createdAt = Instant.parse('2026-01-01T00:00:00Z')

    when:
    def model = mapper.toModel(entity)

    then:
    model.noteId == 'n1'
    model.sharedWithUserId == '222'
    model.permission == expected
    model.createdAt == entity.createdAt

    where:
    permission | expected
    'READ'     | SharePermission.READ
    'WRITE'    | SharePermission.WRITE
  }

  def "returns null when mapping a null note entity"() {
    expect:
    mapper.toModel((NoteEntity) null) == null
  }

  def "returns null when mapping a null share entity"() {
    expect:
    mapper.toModel((NoteShareEntity) null) == null
  }
}
