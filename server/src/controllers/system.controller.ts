import { Request, Response } from 'express';
import os from 'os';

export const getHealth = async (req: Request, res: Response) => {
  res.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    uptime: process.uptime()
  });
};

export const getVersion = async (req: Request, res: Response) => {
  const packageJson = require('../../package.json');
  res.json({
    version: packageJson.version,
    name: packageJson.name,
    nodeVersion: process.version
  });
};

export const getSystemInfo = async (req: Request, res: Response) => {
  const packageJson = require('../../package.json');
  res.json({
    version: packageJson.version,
    environment: process.env.NODE_ENV || 'development',
    nodeVersion: process.version,
    platform: process.platform,
    uptime: process.uptime(),
    memory: {
      total: os.totalmem(),
      free: os.freemem(),
      usage: process.memoryUsage()
    },
    cpu: {
      count: os.cpus().length,
      loadavg: os.loadavg()
    }
  });
};
