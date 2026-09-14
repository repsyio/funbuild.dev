import { Component, OnInit, inject, signal } from '@angular/core';
import { Role, UserSummary } from '../../core/models';
import { UserService } from '../../core/user.service';

@Component({
  selector: 'app-admin-users',
  templateUrl: './admin-users.html',
})
export class AdminUsers implements OnInit {
  private readonly userService = inject(UserService);

  protected readonly users = signal<UserSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly roles: Role[] = ['MEMBER', 'ADMIN'];

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.userService.list().subscribe((users) => {
      this.users.set(users);
      this.loading.set(false);
    });
  }

  changeRole(user: UserSummary, role: Role): void {
    if (role === user.role) {
      return;
    }
    this.userService.updateRole(user.id, role).subscribe((updated) => {
      this.users.set(this.users().map((u) => (u.id === updated.id ? updated : u)));
    });
  }

  delete(user: UserSummary): void {
    if (!confirm(`Delete ${user.displayName}? This also removes their submissions and votes.`)) {
      return;
    }
    this.userService.delete(user.id).subscribe(() => {
      this.users.set(this.users().filter((u) => u.id !== user.id));
    });
  }
}
