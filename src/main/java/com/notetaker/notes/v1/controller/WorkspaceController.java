package com.notetaker.notes.v1.controller;

import com.notetaker.api.V1WorkspacesApiDelegate;
import com.notetaker.model.AddMemberRequest;
import com.notetaker.model.CreateWorkspaceRequest;
import com.notetaker.model.Workspace;
import com.notetaker.model.WorkspaceMember;
import com.notetaker.model.WorkspaceMembersResponse;
import com.notetaker.model.WorkspacesResponse;
import com.notetaker.notes.v1.mapper.WorkspaceMapper;
import com.notetaker.notes.v1.service.WorkspaceService;
import com.notetaker.notes.v1.service.WorkspaceService.MemberView;
import com.notetaker.notes.v1.service.WorkspaceService.MembershipView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkspaceController implements V1WorkspacesApiDelegate {

  private final WorkspaceService workspaceService;
  private final WorkspaceMapper workspaceMapper;

  @Override
  public ResponseEntity<Workspace> createWorkspace(CreateWorkspaceRequest createWorkspaceRequest) {
    MembershipView created = workspaceService.create(createWorkspaceRequest.getName());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(workspaceMapper.toModel(created.workspace(), created.role()));
  }

  @Override
  public ResponseEntity<WorkspacesResponse> listWorkspaces() {
    List<Workspace> workspaces = workspaceService.listMine().stream()
        .map(view -> workspaceMapper.toModel(view.workspace(), view.role()))
        .toList();
    return ResponseEntity.ok(WorkspacesResponse.builder().workspaces(workspaces).build());
  }

  @Override
  public ResponseEntity<WorkspaceMembersResponse> listWorkspaceMembers(Long id) {
    List<WorkspaceMember> members = workspaceService.listMembers(id).stream()
        .map(this::toModel)
        .toList();
    return ResponseEntity.ok(WorkspaceMembersResponse.builder().members(members).build());
  }

  @Override
  public ResponseEntity<WorkspaceMember> addWorkspaceMember(Long id, AddMemberRequest addMemberRequest) {
    String role = addMemberRequest.getRole() == null ? null : addMemberRequest.getRole().getValue();
    MemberView member = workspaceService.addMember(id, addMemberRequest.getEmployeeNumber(), role);
    HttpStatus status = member.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(toModel(member));
  }

  @Override
  public ResponseEntity<Void> removeWorkspaceMember(Long id, Long userId) {
    workspaceService.removeMember(id, userId);
    return ResponseEntity.noContent().build();
  }

  private WorkspaceMember toModel(MemberView view) {
    return workspaceMapper.toModel(view.user(), view.role(), view.joinedAt());
  }
}
