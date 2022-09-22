import {Command, flags} from '@oclif/command'
import { flatMap, flatten, reduce } from 'lodash';
import localConfig from '../config/local';
import { getMinorVersion } from '../lib/data';
import { remapYAMLTier, statusTier } from '../lib/docker';

const SOCKET_PORT = 5001;

export default class Status extends Command {
  static description = 'Get which containers are running.'

  static examples = [
    `$ sourcescape status`,
  ]

  static flags = {
    help: flags.help({char: 'h'})
  }

  static args = []

  async run() {
    const {args, flags} = this.parse(Status);
    this.log(`VERSION: ${getMinorVersion()}`);
    
    await reduce(localConfig.services, async (prev, items, idx) => {
      const remapped = remapYAMLTier(items);

      return prev.then(async () => {
        console.warn(`===== TIER ${idx} =====`);
        await statusTier(remapped, this.log)
        return null;
      });
    }, Promise.resolve(null));
  }
}
