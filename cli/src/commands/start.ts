import {Command, flags} from '@oclif/command'
import { flatMap, reduce } from 'lodash';

import config from '../config';
import { ensureNetwork, ensureTier, getImages, remapYAMLTier, pullImage } from '../lib/docker';
import open from 'open';
import { ensureDataDir, getMinorVersion } from '../lib/data';
import { fork, spawn } from 'child_process';
import { join, resolve } from 'path';
import { openSync, watch } from 'fs';
import { SOURCESCAPE_DIR } from '../lib/data';
import { exit } from 'process';

export default class Start extends Command {
  static description = 'Initialize SourceScape.'

  static examples = [
    `$ sourcescape start <YOUR_DIRECTORY>`,
  ]

  static flags = {
    help: flags.help({char: 'h'}),
    "force-pull": flags.boolean({char: 'f', description: 'Force pull images'}),
    port: flags.integer({char: 'p', description: 'Expose this port', default: 5000})
  }

  static strict = false

  async run() {
    const {argv, flags} = this.parse(Start);

    const { port } = flags;

    // TODO: combine with config
    function resolveDirectory(path: string) {
      if(path[0] === '~' || path[0] === '/') {
        return path; // we already handle this properly
      } else {
        return resolve(path);
      }
    }

    const initializeDirectories = argv.map(resolveDirectory);
    this.log('INITIALIZING SOURCESCAPE FOR DIRECTORIES:');
    initializeDirectories.forEach((d) => this.log(`  - ${d}`));

    await ensureNetwork();
    await ensureDataDir(this.log);

    // TODO: ensure images
    const allImages: string[] = flatMap(config.services, (objs) => {
      return flatMap(Object.keys(objs), (k) => {
        if (objs[k].base) {
          return [];
        } else {
          return [objs[k].image];
        }
      });
    });

    const images = await getImages();
    const minorVersion = getMinorVersion();
    const imageSet = images.filter((i: { tag: string }) => (i.tag === minorVersion)).map((image: {repository: string}) => (image.repository));
    const missing = allImages.filter((i) => (!imageSet.includes(i)));
    const containsAll = missing.length === 0;

    if (!containsAll || flags['force-pull']) {
      this.warn('Missing images. Pulling...');
      await reduce(missing, async (prev, item) => {  
        return prev.then(async () => {
          this.log(`Pulling ${item}:${minorVersion}`);
          await pullImage(item, minorVersion);
          return null;
        });
      }, Promise.resolve(null));
    }

    // ENSURE CONTAINERS
    await reduce(config.services, async (prev, items, idx) => {
      const remapped = remapYAMLTier(items);

      return prev.then(async () => {
        console.warn(`===== TIER ${idx} =====`);
        await ensureTier(remapped, initializeDirectories, minorVersion, port, this.log)
        return null;
      });
    }, Promise.resolve(null));

    open(`http://localhost:${port}`)

    // need to explicitly exit
    exit(0);
  }
}
