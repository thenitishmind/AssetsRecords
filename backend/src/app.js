import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import { env } from './config/env.js';
import { errorHandler, notFoundHandler } from './middleware/index.js';
import { buildRouter } from './routes/index.js';

export function createApp(options = {}) {
  const app = express();
  app.disable('x-powered-by');
  app.set('trust proxy', 1);
  app.use(helmet());
  app.use(cors({ origin: env.corsOrigins.includes('*') ? true : env.corsOrigins }));
  app.use(express.json({ limit: '200kb' }));
  app.use('/api', buildRouter(options));
  app.use(notFoundHandler);
  app.use(errorHandler);
  return app;
}
