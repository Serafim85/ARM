import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { SelectModule } from 'primeng/select';
import { AppRole } from '../../auth.service';
import { DeviceOptionSelectComponent } from '../../components/device-option-select/device-option-select.component';
import { UsersService } from '../../services/users.service';

@Component({
  selector: 'app-user-form-modal',
  standalone: true,
  imports: [
    FormsModule,
    ButtonModule,
    InputTextModule,
    PasswordModule,
    SelectModule,
    CheckboxModule,
    DeviceOptionSelectComponent,
  ],
  templateUrl: './user-form-modal.component.html',
  styleUrl: './user-form-modal.component.css',
})
export class UserFormModalComponent {
  protected readonly us = inject(UsersService);

  protected readonly createStatusOptions = [
    { label: 'Активен', value: 'ENABLED' },
    { label: 'Заблокирован', value: 'BLOCKED' },
  ];

  protected onBackdrop(): void {
    if (this.us.createUserLoading() || this.us.usersLoading()) return;
    this.us.closeUserFormSheet();
  }

  protected close(): void {
    if (this.us.createUserLoading() || this.us.usersLoading()) return;
    this.us.closeUserFormSheet();
  }

  protected saveProfile(): void {
    const target = this.us.editFormTarget();
    if (target) this.us.saveUserProfile(target);
  }

  protected resetPassword(): void {
    const target = this.us.editFormTarget();
    if (target) this.us.resetUserPassword(target);
  }

  protected hasCreateRole(role: AppRole): boolean {
    return this.us.hasCreateRole(role);
  }

  protected onCreateRoleChange(role: AppRole, checked: boolean): void {
    this.us.onCreateUserRoleChange(role, checked);
  }

  protected onCreateUserStatusChange(value: string | number | null): void {
    this.us.createUserEnabled.set(String(value) === 'ENABLED');
  }
}
