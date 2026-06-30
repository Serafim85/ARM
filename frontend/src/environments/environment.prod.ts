/**
 * Сборка production: запросы к API с того же origin (nginx отдаёт SPA и проксирует /api).
 * Если бэкенд на другом хосте — задайте полный URL, например https://api.example.com
 */
export const environment = {
  production: true,
  apiBaseUrl: '',
  hideNetworkScannerFeatures: true,
};
