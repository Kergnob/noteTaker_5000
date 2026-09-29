package com.notetaker.notes.v1.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.notetaker.exception.ForbiddenException;
import com.notetaker.exception.NotFoundException;
import com.notetaker.notes.v1.repository.WorkspaceMemberRepository;
import com.notetaker.notes.v1.repository.WorkspaceRepository;
import com.notetaker.notes.v1.repository.entity.AppUserEntity;
import com.notetaker.notes.v1.repository.entity.WorkspaceEntity;
import com.notetaker.notes.v1.repository.entity.WorkspaceMemberEntity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Workspace lifecycle plus membership management (spaces/groups of collaborating users). */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WorkspaceService {

  private final WorkspaceRepository workspaceRepository;
  private final WorkspaceMemberRepository memberRepository;
  private final UserService userService;

  /** A workspace paired with the calling user's role in it. */
  public record MembershipView(WorkspaceEntity workspace, String role) {}

  /** A member of a workspace: the resolved user plus their role and join time. */
  public record MemberView(AppUserEntity user, String role, Instant joinedAt, boolean created) {}

  public MembershipView create(String name) {
    AppUserEntity caller = userService.currentUser();
    WorkspaceEntity workspace = new WorkspaceEntity();
    workspace.setName(name);
    workspace = workspaceRepository.save(workspace);
    memberRepository.save(
        new WorkspaceMemberEntity(workspace.getId(), caller.getId(), WorkspaceRole.OWNER));
    log.info("Created workspace {} by user {}", workspace.getId(), caller.getId());
    return new MembershipView(workspace, WorkspaceRole.OWNER);
  }

  @Transactional(readOnly = true)
  public List<MembershipView> listMine() {
    Long userId = userService.currentUser().getId();
    List<WorkspaceMemberEntity> memberships = memberRepository.findByUserId(userId);
    Map<Long, WorkspaceEntity> workspaces = workspaceRepository
        .findAllById(memberships.stream().map(WorkspaceMemberEntity::getWorkspaceId).toList())
        .stream()
        .collect(Collectors.toMap(WorkspaceEntity::getId, Function.identity()));
    return memberships.stream()
        .filter(m -> workspaces.containsKey(m.getWorkspaceId()))
        .map(m -> new MembershipView(workspaces.get(m.getWorkspaceId()), m.getRole()))
        .sorted(Comparator.comparing(v -> v.workspace().getId()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<MemberView> listMembers(Long workspaceId) {
    requireMember(workspaceId);
    return toMemberViews(memberRepository.findByWorkspaceId(workspaceId));
  }

  public MemberView addMember(Long workspaceId, String employeeNumber, String role) {
    requireOwner(workspaceId);
    String resolvedRole = (role == null || role.isBlank()) ? WorkspaceRole.VIEWER : role.trim();
    if (!WorkspaceRole.isValid(resolvedRole)) {
      throw new IllegalArgumentException("Role must be one of " + WorkspaceRole.ALL);
    }
    AppUserEntity target = userService.getOrCreate(employeeNumber);
    WorkspaceMemberEntity existing = memberRepository
        .findByWorkspaceIdAndUserId(workspaceId, target.getId())
        .orElse(null);
    boolean created = existing == null;
    WorkspaceMemberEntity member = created
        ? new WorkspaceMemberEntity(workspaceId, target.getId(), resolvedRole)
        : existing;
    member.setRole(resolvedRole);
    member = memberRepository.save(member);
    log.info("{} member {} in workspace {} with role {}",
      created ? "Added" : "Updated", target.getId(), workspaceId, member.getRole());
    return new MemberView(target, member.getRole(), member.getCreatedAt(), created);
  }

  public void removeMember(Long workspaceId, Long userId) {
    requireOwner(workspaceId);
    List<WorkspaceMemberEntity> members = memberRepository.findByWorkspaceId(workspaceId);
    WorkspaceMemberEntity target = members.stream()
        .filter(m -> m.getUserId().equals(userId))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("User " + userId + " is not a member of workspace " + workspaceId));
    boolean lastOwner = WorkspaceRole.OWNER.equals(target.getRole())
        && members.stream().filter(m -> WorkspaceRole.OWNER.equals(m.getRole())).count() == 1;
    if (lastOwner) {
      throw new ForbiddenException("Cannot remove the last owner of workspace " + workspaceId);
    }
    memberRepository.delete(target);
    log.info("Removed member {} from workspace {}", userId, workspaceId);
  }

  private List<MemberView> toMemberViews(List<WorkspaceMemberEntity> members) {
    Map<Long, AppUserEntity> users = userService
        .findAll(members.stream().map(WorkspaceMemberEntity::getUserId).toList())
        .stream()
        .collect(Collectors.toMap(AppUserEntity::getId, Function.identity()));
    return members.stream()
        .filter(m -> users.containsKey(m.getUserId()))
        .map(m -> new MemberView(users.get(m.getUserId()), m.getRole(), m.getCreatedAt(), false))
        .sorted(Comparator.comparing(v -> v.user().getId()))
        .toList();
  }

  private WorkspaceMemberEntity requireMember(Long workspaceId) {
    Long userId = userService.currentUser().getId();
    return memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
        .orElseThrow(() -> new ForbiddenException("You are not a member of workspace " + workspaceId));
  }

  private void requireOwner(Long workspaceId) {
    WorkspaceMemberEntity membership = requireMember(workspaceId);
    if (!WorkspaceRole.OWNER.equals(membership.getRole())) {
      throw new ForbiddenException("Only an owner may manage workspace " + workspaceId);
    }
  }
}
