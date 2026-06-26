import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests/e2e',
  timeout: 30000,
  retries: 1,

  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  webServer: [
    {
      command: 'cd ../server && mvn spring-boot:run -q',
      url: 'http://localhost:8080',
      timeout: 60000,
      reuseExistingServer: true,
    },
    {
      command: 'npm run dev',
      url: 'http://localhost:5173',
      timeout: 30000,
      reuseExistingServer: true,
    },
  ],

  projects: [
    {
      name: 'chromium',
      use: { browserName: 'chromium' },
    },
  ],
});
