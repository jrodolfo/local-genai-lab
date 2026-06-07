import {defineConfig, devices} from '@playwright/test';

export default defineConfig({
    testDir: './e2e',
    snapshotDir: './e2e/__screenshots__',
    fullyParallel: true,
    reporter: [['list']],
    webServer: {
        command: 'FRONTEND_PORT=4173 npm run dev -- --host 127.0.0.1',
        url: 'http://127.0.0.1:4173',
        reuseExistingServer: !process.env.CI,
        timeout: 120_000
    },
    use: {
        ...devices['Desktop Chrome'],
        baseURL: 'http://127.0.0.1:4173',
        viewport: {width: 1440, height: 1100},
        screenshot: 'only-on-failure',
        trace: 'on-first-retry'
    }
});
