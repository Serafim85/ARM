import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { API_BASE_URL } from '../api-config';
import { AuthService, AppRole, AuthSession } from '../auth.service';
import { NotifierService } from '../notifier.service';
import {
  AdminAuditLogRecord,
  CreateUserRequest,
  UpdateUserProfileRequest,
  UserManagementRecord,
  UserStatusFilter,
} from '../models';

export type UserFormSheet = { kind: 'create', user?: undefined } | { kind: 'edit'; user: UserManagementRecord };

@Injectable({ providedIn: 'root' })
export class UsersService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly notify = inject(NotifierService);

  readonly appRoles: AppRole[] = ['ADMIN', 'OPERATOR', 'VIEWER'];

  readonly systemUsers = signal<UserManagementRecord[]>([]);
  readonly usersLoading = signal(false);
  readonly usersSearch = signal('');
  readonly usersRoleFilter = signal<AppRole | 'ALL'>('ALL');
  readonly usersStatusFilter = signal<UserStatusFilter>('ALL');
  readonly createUserEmail = signal('');
  readonly createUserDisplayName = signal('');
  readonly createUserPassword = signal('');
  readonly createUserEnabled = signal(true);
  readonly createUserRoles = signal<AppRole[]>(['VIEWER']);
  readonly createUserLoading = signal(false);
  /** Окно создания или редактирования пользователя (один попап). */
  readonly userFormSheet = signal<UserFormSheet | null>(null);
  readonly editUserEmail = signal('');
  readonly editUserDisplayName = signal('');
  readonly resetPasswordValue = signal('');
  readonly confirmBlockUser = signal<UserManagementRecord | null>(null);
  readonly generatedPassword = signal('');
  readonly auditLogs = signal<AdminAuditLogRecord[]>([]);

  readonly totalUsersCount = computed(() => this.systemUsers().length);
  readonly activeUsersCount = computed(() => this.systemUsers().filter((u) => u.enabled).length);
  readonly blockedUsersCount = computed(() => this.systemUsers().filter((u) => !u.enabled).length);

  readonly filteredSystemUsers = computed(() => {
    const query = this.usersSearch().trim().toLowerCase();
    const role = this.usersRoleFilter();
    const status = this.usersStatusFilter();
    return this.systemUsers().filter((user) => {
      const matchesQuery =
        !query ||
        [user.displayName, user.email, String(user.id), ...user.roles]
          .join(' ')
          .toLowerCase()
          .includes(query);
      const matchesRole = role === 'ALL' || user.roles.includes(role);
      const matchesStatus =
        status === 'ALL' ||
        (status === 'ACTIVE' && user.enabled) ||
        (status === 'BLOCKED' && !user.enabled);
      return matchesQuery && matchesRole && matchesStatus;
    });
  });

  hasRole(user: UserManagementRecord, role: AppRole): boolean {
    return user.roles.includes(role);
  }

  hasCreateRole(role: AppRole): boolean {
    return this.createUserRoles().includes(role);
  }

  canEditUserRoles(user: UserManagementRecord): boolean {
    return !this.usersLoading() && !this.isCurrentUser(user);
  }

  canToggleUserStatus(user: UserManagementRecord): boolean {
    return !this.usersLoading() && !this.isCurrentUser(user);
  }

  onCreateUserRoleChange(role: AppRole, checked: boolean): void {
    this.createUserRoles.update((current) => {
      const updated = checked ? [...current, role] : current.filter((r) => r !== role);
      return Array.from(new Set(updated));
    });
  }

  onUserRoleChange(user: UserManagementRecord, role: AppRole, checked: boolean): void {
    const roles = checked ? [...user.roles, role] : user.roles.filter((r) => r !== role);
    this.updateUserRoles(user, Array.from(new Set(roles)));
  }

  openCreateUserModal(): void {
    this.createUserEmail.set('');
    this.createUserDisplayName.set('');
    this.createUserPassword.set('');
    this.createUserEnabled.set(true);
    this.createUserRoles.set(['VIEWER']);
    this.userFormSheet.set({ kind: 'create' });
  }

  startEditUser(user: UserManagementRecord): void {
    this.editUserEmail.set(user.email);
    this.editUserDisplayName.set(user.displayName);
    this.resetPasswordValue.set('');
    this.generatedPassword.set('');
    this.userFormSheet.set({ kind: 'edit', user });
  }

  closeUserFormSheet(): void {
    this.userFormSheet.set(null);
    this.editUserEmail.set('');
    this.editUserDisplayName.set('');
    this.resetPasswordValue.set('');
    this.generatedPassword.set('');
    this.createUserEmail.set('');
    this.createUserDisplayName.set('');
    this.createUserPassword.set('');
    this.createUserEnabled.set(true);
    this.createUserRoles.set(['VIEWER']);
  }

  /** Пользователь в режиме редактирования (для вызовов API), иначе `null`. */
  editFormTarget(): UserManagementRecord | null {
    const s = this.userFormSheet();
    return s?.kind === 'edit' ? s.user : null;
  }

  toggleUserBlocked(user: UserManagementRecord): void {
    this.confirmBlockUser.set(user);
  }

  closeBlockConfirmModal(): void {
    this.confirmBlockUser.set(null);
  }

  generateTemporaryPassword(): void {
    const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%';
    const password = Array.from({ length: 12 }, () =>
      alphabet[Math.floor(Math.random() * alphabet.length)]
    ).join('');
    this.generatedPassword.set(password);
    this.resetPasswordValue.set(password);
  }

  private fallbackCopyText(text: string): boolean {
    try {
      const textarea = document.createElement('textarea');
      textarea.value = text;
      textarea.setAttribute('readonly', 'true');
      textarea.style.position = 'fixed';
      textarea.style.left = '-9999px';
      textarea.style.top = '0';
      document.body.appendChild(textarea);
      textarea.focus();
      textarea.select();
      const ok = document.execCommand('copy');
      document.body.removeChild(textarea);
      return ok;
    } catch {
      return false;
    }
  }

  copyGeneratedPassword(): void {
    const password = this.generatedPassword() || this.resetPasswordValue().trim();
    if (!password) {
      this.notify.warn('Сначала сгенерируйте или введите пароль.', 'Пользователи');
      return;
    }

    const clipboard = navigator.clipboard;
    if (clipboard?.writeText) {
      clipboard
        .writeText(password)
        .then(() => this.notify.success('Пароль скопирован в буфер обмена.', 'Пользователи'))
        .catch(() => {
          const ok = this.fallbackCopyText(password);
          if (ok) {
            this.notify.success('Пароль скопирован в буфер обмена.', 'Пользователи');
            return;
          }
          this.notify.error('Не удалось скопировать пароль в буфер обмена.', 'Пользователи');
        });
      return;
    }

    const ok = this.fallbackCopyText(password);
    if (ok) {
      this.notify.success('Пароль скопирован в буфер обмена.', 'Пользователи');
      return;
    }
    this.notify.error('Не удалось скопировать пароль в буфер обмена.', 'Пользователи');
  }

  loadSystemUsers(): void {
    this.usersLoading.set(true);
    this.http.get<UserManagementRecord[]>(`${this.apiBaseUrl}/api/admin/users`).subscribe({
      next: (users) => {
        this.systemUsers.set(users);
        this.loadAuditLogs();
        this.usersLoading.set(false);
      },
      error: (error) => {
        this.notify.error(this.resolveError(error, 'Не удалось загрузить список пользователей.'), 'Пользователи');
        this.usersLoading.set(false);
      },
    });
  }

  loadAuditLogs(): void {
    this.http.get<AdminAuditLogRecord[]>(`${this.apiBaseUrl}/api/admin/users/audit-logs`).subscribe({
      next: (logs) => this.auditLogs.set(logs),
      error: () => this.auditLogs.set([]),
    });
  }

  createUser(): void {
    const payload: CreateUserRequest = {
      email: this.createUserEmail().trim(),
      displayName: this.createUserDisplayName().trim(),
      password: this.createUserPassword().trim(),
      roles: this.createUserRoles(),
      enabled: this.createUserEnabled(),
    };

    if (!payload.email || !payload.displayName || !payload.password) {
      this.notify.warn('Заполните email, имя пользователя и пароль.', 'Пользователи');
      return;
    }
    if (payload.roles.length === 0) {
      this.notify.warn('Назначьте хотя бы одну роль новому пользователю.', 'Пользователи');
      return;
    }

    this.createUserLoading.set(true);

    this.http.post<UserManagementRecord>(`${this.apiBaseUrl}/api/admin/users`, payload).subscribe({
      next: (user) => {
        this.systemUsers.update((c) => [user, ...c]);
        this.notify.success(`Пользователь ${user.email} создан.`, 'Пользователи');
        this.loadAuditLogs();
        this.createUserLoading.set(false);
        this.closeUserFormSheet();
      },
      error: (error) => {
        this.notify.error(this.resolveError(error, 'Не удалось создать пользователя.'), 'Пользователи');
        this.createUserLoading.set(false);
      },
    });
  }

  updateUserRoles(user: UserManagementRecord, roles: AppRole[]): void {
    this.usersLoading.set(true);
    this.http
      .put<UserManagementRecord>(`${this.apiBaseUrl}/api/admin/users/${user.id}/roles`, { roles })
      .subscribe({
        next: (updated) => {
          this.applyUpdatedUser(updated);
          this.notify.success(`Роли пользователя ${updated.email} обновлены.`, 'Пользователи');
          this.loadAuditLogs();
          this.usersLoading.set(false);
        },
        error: (error) => {
          this.notify.error(this.resolveError(error, 'Не удалось обновить роли пользователя.'), 'Пользователи');
          this.usersLoading.set(false);
        },
      });
  }

  confirmToggleUserBlocked(): void {
    const user = this.confirmBlockUser();
    if (!user) return;

    this.usersLoading.set(true);

    this.http
      .put<UserManagementRecord>(`${this.apiBaseUrl}/api/admin/users/${user.id}/status`, {
        enabled: !user.enabled,
      })
      .subscribe({
        next: (updated) => {
          this.applyUpdatedUser(updated);
          this.notify.success(
            updated.enabled
              ? `Пользователь ${updated.email} разблокирован.`
              : `Пользователь ${updated.email} заблокирован.`,
            'Пользователи'
          );
          this.confirmBlockUser.set(null);
          this.loadAuditLogs();
          this.usersLoading.set(false);
        },
        error: (error) => {
          this.notify.error(this.resolveError(error, 'Не удалось изменить статус пользователя.'), 'Пользователи');
          this.usersLoading.set(false);
        },
      });
  }

  saveUserProfile(user: UserManagementRecord): void {
    const payload: UpdateUserProfileRequest = {
      email: this.editUserEmail().trim(),
      displayName: this.editUserDisplayName().trim(),
    };
    if (!payload.email || !payload.displayName) {
      this.notify.warn('Заполните имя и email пользователя.', 'Пользователи');
      return;
    }

    this.usersLoading.set(true);

    this.http
      .put<UserManagementRecord>(`${this.apiBaseUrl}/api/admin/users/${user.id}/profile`, payload)
      .subscribe({
        next: (updated) => {
          this.applyUpdatedUser(updated);
          this.syncSession(user, updated);
          this.notify.success(`Профиль пользователя ${updated.email} обновлен.`, 'Пользователи');
          this.loadAuditLogs();
          this.closeUserFormSheet();
          this.usersLoading.set(false);
        },
        error: (error) => {
          this.notify.error(this.resolveError(error, 'Не удалось обновить профиль пользователя.'), 'Пользователи');
          this.usersLoading.set(false);
        },
      });
  }

  resetUserPassword(user: UserManagementRecord): void {
    const password = this.resetPasswordValue().trim();
    if (!password) {
      this.notify.warn('Введите новый пароль для пользователя.', 'Пользователи');
      return;
    }
    this.usersLoading.set(true);

    this.http
      .put(`${this.apiBaseUrl}/api/admin/users/${user.id}/password`, { password })
      .subscribe({
        next: () => {
          this.notify.success(`Пароль пользователя ${user.email} обновлен.`, 'Пользователи');
          this.resetPasswordValue.set('');
          this.generatedPassword.set('');
          this.loadAuditLogs();
          this.usersLoading.set(false);
        },
        error: (error) => {
          this.notify.error(this.resolveError(error, 'Не удалось сбросить пароль пользователя.'), 'Пользователи');
          this.usersLoading.set(false);
        },
      });
  }

  private isCurrentUser(user: UserManagementRecord): boolean {
    return user.email.toLowerCase() === (this.auth.authSession()?.email ?? '').toLowerCase();
  }

  private applyUpdatedUser(updated: UserManagementRecord): void {
    this.systemUsers.update((c) => c.map((u) => (u.id === updated.id ? updated : u)));
  }

  private syncSession(previous: UserManagementRecord, updated: UserManagementRecord): void {
    const session = this.auth.authSession();
    if (!session || session.email.toLowerCase() !== previous.email.toLowerCase()) return;
    const next: AuthSession = { ...session, email: updated.email, displayName: updated.displayName };
    this.auth.updateSession(next);
  }

  private resolveError(error: { status?: number; error?: { message?: string } }, fallback: string): string {
    if (error?.status === 401) return 'Сессия истекла. Выполните вход заново.';
    if (error?.status === 403) return 'Доступ к управлению пользователями есть только у администратора.';
    if (error?.status === 404) return 'Backend еще не был перезапущен с новым API управления пользователями.';
    return error?.error?.message ?? fallback;
  }
}
