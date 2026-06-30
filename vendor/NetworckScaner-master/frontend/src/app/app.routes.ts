import { Routes } from '@angular/router';
import { adminGuard } from './admin.guard';
import { authGuard } from './auth.guard';
import { guestGuard } from './guest.guard';
import { MainWorkspaceComponent } from './main-workspace.component';
import { DeviceCardComponent } from './pages/monitoring-page/device-card/device-card.component';
import { DeviceCardTabShellComponent } from './pages/monitoring-page/device-card/device-card-tab-shell.component';
import { EventsPageComponent } from './pages/events-page/events-page.component';
import { LoginPageComponent } from './pages/login-page/login-page.component';
import { MonitoringPageComponent } from './pages/monitoring-page/monitoring-page.component';
import { MonitoringTemplatesPageComponent } from './pages/monitoring-templates-page/monitoring-templates-page.component';
import { MonitoringTemplateDetailsPageComponent } from './pages/monitoring-templates-page/monitoring-template-details-page.component';
import { ScanPageComponent } from './pages/scan-page/scan-page.component';
import { ScanJobsPageComponent } from './pages/scan-jobs-page/scan-jobs-page.component';
import { DashboardDetailPageComponent } from './pages/dashboards-page/dashboard-detail-page.component';
import { DashboardsListPageComponent } from './pages/dashboards-page/dashboards-list-page.component';
import { UsersPageComponent } from './pages/users-page/users-page.component';
import { SystemSettingsPageComponent } from './pages/system-settings-page/system-settings-page.component';
import { systemSettingsGuard } from './system-settings.guard';
import { topologyPageCanDeactivate } from './pages/topology-page/topology-page.can-deactivate';

export const routes: Routes = [
  { path: 'login', canActivate: [guestGuard], component: LoginPageComponent },
  {
    path: '',
    canActivate: [authGuard],
    component: MainWorkspaceComponent,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'scan' },
      { path: 'scan', component: ScanPageComponent },
      { path: 'scan-jobs', component: ScanJobsPageComponent },
      {
        path: 'monitoring/:id',
        component: DeviceCardComponent,
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'info' },
          { path: 'info', component: DeviceCardTabShellComponent, data: { deviceTab: 'info' } },
          {
            path: 'configuration',
            component: DeviceCardTabShellComponent,
            data: { deviceTab: 'configuration' },
          },
          { path: 'metrics', component: DeviceCardTabShellComponent, data: { deviceTab: 'metrics' } },
          { path: 'snapshot', component: DeviceCardTabShellComponent, data: { deviceTab: 'snapshot' } },
          { path: 'item-config', component: DeviceCardTabShellComponent, data: { deviceTab: 'item-config' } },
          { path: 'events', component: DeviceCardTabShellComponent, data: { deviceTab: 'events' } },
          {
            path: 'config-management',
            component: DeviceCardTabShellComponent,
            data: { deviceTab: 'config-management' },
          },
          { path: '**', redirectTo: 'info' },
        ],
      },
      { path: 'monitoring', component: MonitoringPageComponent },
      {
        path: 'topology',
        loadComponent: () =>
          import('./pages/topology-page/topology-page.component').then((m) => m.TopologyPageComponent),
        canDeactivate: [topologyPageCanDeactivate],
      },
      { path: 'monitoring-templates', canActivate: [adminGuard], component: MonitoringTemplatesPageComponent },
      { path: 'monitoring-templates/:id', canActivate: [adminGuard], component: MonitoringTemplateDetailsPageComponent },
      { path: 'events', component: EventsPageComponent },
      { path: 'dashboards', component: DashboardsListPageComponent },
      { path: 'dashboards/:id', component: DashboardDetailPageComponent },
      {
        path: 'audit',
        canActivate: [adminGuard],
        loadComponent: () => import('./pages/audit-page/audit-page.component').then((m) => m.AuditPageComponent),
      },
      { path: 'users', canActivate: [adminGuard], component: UsersPageComponent },
      { path: 'system-settings', canActivate: [systemSettingsGuard], component: SystemSettingsPageComponent }
    ]
  },
  { path: '**', redirectTo: '' }
];
