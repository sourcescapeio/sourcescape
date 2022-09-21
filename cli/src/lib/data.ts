import * as os from 'os';
import { existsSync, mkdirSync, rmdirSync } from 'fs';

export const SOURCESCAPE_DIR = `${os.homedir()}/.sourcescape`;
export const SOURCESCAPE_DATA_DIR = `${SOURCESCAPE_DIR}/data`;

export function getMinorVersion() {
  const version = require('../../package.json').version;

  return version && version.split('.').slice(0, 2).join(".");
}

export function ensureBaseDir(log: Function) {
  if (!existsSync(SOURCESCAPE_DIR)) {
    log("Creating .sourcescape directory")
    mkdirSync(SOURCESCAPE_DIR, {recursive: true});
  } else {
    log('Sourcescape directory exists');
  }
}

export function ensureDataDir(log: Function) {
  if (!existsSync(SOURCESCAPE_DATA_DIR)) {
    log("Creating .sourcescape/data directory")
    mkdirSync(SOURCESCAPE_DATA_DIR, {recursive: true});
  } else {
    log('Sourcescape directory exists');
  }
}

export function destroyBaseDir(log: Function) {
  if (existsSync(SOURCESCAPE_DIR)) {
    log("Deleting .sourcescape directory")
    rmdirSync(SOURCESCAPE_DIR, {recursive: true});
  } else {
    log("No .sourcescape directory");
  }
}
