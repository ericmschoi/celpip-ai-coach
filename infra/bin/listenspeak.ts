#!/usr/bin/env node
import { App, Tags } from 'aws-cdk-lib';
import { prefix, resolveConfig } from '../lib/config.js';
import { ListenSpeakStack } from '../lib/listenspeak-stack.js';

const app = new App();
const config = resolveConfig(app);

const stack = new ListenSpeakStack(app, `${prefix(config)}`, {
  config,
  env: { account: config.account, region: config.region },
  description: 'ListenSpeak AI Coach - independent CELPIP-style practice app',
});

Tags.of(stack).add('project', config.project);
Tags.of(stack).add('environment', config.envName);
Tags.of(stack).add('managed-by', 'aws-cdk');
