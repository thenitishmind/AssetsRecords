import { env } from './config/env.js';
import { connectDb, ensureIndexes, supportsTransactions } from './config/db.js';
import { seedAdminIfNeeded } from './services/authService.js';
import { createApp } from './app.js';

async function main() {
  await connectDb();
  await ensureIndexes();
  await seedAdminIfNeeded();
  const app = createApp();
  app.listen(env.port, '0.0.0.0', () => {
    console.log(
      `Asset Management API listening on :${env.port} (db: ${env.dbName}, transactions: ${supportsTransactions() ? 'on' : 'off'})`,
    );
  });
}

main().catch((e) => {
  console.error('Failed to start server:', e.message);
  process.exit(1);
});
