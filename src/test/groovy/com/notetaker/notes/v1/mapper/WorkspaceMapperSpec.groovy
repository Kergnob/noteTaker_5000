package com.notetaker.notes.v1.mapper

import com.notetaker.model.WorkspaceRole
import com.notetaker.notes.v1.repository.entity.AppUserEntity
import com.notetaker.notes.v1.repository.entity.WorkspaceEntity
import spock.lang.Specification

import java.time.Instant

class WorkspaceMapperSpec extends Specification {

  WorkspaceMapper mapper = new WorkspaceMapper() {}

  def "maps a WorkspaceEntity plus the caller's role to the Workspace model"() {
    given:
    def created = Instant.parse('2026-01-01T00:00:00Z')
    def modified = Instant.parse('2026-01-02T00:00:00Z')
    def entity = new WorkspaceEntity()
    entity.id = 12L
    entity.name = 'Dock 12 crew'
    entity.createdAt = created
    entity.modifiedAt = modified

    when:
    def model = mapper.toModel(entity, 'OWNER')

    then:
    model.id == 12L
    model.name == 'Dock 12 crew'
    model.role == WorkspaceRole.OWNER
    model.createdAt == created
    model.updatedAt == modified
  }

  def "maps an AppUserEntity membership to the WorkspaceMember model"() {
    given:
    def joined = Instant.parse('2026-01-01T00:00:00Z')
    def user = new AppUserEntity()
    user.id = 55L
    user.employeeNumber = '987654'
    user.displayName = 'Jane'

    when:
    def model = mapper.toModel(user, 'EDITOR', joined)

    then:
    model.userId == 55L
    model.employeeNumber == '987654'
    model.displayName == 'Jane'
    model.role == WorkspaceRole.EDITOR
    model.joinedAt == joined
  }

  def "returns null for null inputs"() {
    expect:
    mapper.toModel((WorkspaceEntity) null, 'OWNER') == null
    mapper.toModel((AppUserEntity) null, 'OWNER', Instant.now()) == null
  }
}
