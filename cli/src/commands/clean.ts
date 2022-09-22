import {Command, flags} from '@oclif/command'
import { flatMap, flatten } from 'lodash';
import config from '../config';
import { destroyBaseDir } from '../lib/data';
import { stopAll, wipeImages, remapYAMLTier } from '../lib/docker';

export default class Down extends Command {
  static description = 'Cleans out existing SourceScape containers. Used for upgrading.'

  static examples = [
    `$ sourcescape clean`,
  ]

  static flags = {
    help: flags.help({char: 'h'}),
    data: flags.boolean({char: 'd', description: 'Wipe datastores.'}),
    images: flags.boolean({char: 'i', description: 'Delete images.'}),
  }

  async run() {
    const {flags} = this.parse(Down)

    const flattened = flatten((config.services as any[]).map((items: any) => {
      return remapYAMLTier(items);
    }));

    await stopAll(flattened, this.log, flags.data, true); // destroy = true

    if(flags.data) {
      destroyBaseDir(this.log);
    }

    if (flags.images) {
      await wipeImages(flattened);
    }

    this.exit(0);
  }
}
